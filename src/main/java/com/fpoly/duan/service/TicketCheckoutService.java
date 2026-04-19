package com.fpoly.duan.service;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.fpoly.duan.entity.Movie;
import com.fpoly.duan.entity.MembershipRank;
import com.fpoly.duan.entity.OrderDetailFood;
import com.fpoly.duan.entity.OrderOnline;
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
import com.fpoly.duan.repository.PromotionRepository;
import com.fpoly.duan.repository.ProductRepository;
import com.fpoly.duan.repository.SeatRepository;
import com.fpoly.duan.repository.ShowtimeRepository;
import com.fpoly.duan.repository.TicketRepository;
import com.fpoly.duan.repository.UserRepository;
import com.fpoly.duan.repository.UserVoucherRepository;
import com.fpoly.duan.entity.Promotion;
import com.fpoly.duan.entity.UserVoucher;
import com.fpoly.duan.entity.Voucher;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketCheckoutService {

    private static final int ORDER_STATUS_PENDING = 0;
    private static final int ORDER_STATUS_PAID = 1;
    private static final int ORDER_STATUS_CANCELLED = 2;
    private static final int TICKET_STATUS_PENDING = 0;
    private static final int TICKET_STATUS_PAID = 1;
    private static final int TICKET_STATUS_CANCELLED = 2;
    private static final int FOOD_STATUS_PENDING = 0;
    private static final int FOOD_STATUS_PAID = 1;
    private static final int FOOD_STATUS_CANCELLED = 2;

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

        if (showtime.getRoom() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suất chiếu chưa gắn phòng");
        }
        Integer roomId = showtime.getRoom().getRoomId();
        Integer cinemaId = showtime.getRoom().getCinema() != null ? showtime.getRoom().getCinema().getCinemaId() : null;

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
        List<Integer> dbHeldSeatIds = ticketRepository.findHeldSeatIdsByShowtime(showtime.getShowtimeId());
        SeatLayoutRules.assertNoSingleSeatOrphanInRows(allRoomSeats,
                SeatLayoutRules.mergeBlocked(dbHeldSeatIds, seatIdSet));

        String clientHoldId = req.getClientHoldId();
        if (clientHoldId != null && !clientHoldId.isBlank()) {
            for (Integer sid : seatIdSet) {
                if (ephemeralSeatHoldService.isHeldByOther(showtime.getShowtimeId(), clientHoldId, sid)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Một hoặc nhiều ghế đang được người khác chọn — vui lòng chọn ghế khác");
                }
            }
        }

        long conflict = ticketRepository.countHeldOrPaidTicketsForSeats(showtime.getShowtimeId(), seatIdSet);
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
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus(ORDER_STATUS_PENDING);
        order.setUserVoucher(null);
        order.setOrderCode(String.valueOf(payosOrderCode));
        order = orderOnlineRepository.save(order);

        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < seats.size(); i++) {
            Ticket t = new Ticket();
            t.setShowtime(showtime);
            t.setSeat(seats.get(i));
            t.setOrderOnline(order);
            PricedSeatLine pl = pricedLines.get(i);
            t.setOriginalPrice(pl.originalPrice());
            // promotion_discount chỉ lưu phần KM phim
            t.setPromotionDiscount(pl.promotionDiscount());
            // price là giá cuối (đã trừ KM phim + giảm hạng)
            t.setPrice(pl.finalPrice());
            t.setStatus(TICKET_STATUS_PENDING);
            tickets.add(t);
        }
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
            LocalDate todayVoucher = LocalDate.now();
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
            ephemeralSeatHoldService.releaseSeats(showtime.getShowtimeId(), seatIdSet);
        }

        String description = truncate(
                snackVnd > 0 ? ("Ve + BNN #" + order.getOrderOnlineId()) : ("Ve xem phim #" + order.getOrderOnlineId()),
                240);

        // Use final amount after discount for PayOS
        int finalAmountVnd = (int) Math.round(finalAmount);
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

        Integer cinemaId = showtime.getRoom() != null && showtime.getRoom().getCinema() != null
                ? showtime.getRoom().getCinema().getCinemaId()
                : null;

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
        VoucherDiscount vd = computeVoucherDiscountForQuote(userId, req.getUserVoucherId(), subtotal);

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
    public void cancelPendingOrderByPayosCode(Integer userId, long payosOrderCode) {
        OrderOnline o = orderOnlineRepository.findByOrderCode(String.valueOf(payosOrderCode))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn với mã PayOS"));

        if (o.getUser() == null || !o.getUser().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Đơn không thuộc tài khoản của bạn");
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
            ephemeralSeatHoldService.releaseSeats(stId, seatIds);
        }
    }

    @Transactional
    public TicketCheckoutResponse checkoutFoodOnly(Integer userId, FoodOnlyCheckoutRequest req) {
        User user = loadUser(userId);
        cinemaRepository.findById(req.getCinemaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy rạp"));

        long payosOrderCode = allocateUniquePayosOrderCode();

        OrderOnline order = new OrderOnline();
        order.setUser(user);
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus(ORDER_STATUS_PENDING);
        order.setUserVoucher(null);
        order.setOrderCode(String.valueOf(payosOrderCode));
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

    private TicketCheckoutResponse finalizePayos(
            User user,
            long payosOrderCode,
            int amountVnd,
            String description,
            String returnUrl,
            String cancelUrl,
            OrderOnline order) {

        PayOSCreatePaymentLinkRequest payReq = PayOSCreatePaymentLinkRequest.builder()
                .orderCode(payosOrderCode)
                .amount(amountVnd)
                .description(description)
                .returnUrl(returnUrl.trim())
                .cancelUrl(cancelUrl.trim())
                .buyerName(user.getFullname() != null && !user.getFullname().isBlank() ? user.getFullname() : user.getUsername())
                .buyerEmail(user.getEmail())
                .buyerPhone(user.getPhone())
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

    private VoucherDiscount computeVoucherDiscountForQuote(Integer userId, Integer userVoucherId, double subtotal) {
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
        LocalDate today = LocalDate.now();
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
        LocalDate today = LocalDate.now();
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
        int currentYear = LocalDate.now().getYear();
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

        Optional<OrderOnline> opt = orderOnlineRepository.findByOrderCode(String.valueOf(orderCode));
        if (opt.isEmpty()) {
            /* Đơn đã bị xóa khi user hủy thanh toán — PayOS vẫn có thể gọi webhook muộn */
            return;
        }
        OrderOnline order = opt.get();

        if (order.getStatus() != null && order.getStatus() == ORDER_STATUS_PAID) {
            return;
        }
        if (order.getStatus() == null || order.getStatus() != ORDER_STATUS_PENDING) {
            /* Đã hủy / trạng thái lạ — không kích hoạt thanh toán */
            return;
        }

        int expected = (int) Math.round(order.getFinalAmount() != null ? order.getFinalAmount() : 0.0);
        if (paidAmount >= 0 && Math.abs(paidAmount - expected) > 1) {
            throw new IllegalArgumentException("Số tiền webhook không khớp đơn (expected " + expected + ", got " + paidAmount + ")");
        }

        order.setStatus(ORDER_STATUS_PAID);
        orderOnlineRepository.save(order);

        List<Ticket> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId());
        for (Ticket t : tickets) {
            t.setStatus(TICKET_STATUS_PAID);
        }
        ticketRepository.saveAll(tickets);

        List<OrderDetailFood> foods = orderDetailFoodRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId());
        for (OrderDetailFood f : foods) {
            f.setStatus(FOOD_STATUS_PAID);
        }
        orderDetailFoodRepository.saveAll(foods);

        if (order.getUser() != null) {
            recalculateUserRankFromPaidOrders(order.getUser());
        }
    }

    private void recalculateUserRankFromPaidOrders(User user) {
        int currentYear = LocalDate.now().getYear();
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
        LocalDateTime now = LocalDateTime.now();
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

    /** Giá vé cơ bản (đã phụ thu) — cùng công thức {@link com.fpoly.duan.controller.ShowtimeController#toDTO}. */
    public static double computeUnitBasePrice(Showtime s) {
        Movie movie = s.getMovie();
        double basePrice = movie != null && movie.getBasePrice() != null ? movie.getBasePrice() : 0.0;
        double surcharge = s.getSurcharge() != null ? s.getSurcharge() : 0.0;
        return basePrice + surcharge;
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
        double maxDiscount = voucher.getMaxDiscountAmount() != null ? voucher.getMaxDiscountAmount() : Double.MAX_VALUE;

        double discount = 0.0;
        if ("PERCENT".equalsIgnoreCase(type) || "%".equals(type)) {
            discount = orderTotal * (value / 100.0);
        } else {
            // FIXED amount
            discount = value;
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
}
