package com.fpoly.duan.service;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fpoly.duan.dto.FoodOnlyCheckoutRequest;
import com.fpoly.duan.dto.SnackLineRequest;
import com.fpoly.duan.dto.TicketCheckoutRequest;
import com.fpoly.duan.dto.TicketCheckoutResponse;
import com.fpoly.duan.dto.TicketQuoteLineDTO;
import com.fpoly.duan.dto.TicketQuoteResponse;
import com.fpoly.duan.dto.payos.PayOSCheckoutData;
import com.fpoly.duan.dto.payos.PayOSCreatePaymentLinkRequest;
import com.fpoly.duan.entity.CinemaProduct;
import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.entity.Movie;
import com.fpoly.duan.entity.MembershipRank;
import com.fpoly.duan.entity.OrderDetailFood;
import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.entity.PointsHistory;
import com.fpoly.duan.entity.Product;
import com.fpoly.duan.entity.Seat;
import com.fpoly.duan.entity.SeatType;
import com.fpoly.duan.entity.Showtime;
import com.fpoly.duan.entity.Ticket;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.repository.CinemaProductRepository;
import com.fpoly.duan.repository.CinemaRepository;
import com.fpoly.duan.repository.OrderDetailFoodRepository;
import com.fpoly.duan.repository.OrderOnlineRepository;
import com.fpoly.duan.repository.MembershipRankRepository;
import com.fpoly.duan.repository.PointsHistoryRepository;
import com.fpoly.duan.repository.PromotionRepository;
import com.fpoly.duan.repository.ProductRepository;
import com.fpoly.duan.repository.SeatRepository;
import com.fpoly.duan.repository.ShowtimeRepository;
import com.fpoly.duan.repository.TicketRepository;
import com.fpoly.duan.repository.UserRepository;
import com.fpoly.duan.repository.UserVoucherRepository;
import com.fpoly.duan.util.SeatLabel;
import com.fpoly.duan.entity.Promotion;
import com.fpoly.duan.entity.UserVoucher;
import com.fpoly.duan.entity.Voucher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketCheckoutService {
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final int ORDER_STATUS_PENDING = 0;
    private static final int ORDER_STATUS_PAID = 1;
    private static final int ORDER_STATUS_CANCELLED = 2;
    private static final int TICKET_STATUS_PENDING = 0;
    private static final int TICKET_STATUS_PAID = 1;
    private static final int TICKET_STATUS_CANCELLED = 2;
    private static final int FOOD_STATUS_PENDING = 0;
    private static final int FOOD_STATUS_PAID = 1;
    private static final int FOOD_STATUS_CANCELLED = 2;
    private static final long PENDING_SEAT_HOLD_MINUTES = 5;

    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;
    private final OrderOnlineRepository orderOnlineRepository;
    private final UserRepository userRepository;
    private final UserVoucherRepository userVoucherRepository;
    private final PayOSService payOSService;
    private final CinemaProductRepository cinemaProductRepository;
    private final ProductRepository productRepository;
    private final OrderDetailFoodRepository orderDetailFoodRepository;
    private final MembershipRankRepository membershipRankRepository;
    private final CinemaRepository cinemaRepository;
    private final EphemeralSeatHoldService ephemeralSeatHoldService;
    private final PromotionRepository promotionRepository;
    private final PointsHistoryRepository pointsHistoryRepository;
    private final TicketQrService ticketQrService;
    private final TicketEmailService ticketEmailService;
    private final CinemaScopeService cinemaScopeService;

    @Transactional
    public TicketCheckoutResponse checkout(Integer userId, TicketCheckoutRequest req) {
        User user = loadUser(userId);

        LinkedHashSet<Integer> seatIdSet = new LinkedHashSet<>(req.getSeatIds());
        if (seatIdSet.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ít nhất một ghế");
        }

        Showtime showtime = showtimeRepository.findByIdForUpdate(req.getShowtimeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy suất chiếu"));

        assertShowtimeBookable(showtime);
        assertMovieOpenForOnlineBooking(showtime);
        assertCustomerMeetsAgeLimit(user, showtime);

        if (showtime.getRoom() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suất chiếu chưa gắn phòng");
        }
        cinemaScopeService.requireRoomOperational(showtime.getRoom());
        Integer roomId = showtime.getRoom().getRoomId();
        Integer cinemaId = showtime.getRoom().getCinema() != null ? showtime.getRoom().getCinema().getCinemaId() : null;
        Cinema cinema = requireCustomerCinemaAvailable(cinemaId);

        List<Seat> seats = seatRepository.findAllByIdWithType(seatIdSet);
        if (seats.size() != seatIdSet.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Một hoặc nhiều ghế không tồn tại");
        }
        for (Seat seat : seats) {
            if (seat.getRoom() == null || !roomId.equals(seat.getRoom().getRoomId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ghế không thuộc phòng của suất chiếu này");
            }
        }

        List<Seat> allRoomSeats = seatRepository.findByRoom_RoomId(roomId);
        LocalDateTime pendingSince = nowInAppZone().minusMinutes(PENDING_SEAT_HOLD_MINUTES);
        List<Integer> dbHeldSeatIds = ticketRepository.findHeldSeatIdsByShowtime(
                showtime.getShowtimeId(),
                pendingSince);
        SeatLayoutRules.assertNoNewSingleSeatOrphanInRows(allRoomSeats,
                new HashSet<>(dbHeldSeatIds), seatIdSet);

        String clientHoldId = req.getClientHoldId();
        if (clientHoldId != null && !clientHoldId.isBlank()) {
            for (Integer sid : seatIdSet) {
                if (ephemeralSeatHoldService.isHeldByOther(showtime.getShowtimeId(), clientHoldId, sid)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Một hoặc nhiều ghế đang được người khác chọn — vui lòng chọn ghế khác");
                }
            }
        }

        long conflict = ticketRepository.countHeldOrPaidTicketsForSeats(
                showtime.getShowtimeId(),
                seatIdSet,
                pendingSince);
        if (conflict > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Một hoặc nhiều ghế đã được giữ hoặc đã bán");
        }

        PricingContext pricing = buildPricingContext(user, showtime, cinemaId);
        List<PricedSeatLine> pricedLines = priceSeats(seats, pricing);

        int ticketVnd = pricedLines.stream()
                .mapToInt(l -> (int) Math.round(l.finalPrice()))
                .sum();
        double ticketDouble = (double) ticketVnd;

        if (req.getSnacks() != null && !req.getSnacks().isEmpty() && cinemaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suất chiếu chưa gắn rạp — không thể thêm bắp nước");
        }

        long payosOrderCode = allocateUniquePayosOrderCode();

        OrderOnline order = new OrderOnline();
        order.setUser(user);
        order.setCreatedAt(nowInAppZone());
        order.setStatus(ORDER_STATUS_PENDING);
        order.setUserVoucher(null);
        order.setOrderCode(String.valueOf(payosOrderCode));
        order.setPaymentMethod("PAYOS");
        order.setCinema(cinema);
        order = orderOnlineRepository.save(order);

        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < seats.size(); i++) {
            Ticket t = new Ticket();
            t.setShowtime(showtime);
            t.setSeat(seats.get(i));
            t.setSeatLabel(SeatLabel.of(seats.get(i)));
            t.setOrderOnline(order);
            PricedSeatLine pl = pricedLines.get(i);
            t.setOriginalPrice(pl.originalPrice());
            t.setPromotionDiscount(pl.promotionDiscount());
            t.setPrice(pl.finalPrice());
            t.setStatus(TICKET_STATUS_PENDING);
            tickets.add(t);
        }
        ticketRepository.saveAll(tickets);
        ticketQrService.assignSecureCodes(tickets);
        ticketRepository.saveAll(tickets);

        List<OrderDetailFood> foodRows = new ArrayList<>();
        int snackVnd = 0;
        double snackDouble = 0.0;
        if (req.getSnacks() != null && !req.getSnacks().isEmpty()) {
            SnackTotals st = buildValidatedSnackLines(cinemaId, req.getSnacks(), order);
            snackVnd = st.vndTotal();
            snackDouble = st.doubleTotal();
            foodRows.addAll(st.rows());
        }

        int amountVnd = ticketVnd + snackVnd;
        double totalDouble = ticketDouble + snackDouble;
        if (amountVnd < 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền thanh toán không hợp lệ (tối thiểu 1.000đ)");
        }

        // Xử lý voucher giảm giá
        UserVoucher userVoucher = null;
        double discountAmount = 0.0;
        if (req.getUserVoucherId() != null) {
            userVoucher = userVoucherRepository.findById(req.getUserVoucherId()).orElse(null);
            if (userVoucher == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher không tồn tại");
            }
            if (!userVoucher.getUser().getUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Voucher không thuộc tài khoản của bạn");
            }
            if (userVoucher.getStatus() == null || userVoucher.getStatus() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher đã được sử dụng");
            }
            Voucher voucher = userVoucher.getVoucher();
            if (voucher == null || voucher.getStatus() == null || voucher.getStatus() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher không khả dụng");
            }
            Integer voucherCinemaId = voucher.getCinema() != null ? voucher.getCinema().getCinemaId() : null;
            if (voucherCinemaId == null || !voucherCinemaId.equals(cinemaId)) {
                String voucherCinemaName = voucher.getCinema() != null ? voucher.getCinema().getName() : null;
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Voucher này chỉ áp dụng tại rạp "
                                + (voucherCinemaName != null ? voucherCinemaName : "khác") + ", không áp dụng cho rạp bạn đang đặt vé");
            }
            LocalDate todayVoucher = todayInAppZone();
            if (voucher.getStartDate() != null && todayVoucher.isBefore(voucher.getStartDate())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher chưa có hiệu lực");
            }
            if (voucher.getEndDate() != null && todayVoucher.isAfter(voucher.getEndDate())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher đã hết hạn");
            }
            // Kiểm tra giá trị đơn hàng tối thiểu
            double minOrderValue = voucher.getMinOrderValue() != null ? voucher.getMinOrderValue() : 0;
            if (totalDouble < minOrderValue) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Đơn hàng tối thiểu " + minOrderValue + "đ để áp dụng voucher");
            }
            // Tính giảm giá (round giống FE)
            discountAmount = Math.round(calculateDiscount(totalDouble, voucher));
            // Cập nhật trạng thái voucher thành đã dùng
            userVoucher.setStatus(0);
            userVoucherRepository.save(userVoucher);
        }

        double finalAmount = totalDouble - discountAmount;
        if (finalAmount < 0) finalAmount = 0;

        order.setOriginalAmount(totalDouble);
        order.setDiscountAmount(discountAmount);
        order.setFinalAmount(finalAmount);
        order.setUserVoucher(userVoucher);
        orderOnlineRepository.save(order);

        if (!foodRows.isEmpty()) {
            orderDetailFoodRepository.saveAll(foodRows);
        }

        if (clientHoldId != null && !clientHoldId.isBlank()) {
            ephemeralSeatHoldService.releaseSeats(showtime.getShowtimeId(), seatIdSet, userId);
        }

        String description = truncate(
                snackVnd > 0 ? ("Ve + BNN #" + order.getOrderOnlineId()) : ("Ve xem phim #" + order.getOrderOnlineId()),
                240);

        // Use final amount after discount for PayOS
        int finalAmountVnd = (int) Math.round(finalAmount);
        if (finalAmountVnd <= 0) {
            order.setPaymentMethod(discountAmount > 0 ? "VOUCHER" : "FREE");
            orderOnlineRepository.save(order);
            finalizePaidOrder(order);
            rewardPaidOrder(order);
            return TicketCheckoutResponse.builder()
                    .orderOnlineId(order.getOrderOnlineId())
                    .payosOrderCode(payosOrderCode)
                    .amountVnd(0)
                    .payos(null)
                    .build();
        }
        return finalizePayos(user, payosOrderCode, finalAmountVnd, description, req.getReturnUrl(), req.getCancelUrl(), order);
    }

    /**
     * Báo giá theo đúng công thức BE (không tạo order/ticket).
     */
    @Transactional(readOnly = true)
    public TicketQuoteResponse quote(Integer userId, TicketCheckoutRequest req) {
        User user = loadUser(userId);

        LinkedHashSet<Integer> seatIdSet = new LinkedHashSet<>(req.getSeatIds());
        if (seatIdSet.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ít nhất một ghế");
        }

        Showtime showtime = showtimeRepository.findById(req.getShowtimeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy suất chiếu"));
        assertMovieOpenForOnlineBooking(showtime);
        assertCustomerMeetsAgeLimit(user, showtime);

        Integer cinemaId = showtime.getRoom() != null && showtime.getRoom().getCinema() != null
                ? showtime.getRoom().getCinema().getCinemaId()
                : null;
        requireCustomerCinemaAvailable(cinemaId);

        List<Seat> seats = seatRepository.findAllByIdWithType(seatIdSet);
        if (seats.size() != seatIdSet.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Một hoặc nhiều ghế không tồn tại");
        }

        PricingContext pricing = buildPricingContext(user, showtime, cinemaId);
        List<PricedSeatLine> pricedLines = priceSeats(seats, pricing);

        double ticketTotal = pricedLines.stream().mapToDouble(PricedSeatLine::finalPrice).sum();

        double snackTotal = 0.0;
        if (req.getSnacks() != null && !req.getSnacks().isEmpty()) {
            if (cinemaId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suất chiếu chưa gắn rạp — không thể thêm bắp nước");
            }
            SnackTotals st = buildValidatedSnackLines(cinemaId, req.getSnacks(), null);
            snackTotal = st.doubleTotal();
        }

        double subtotal = ticketTotal + snackTotal;
        VoucherDiscount vd = computeVoucherDiscountForQuote(userId, req.getUserVoucherId(), subtotal, cinemaId);

        double finalAmount = subtotal - vd.discountAmount();
        if (finalAmount < 0) finalAmount = 0;

        List<TicketQuoteLineDTO> dtoLines = pricedLines.stream()
                .map(l -> TicketQuoteLineDTO.builder()
                        .seatId(l.seatId())
                        .seatLabel(l.seatLabel())
                        .seatTypeName(l.seatTypeName())
                        .originalPrice(l.originalPrice())
                        .promotionDiscount(l.promotionDiscount())
                        .membershipDiscount(l.membershipDiscount())
                        .finalPrice(l.finalPrice())
                        .build())
                .toList();

        return TicketQuoteResponse.builder()
                .ticketLines(dtoLines)
                .ticketTotal(ticketTotal)
                .snackTotal(snackTotal)
                .voucherDiscount(vd.discountAmount())
                .finalAmount(finalAmount)
                .rankName(pricing.rankName())
                .membershipDiscountPercent(pricing.membershipDiscountPercent())
                .build();
    }

    /**
     * Khách hủy thanh toán PayOS — giữ lịch sử đơn nhưng chuyển trạng thái hủy để trả ghế.
     */
    @Transactional
    public boolean cancelPendingOrderByPayosCode(Integer userId, long payosOrderCode) {
        OrderOnline o = orderOnlineRepository.findByOrderCode(String.valueOf(payosOrderCode))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn với mã PayOS"));

        if (o.getUser() == null || !o.getUser().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Đơn không thuộc tài khoản của bạn");
        }

        if (o.getStatus() != null && o.getStatus() == ORDER_STATUS_PAID) {
            rewardPaidOrder(o);
            return true;
        }

        try {
            if (syncPaidPayosOrderIfPaid(o)) {
                return true;
            }
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Chưa hủy đơn vì không kiểm tra được trạng thái PayOS: " + e.getMessage(), e);
        }

        if (o.getStatus() == null || o.getStatus() != ORDER_STATUS_PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ hủy được đơn đang chờ thanh toán");
        }

        List<Ticket> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(o.getOrderOnlineId());
        List<OrderDetailFood> foods = orderDetailFoodRepository.findByOrderOnline_OrderOnlineId(o.getOrderOnlineId());

        Integer stId = tickets.stream()
                .map(t -> t.getShowtime() != null ? t.getShowtime().getShowtimeId() : null)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        Set<Integer> seatIds = tickets.stream()
                .map(t -> t.getSeat() != null ? t.getSeat().getSeatId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        o.setStatus(ORDER_STATUS_CANCELLED);
        orderOnlineRepository.save(o);

        for (Ticket t : tickets) {
            t.setStatus(TICKET_STATUS_CANCELLED);
        }
        ticketRepository.saveAll(tickets);

        for (OrderDetailFood f : foods) {
            f.setStatus(FOOD_STATUS_CANCELLED);
        }
        orderDetailFoodRepository.saveAll(foods);

        // Khôi phục voucher để dùng lại
        UserVoucher userVoucher = o.getUserVoucher();
        if (userVoucher != null && userVoucher.getStatus() != null && userVoucher.getStatus() == 0) {
            userVoucher.setStatus(1); // Khôi phục về trạng thái chưa dùng
            userVoucherRepository.save(userVoucher);
        }

        if (stId != null && !seatIds.isEmpty()) {
            // Khách tự huỷ (không phải hết hạn tự động) — không tính vi phạm chống phá.
            ephemeralSeatHoldService.releaseSeats(stId, seatIds, userId);
        }
        return false;
    }

    /**
     * Đồng bộ lại đơn PayOS trước khi hủy/dọn quá hạn.
     * Nếu PayOS đã PAID thì chốt đơn và cộng điểm, tránh trường hợp webhook local/LAN không gọi được.
     */
    public boolean syncPaidPayosOrderIfPaid(OrderOnline order) {
        if (order == null) {
            return false;
        }

        Long payosOrderCode = parsePositiveLong(order.getOrderCode());
        if (payosOrderCode == null) {
            return false;
        }

        if (order.getStatus() != null && order.getStatus() == ORDER_STATUS_PAID) {
            rewardPaidOrder(order);
            return true;
        }

        boolean isPending = order.getStatus() != null && order.getStatus() == ORDER_STATUS_PENDING;
        boolean isCancelled = order.getStatus() != null && order.getStatus() == ORDER_STATUS_CANCELLED;
        if (!isPending && !isCancelled) {
            return false;
        }

        PayOSCheckoutData payos = payOSService.getPaymentInformation(payosOrderCode);
        if (!"PAID".equalsIgnoreCase(payos.getStatus())) {
            return false;
        }

        int expected = (int) Math.round(order.getFinalAmount() != null ? order.getFinalAmount() : 0.0);
        assertPayosAmountMatches(payos, expected);

        if (isCancelled) {
            assertCanRestoreCancelledPaidOrder(order);
        }

        finalizePaidOrder(order);
        rewardPaidOrder(order);
        return true;
    }

    @Transactional
    public TicketCheckoutResponse confirmPaidOrderByPayosCode(Integer userId, Long payosOrderCode) {
        if (payosOrderCode == null || payosOrderCode <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã PayOS không hợp lệ");
        }

        OrderOnline order = orderOnlineRepository.findByOrderCode(String.valueOf(payosOrderCode))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn với mã PayOS"));

        if (order.getUser() == null || !order.getUser().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Đơn không thuộc tài khoản của bạn");
        }

        int expected = (int) Math.round(order.getFinalAmount() != null ? order.getFinalAmount() : 0.0);

        if (order.getStatus() != null && order.getStatus() == ORDER_STATUS_PAID) {
            rewardPaidOrder(order);
            return TicketCheckoutResponse.builder()
                    .orderOnlineId(order.getOrderOnlineId())
                    .payosOrderCode(payosOrderCode)
                    .amountVnd(expected)
                    .payos(PayOSCheckoutData.builder()
                            .orderCode(payosOrderCode)
                            .amount((long) expected)
                            .currency("VND")
                            .status("PAID")
                            .build())
                    .build();
        }

        boolean isPending = order.getStatus() != null && order.getStatus() == ORDER_STATUS_PENDING;
        boolean isCancelled = order.getStatus() != null && order.getStatus() == ORDER_STATUS_CANCELLED;
        if (!isPending && !isCancelled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn không còn ở trạng thái có thể xác nhận");
        }

        PayOSCheckoutData payos;
        try {
            payos = payOSService.getPaymentInformation(payosOrderCode);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayOS: " + e.getMessage(), e);
        }

        if (!"PAID".equalsIgnoreCase(payos.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "PayOS chưa xác nhận thanh toán cho đơn này (trạng thái: " + payos.getStatus() + ")");
        }

        assertPayosAmountMatches(payos, expected);

        if (isCancelled) {
            assertCanRestoreCancelledPaidOrder(order);
        }

        finalizePaidOrder(order);
        rewardPaidOrder(order);

        return TicketCheckoutResponse.builder()
                .orderOnlineId(order.getOrderOnlineId())
                .payosOrderCode(payosOrderCode)
                .amountVnd(expected)
                .payos(payos)
                .build();
    }

    /**
     * Kiểm tra trạng thái đơn PayOS để FE poll trong khi hiển thị QR trong app — KHÔNG throw khi đơn
     * còn đang chờ thanh toán (khác {@link #confirmPaidOrderByPayosCode}), tiện cho việc gọi lặp lại
     * mỗi vài giây mà không tạo lỗi/exception noise. Nếu PayOS đã báo PAID thì chốt đơn luôn.
     */
    @Transactional
    public TicketCheckoutResponse checkPayosStatus(Integer userId, Long payosOrderCode) {
        if (payosOrderCode == null || payosOrderCode <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã PayOS không hợp lệ");
        }
        OrderOnline order = orderOnlineRepository.findByOrderCode(String.valueOf(payosOrderCode))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn với mã PayOS"));
        if (order.getUser() == null || !order.getUser().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Đơn không thuộc tài khoản của bạn");
        }

        int expected = (int) Math.round(order.getFinalAmount() != null ? order.getFinalAmount() : 0.0);
        String status;
        if (order.getStatus() != null && order.getStatus() == ORDER_STATUS_PAID) {
            status = "PAID";
            // Gọi lại dù đơn đã PAID sẵn (idempotent nhờ check "alreadyRewarded" trong
            // addPointsForOrder) — tự phục hồi nếu webhook đã chốt đơn nhưng lỡ chưa cộng điểm.
            rewardPaidOrder(order);
        } else if (order.getStatus() != null && order.getStatus() == ORDER_STATUS_PENDING) {
            status = "PENDING";
            try {
                PayOSCheckoutData payosInfo = payOSService.getPaymentInformation(payosOrderCode);
                if ("PAID".equalsIgnoreCase(payosInfo.getStatus())) {
                    assertPayosAmountMatches(payosInfo, expected);
                    finalizePaidOrder(order);
                    rewardPaidOrder(order);
                    status = "PAID";
                }
            } catch (Exception e) {
                log.warn("Không kiểm tra được trạng thái PayOS cho đơn {}: {}", payosOrderCode, e.getMessage());
            }
        } else {
            status = "CANCELLED";
        }

        return TicketCheckoutResponse.builder()
                .orderOnlineId(order.getOrderOnlineId())
                .payosOrderCode(payosOrderCode)
                .amountVnd(expected)
                .payos(PayOSCheckoutData.builder()
                        .orderCode(payosOrderCode)
                        .amount((long) expected)
                        .currency("VND")
                        .status(status)
                        .build())
                .build();
    }

    @Transactional
    public TicketCheckoutResponse checkoutFoodOnly(Integer userId, FoodOnlyCheckoutRequest req) {
        User user = loadUser(userId);
        Cinema cinema = requireCustomerCinemaAvailable(req.getCinemaId());

        long payosOrderCode = allocateUniquePayosOrderCode();

        OrderOnline order = new OrderOnline();
        order.setUser(user);
        order.setCreatedAt(nowInAppZone());
        order.setStatus(ORDER_STATUS_PENDING);
        order.setUserVoucher(null);
        order.setOrderCode(String.valueOf(payosOrderCode));
        order.setPaymentMethod("PAYOS");
        order.setCinema(cinema);
        order = orderOnlineRepository.save(order);

        SnackTotals st = buildValidatedSnackLines(req.getCinemaId(), req.getItems(), order);
        if (st.vndTotal() < 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn bắp nước tối thiểu 1.000đ");
        }

        order.setOriginalAmount(st.doubleTotal());
        order.setDiscountAmount(0.0);
        order.setFinalAmount(st.doubleTotal());
        orderOnlineRepository.save(order);
        orderDetailFoodRepository.saveAll(st.rows());

        String description = truncate("Bap nuoc #" + order.getOrderOnlineId(), 240);
        return finalizePayos(user, payosOrderCode, st.vndTotal(), description, req.getReturnUrl(), req.getCancelUrl(), order);
    }

    /** Ảnh QR hóa đơn (đơn bắp nước) để khách tự xem/xuất trình tại quầy — công khai như QR vé, bảo
     * mật bằng chính độ khó đoán của receiptToken (HMAC), không cần đăng nhập để hiển thị ảnh. */
    @Transactional(readOnly = true)
    public byte[] getOrderReceiptQrPng(String receiptToken) {
        if (receiptToken == null || receiptToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu mã QR hóa đơn");
        }
        OrderOnline order = orderOnlineRepository.findByReceiptToken(receiptToken.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mã QR hóa đơn"));
        if (order.getStatus() == null || order.getStatus() != ORDER_STATUS_PAID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn không hợp lệ");
        }
        return ticketQrService.toReceiptQrPng(order.getReceiptToken());
    }

    private TicketCheckoutResponse finalizePayos(
            User user,
            long payosOrderCode,
            int amountVnd,
            String description,
            String returnUrl,
            String cancelUrl,
            OrderOnline order) {

        // Thiết lập link thanh toán hết hạn sau 5 phút (300 giây)
        long expiredAt = System.currentTimeMillis() / 1000 + 300;

        PayOSCreatePaymentLinkRequest payReq = PayOSCreatePaymentLinkRequest.builder()
                .orderCode(payosOrderCode)
                .amount(amountVnd)
                .description(description)
                .returnUrl(returnUrl.trim())
                .cancelUrl(cancelUrl.trim())
                .buyerName(user.getFullname() != null && !user.getFullname().isBlank() ? user.getFullname() : user.getEmail())
                .buyerEmail(user.getEmail())
                .buyerPhone(user.getPhone())
                .expiredAt(expiredAt) // Thêm thuộc tính này
                .build();

        PayOSCheckoutData payos;
        try {
            payos = payOSService.createPaymentLink(payReq);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayOS: " + e.getMessage(), e);
        }

        if (payos.getCheckoutUrl() == null || payos.getCheckoutUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayOS không trả về checkoutUrl");
        }

        return TicketCheckoutResponse.builder()
                .orderOnlineId(order.getOrderOnlineId())
                .payosOrderCode(payosOrderCode)
                .amountVnd(amountVnd)
                .payos(payos)
                .build();
    }

    private User loadUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
    }

    private Cinema loadCinema(Integer cinemaId) {
        if (cinemaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không xác định được rạp");
        }
        return cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy rạp"));
    }

    private Cinema requireCustomerCinemaAvailable(Integer cinemaId) {
        Cinema cinema = loadCinema(cinemaId);
        if (cinema.getStatus() != null && cinema.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rạp đang tạm khóa, không thể đặt vé hoặc bắp nước");
        }
        return cinema;
    }

    private SnackTotals buildValidatedSnackLines(Integer cinemaId, List<SnackLineRequest> raw, OrderOnline order) {
        Map<Integer, Integer> qtyByProduct = new LinkedHashMap<>();
        for (SnackLineRequest s : raw) {
            if (s.getProductId() == null || s.getQuantity() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dòng sản phẩm không hợp lệ");
            }
            qtyByProduct.merge(s.getProductId(), s.getQuantity(), Integer::sum);
        }

        int vndTotal = 0;
        double doubleTotal = 0.0;
        List<OrderDetailFood> rows = new ArrayList<>();

        for (Map.Entry<Integer, Integer> e : qtyByProduct.entrySet()) {
            int productId = e.getKey();
            int qty = e.getValue();
            if (qty < 1 || qty > 99) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng sản phẩm không hợp lệ");
            }

            CinemaProduct cp = cinemaProductRepository
                    .findByCinema_CinemaIdAndProduct_ProductId(cinemaId, productId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Sản phẩm không có trong menu rạp hoặc chưa mở bán: " + productId));

            if (!Boolean.TRUE.equals(cp.getIsActive())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sản phẩm không đang bán tại rạp: " + productId);
            }

            Product p = productRepository.findById(productId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy sản phẩm"));
            if (p.getStatus() == null || p.getStatus() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sản phẩm ngừng kinh doanh: " + productId);
            }

            double unit = p.getPrice() != null ? p.getPrice() : 0.0;
            double lineD = unit * qty;
            vndTotal = (int) Math.round(vndTotal + lineD);
            doubleTotal += lineD;

            OrderDetailFood od = new OrderDetailFood();
            od.setOrderOnline(order);
            od.setProduct(p);
            od.setQuantity(qty);
            od.setPrice(unit);
            od.setStatus(FOOD_STATUS_PENDING);
            rows.add(od);
        }

        return new SnackTotals(vndTotal, doubleTotal, rows);
    }

    private record SnackTotals(int vndTotal, double doubleTotal, List<OrderDetailFood> rows) {
    }

    private record VoucherDiscount(double discountAmount) {
    }

    private VoucherDiscount computeVoucherDiscountForQuote(Integer userId, Integer userVoucherId, double subtotal, Integer cinemaId) {
        if (userVoucherId == null) {
            return new VoucherDiscount(0.0);
        }
        UserVoucher uv = userVoucherRepository.findById(userVoucherId).orElse(null);
        if (uv == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher không tồn tại");
        }
        if (uv.getUser() == null || !uv.getUser().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Voucher không thuộc tài khoản của bạn");
        }
        if (uv.getStatus() == null || uv.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher đã được sử dụng");
        }
        Voucher v = uv.getVoucher();
        if (v == null || v.getStatus() == null || v.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher không khả dụng");
        }
        Integer voucherCinemaId = v.getCinema() != null ? v.getCinema().getCinemaId() : null;
        if (voucherCinemaId == null || !voucherCinemaId.equals(cinemaId)) {
            String voucherCinemaName = v.getCinema() != null ? v.getCinema().getName() : null;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Voucher này chỉ áp dụng tại rạp "
                            + (voucherCinemaName != null ? voucherCinemaName : "khác") + ", không áp dụng cho rạp bạn đang đặt vé");
        }
        LocalDate today = todayInAppZone();
        if (v.getStartDate() != null && today.isBefore(v.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher chưa có hiệu lực");
        }
        if (v.getEndDate() != null && today.isAfter(v.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher đã hết hạn");
        }
        double minOrderValue = v.getMinOrderValue() != null ? v.getMinOrderValue() : 0.0;
        if (subtotal < minOrderValue) {
            return new VoucherDiscount(0.0);
        }
        double discount = Math.round(calculateDiscount(subtotal, v));
        if (discount > subtotal) discount = subtotal;
        return new VoucherDiscount(discount);
    }

    private record PricingContext(
            double unitBase,
            double promotionDiscountPercent,
            double membershipDiscountPercent,
            String rankName) {
    }

    private PricingContext buildPricingContext(User user, Showtime showtime, Integer cinemaId) {
        double unitBase = computeUnitBasePrice(showtime);

        Integer movieId = showtime.getMovie() != null ? showtime.getMovie().getMovieId() : null;
        LocalDate today = todayInAppZone();
        List<Promotion> promotions = promotionRepository.findActivePromotions(movieId, cinemaId, today);
        double promotionDiscountPercent = 0.0;
        if (!promotions.isEmpty()) {
            Promotion promo = promotions.get(0);
            promotionDiscountPercent = promo.getDiscountPercent() != null ? promo.getDiscountPercent() : 0.0;
        }

        double membershipDiscountPercent = 0.0;
        String rankName = null;
        MembershipRank effectiveRank = resolveEffectiveRank(user);
        if (effectiveRank != null) {
            membershipDiscountPercent = effectiveRank.getDiscountPercent() != null ? effectiveRank.getDiscountPercent() : 0.0;
            rankName = effectiveRank.getRankName();
        }

        return new PricingContext(unitBase, promotionDiscountPercent, membershipDiscountPercent, rankName);
    }

    /**
     * Dùng tổng chi năm hiện tại để xác định hạng hiệu lực khi checkout/quote.
     * Tránh lệch trường hợp rank_id trong users chưa được cập nhật kịp.
     */
    private MembershipRank resolveEffectiveRank(User user) {
        if (user == null || user.getUserId() == null) return null;
        int currentYear = todayInAppZone().getYear();
        double spending = orderOnlineRepository.sumCompletedRevenueByUserAndYear(user.getUserId(), currentYear);

        List<MembershipRank> activeRanks = membershipRankRepository.findAll().stream()
                .filter(r -> r.getStatus() == null || r.getStatus() == 1)
                .toList();
        if (activeRanks.isEmpty()) return null;

        MembershipRank matched = activeRanks.stream()
                .filter(r -> spending >= (r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                .max(Comparator.comparing(r -> r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                .orElse(null);
        if (matched != null) return matched;
        return activeRanks.stream()
                .min(Comparator.comparing(r -> r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                .orElse(null);
    }

    private record PricedSeatLine(
            Integer seatId,
            String seatLabel,
            String seatTypeName,
            double originalPrice,
            double promotionDiscount,
            double membershipDiscount,
            double finalPrice) {
    }

    private List<PricedSeatLine> priceSeats(List<Seat> seats, PricingContext ctx) {
        List<PricedSeatLine> out = new ArrayList<>();
        for (Seat seat : seats) {
            double seatSurcharge = 0.0;
            boolean isCouple = false;
            SeatType st = seat.getSeatType();
            if (st != null) {
                if (st.getSurcharge() != null) {
                    seatSurcharge = st.getSurcharge();
                }
                isCouple = Boolean.TRUE.equals(st.getCoupleSeat());
            }
            int mult = isCouple ? 2 : 1;

            double original = (ctx.unitBase() + seatSurcharge) * mult;
            double promoDiscount = (ctx.unitBase() * (ctx.promotionDiscountPercent() / 100.0)) * mult;
            double afterPromo = original - promoDiscount;
            double memberDiscount = afterPromo * (ctx.membershipDiscountPercent() / 100.0);
            double finalPrice = afterPromo - memberDiscount;

            long originalRounded = Math.round(original);
            long promoRounded = Math.round(promoDiscount);
            long memberRounded = Math.round(memberDiscount);
            long finalRounded = Math.round(finalPrice);

            String seatTypeName = st != null ? st.getName() : null;
            String seatLabel = (seat.getRow() != null ? seat.getRow() : "") + (seat.getNumber() != null ? seat.getNumber() : "");

            out.add(new PricedSeatLine(
                    seat.getSeatId(),
                    seatLabel != null && !seatLabel.isBlank() ? seatLabel : null,
                    seatTypeName,
                    (double) originalRounded,
                    (double) promoRounded,
                    (double) memberRounded,
                    (double) finalRounded));
        }
        return out;
    }

    /**
     * Ảnh QR đúng định dạng để app khách hiển thị và nhân viên quét.
     * QR chứa qrToken đã mã hóa, không phải orderCode hay chuỗi tự tạo ở FE/app.
     */
    @Transactional(readOnly = true)
    public byte[] getTicketQrPng(String qrToken) {
        if (qrToken == null || qrToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu mã QR vé");
        }
        Ticket ticket = ticketRepository.findByQrToken(qrToken.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mã QR vé"));
        if (ticket.getOrderOnline() == null || ticket.getOrderOnline().getStatus() == null
                || ticket.getOrderOnline().getStatus() == ORDER_STATUS_CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vé không hợp lệ");
        }
        return ticketQrService.toPng(ticket.getQrToken());
    }

    /**
     * Xác nhận thanh toán từ webhook PayOS (chữ ký đã được kiểm tra trước khi gọi).
     */
    @Transactional
    public void confirmPaymentFromPayosWebhook(org.json.JSONObject dataJson) {
        long orderCode = dataJson.optLong("orderCode");
        if (orderCode <= 0) {
            throw new IllegalArgumentException("Webhook thiếu orderCode");
        }
        int paidAmount = dataJson.optInt("amount", -1);
        if (paidAmount < 0) {
            paidAmount = (int) dataJson.optLong("amount", -1L);
        }
        log.debug("PayOS webhook received: orderCode={}, paidAmount={}", orderCode, paidAmount);

        Optional<OrderOnline> opt = orderOnlineRepository.findByOrderCode(String.valueOf(orderCode));
        if (opt.isEmpty()) {
            log.debug("PayOS webhook ignored because order was not found: orderCode={}", orderCode);
            /* Đơn đã bị xóa khi user hủy thanh toán — PayOS vẫn có thể gọi webhook muộn */
            return;
        }
        OrderOnline order = opt.get();

        if (order.getStatus() != null && order.getStatus() == ORDER_STATUS_PAID) {
            rewardPaidOrder(order);
            return;
        }
        boolean isPending = order.getStatus() != null && order.getStatus() == ORDER_STATUS_PENDING;
        boolean isCancelled = order.getStatus() != null && order.getStatus() == ORDER_STATUS_CANCELLED;
        if (!isPending && !isCancelled) {
            /* Đã hủy / trạng thái lạ — không kích hoạt thanh toán */
            return;
        }

        int expected = (int) Math.round(order.getFinalAmount() != null ? order.getFinalAmount() : 0.0);
        if (paidAmount >= 0 && Math.abs(paidAmount - expected) > 1) {
            throw new IllegalArgumentException("Số tiền webhook không khớp đơn (expected " + expected + ", got " + paidAmount + ")");
        }

        if (isCancelled) {
            assertCanRestoreCancelledPaidOrder(order);
        }

        finalizePaidOrder(order);
        rewardPaidOrder(order);
    }

    private void assertCanRestoreCancelledPaidOrder(OrderOnline order) {
        List<Ticket> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId());
        Map<Integer, Set<Integer>> seatIdsByShowtime = tickets.stream()
                .filter(t -> t.getShowtime() != null
                        && t.getShowtime().getShowtimeId() != null
                        && t.getSeat() != null
                        && t.getSeat().getSeatId() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getShowtime().getShowtimeId(),
                        Collectors.mapping(t -> t.getSeat().getSeatId(), Collectors.toSet())));

        for (Map.Entry<Integer, Set<Integer>> entry : seatIdsByShowtime.entrySet()) {
            if (!entry.getValue().isEmpty()
                    && ticketRepository.countPaidTicketsForSeats(entry.getKey(), entry.getValue()) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ghế trong đơn đã bị bán cho đơn khác sau khi đơn quá hạn. Vui lòng liên hệ quầy để xử lý.");
            }
        }
    }

    private void assertPayosAmountMatches(PayOSCheckoutData payos, int expected) {
        Long paidAmount = payos != null ? payos.getAmount() : null;
        if (paidAmount != null && Math.abs(paidAmount - expected) > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số tiền PayOS không khớp đơn (expected " + expected + ", got " + paidAmount + ")");
        }
    }

    private static Long parsePositiveLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.matches("\\d+")) {
            return null;
        }
        try {
            long parsed = Long.parseLong(trimmed);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void finalizePaidOrder(OrderOnline order) {
        order.setStatus(ORDER_STATUS_PAID);
        if (order.getPaymentMethod() == null || order.getPaymentMethod().isBlank()) {
            order.setPaymentMethod("PAYOS");
        }
        // Mọi đơn online đã thanh toán đều có mã QR hóa đơn riêng (dùng cho bắp nước không có vé
        // để soát/giao hàng tại quầy) — giống cách đơn tại quầy đã có từ trước.
        if (order.getReceiptToken() == null || order.getReceiptToken().isBlank()) {
            ticketQrService.assignReceiptToken(order);
        }
        orderOnlineRepository.save(order);

        UserVoucher userVoucher = order.getUserVoucher();
        if (userVoucher != null && userVoucher.getStatus() != null && userVoucher.getStatus() == 1) {
            userVoucher.setStatus(0);
            userVoucherRepository.save(userVoucher);
        }

        List<Ticket> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId());
        for (Ticket t : tickets) {
            t.setStatus(TICKET_STATUS_PAID);
        }
        ticketRepository.saveAll(tickets);

        // Chỉ gửi sau khi các vé đã có trạng thái PAID và QR hợp lệ.
        ticketEmailService.sendPaidTicketEmailIfNeeded(order);

        List<OrderDetailFood> foods = orderDetailFoodRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId());
        for (OrderDetailFood f : foods) {
            f.setStatus(FOOD_STATUS_PAID);
        }
        orderDetailFoodRepository.saveAll(foods);
    }

    private void rewardPaidOrder(OrderOnline order) {
        if (order.getUser() != null) {
            try {
                recalculateUserRankFromPaidOrders(order.getUser());
                addPointsForOrder(order);
            } catch (Exception e) {
                log.error("Error adding points for PayOS order {}", order.getOrderCode(), e);
            }
        } else {
            log.warn("Cannot add points for PayOS order {} because user is null", order.getOrderCode());
        }
    }

    /**
     * Cộng điểm cho user sau khi thanh toán thành công
     * Quy tắc: 1k = 1 điểm + điểm bonus theo rank
     */
    private void addPointsForOrder(OrderOnline order) {
        try {
            // Load user từ repository để đảm bảo có dữ liệu
            User user = order.getUser();
            if (user != null && user.getUserId() != null) {
                user = userRepository.findById(user.getUserId()).orElse(null);
            }
            
            if (user == null) {
                log.debug("Skip adding points because order {} has no user", order.getOrderCode());
                return;
            }

            String orderCode = order.getOrderCode() != null
                    ? order.getOrderCode()
                    : String.valueOf(order.getOrderOnlineId());
            String descriptionPrefix = "Tích điểm từ đơn " + orderCode;
            boolean alreadyRewarded = pointsHistoryRepository
                    .findByUser_UserIdOrderByDateDescPointHistoryIdDesc(user.getUserId())
                    .stream()
                    .anyMatch(h -> h.getDescription() != null
                            && h.getDescription().startsWith(descriptionPrefix));
            if (alreadyRewarded) {
                log.debug("Skip adding duplicate points for order {} and user {}", orderCode, user.getUserId());
                return;
            }

            double finalAmount = order.getFinalAmount() != null ? order.getFinalAmount() : 0.0;
            
            // Tính điểm từ số tiền: 1k = 1 điểm (làm tròn)
            int pointsFromAmount = (int) Math.round(finalAmount / 1000);
            
            // Lấy điểm bonus từ rank
            MembershipRank rank = resolveEffectiveRank(user);
            int bonusPoints = (rank != null && rank.getBonusPoint() != null) ? rank.getBonusPoint() : 0;
            
            // Tổng điểm
            int totalPoints = pointsFromAmount + bonusPoints;
            
            if (totalPoints <= 0) {
                log.debug("Skip adding non-positive points for order {}", order.getOrderCode());
                return;
            }
            
            // Cộng điểm vào user
            int currentPoints = user.getPoints() != null ? user.getPoints() : 0;
            user.setPoints(currentPoints + totalPoints);
            userRepository.save(user);
            
            // Lưu lịch sử điểm
            PointsHistory pointsHistory = new PointsHistory();
            pointsHistory.setUser(user);
            pointsHistory.setDate(todayInAppZone());
            pointsHistory.setDescription(descriptionPrefix +
                                       " (" + pointsFromAmount + " điểm từ số tiền" + 
                                       (bonusPoints > 0 ? " + " + bonusPoints + " điểm bonus" : "") + ")");
            pointsHistory.setPoints(totalPoints);
            pointsHistoryRepository.save(pointsHistory);
            log.debug("Added {} points for order {} and user {}", totalPoints, orderCode, user.getUserId());
        } catch (Exception e) {
            log.error("Error adding points for order {}", order.getOrderCode(), e);
        }
    }

    /** Cộng khai (không private) để {@code CustomerMeController} gọi trực tiếp — trước đây gọi qua
     * reflection vào bean proxy CGLIB, {@code getDeclaredMethod} không tìm thấy method private của lớp
     * cha nên LUÔN ném NoSuchMethodException (bị nuốt lỗi), khiến việc cập nhật hạng không bao giờ
     * chạy và tốn chi phí reflection+exception vô ích trên mọi request /api/v1/me/*. */
    public void recalculateUserRankFromPaidOrders(User user) {
        int currentYear = todayInAppZone().getYear();
        double completedRevenue = orderOnlineRepository
                .sumCompletedRevenueByUserAndYear(user.getUserId(), currentYear);

        MembershipRank matched = membershipRankRepository.findAll().stream()
                .filter(r -> r.getStatus() == null || r.getStatus() == 1)
                .filter(r -> completedRevenue >= (r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                .max(Comparator.comparing(r -> r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                .orElseGet(() -> membershipRankRepository.findAll().stream()
                        .filter(r -> r.getStatus() == null || r.getStatus() == 1)
                        .min(Comparator.comparing(r -> r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                        .orElse(null));

        user.setTotalSpending(completedRevenue);
        user.setRankId(matched != null ? matched.getRankId() : null);
        userRepository.save(user);
    }

    private long allocateUniquePayosOrderCode() {
        for (int attempt = 0; attempt < 50; attempt++) {
            long candidate = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000_000L);
            if (!orderOnlineRepository.existsByOrderCode(String.valueOf(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Không sinh được mã đơn PayOS duy nhất");
    }

    private static void assertShowtimeBookable(Showtime s) {
        LocalDateTime now = nowInAppZone();
        Movie movie = s.getMovie();
        if (s.getStartTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suất chiếu chưa có thời gian");
        }
        int durationMin = movie != null && movie.getDuration() != null ? movie.getDuration() : 120;
        LocalDateTime end = s.getStartTime().plusMinutes(durationMin);
        if (!now.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suất chiếu đã kết thúc — không thể đặt vé");
        }
    }

    private static void assertMovieOpenForOnlineBooking(Showtime showtime) {
        Movie movie = showtime != null ? showtime.getMovie() : null;
        if (movie == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suất chiếu chưa gắn phim");
        }
        Integer status = movie.getStatus();
        if (status != null && status == 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Phim này đang ở trạng thái sắp chiếu, chưa mở đặt vé online.");
        }
        if (status == null || status != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Phim này hiện không mở bán vé online.");
        }
    }

    private static void assertCustomerMeetsAgeLimit(User user, Showtime showtime) {
        Movie movie = showtime != null ? showtime.getMovie() : null;
        Integer ageLimit = movie != null ? movie.getAgeLimit() : null;
        if (ageLimit == null || ageLimit <= 0) {
            return;
        }

        LocalDate today = todayInAppZone();
        if (user == null || user.getBirthday() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Phim này được phân loại T" + ageLimit + ". Vui lòng cập nhật ngày sinh trong hồ sơ trước khi đặt vé.");
        }
        if (user.getBirthday().isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ngày sinh trong hồ sơ không hợp lệ. Vui lòng cập nhật lại trước khi đặt vé.");
        }

        int customerAge = Period.between(user.getBirthday(), today).getYears();
        if (customerAge < ageLimit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bạn chưa đủ " + ageLimit + " tuổi để đặt vé phim T" + ageLimit + ". Vui lòng chọn phim phù hợp độ tuổi.");
        }
    }

    /** Giá vé cơ bản (đã phụ thu) — cùng công thức {@link com.fpoly.duan.controller.ShowtimeController#toDTO}. */
    public static double computeUnitBasePrice(Showtime s) {
        Movie movie = s.getMovie();
        double basePrice = movie != null && movie.getBasePrice() != null ? movie.getBasePrice() : 0.0;
        double surcharge = s.getSurcharge() != null ? s.getSurcharge() : 0.0;
        return basePrice + surcharge;
    }

    private static LocalDateTime nowInAppZone() {
        return LocalDateTime.now(APP_ZONE);
    }

    private static LocalDate todayInAppZone() {
        return LocalDate.now(APP_ZONE);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private double calculateDiscount(double orderTotal, Voucher voucher) {
        if (voucher == null || voucher.getValue() == null) {
            return 0.0;
        }
        String type = voucher.getDiscountType();
        double value = voucher.getValue();
        double maxDiscount = voucher.getMaxDiscountAmount() != null && voucher.getMaxDiscountAmount() > 0
                ? voucher.getMaxDiscountAmount()
                : Double.MAX_VALUE;

        double discount = 0.0;
        if (isFixedDiscount(type)) {
            // FIXED amount
            discount = value;
        } else {
            // Default của hệ thống voucher hiện tại là phần trăm.
            discount = orderTotal * (value / 100.0);
        }
        // Apply max discount limit
        if (discount > maxDiscount) {
            discount = maxDiscount;
        }
        // Cannot discount more than order total
        if (discount > orderTotal) {
            discount = orderTotal;
        }
        return discount;
    }

    private boolean isFixedDiscount(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase();
        return normalized.equals("FIXED") || normalized.equals("AMOUNT") || normalized.equals("MONEY");
    }
}
