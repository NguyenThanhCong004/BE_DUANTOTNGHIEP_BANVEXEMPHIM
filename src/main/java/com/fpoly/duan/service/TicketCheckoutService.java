package com.fpoly.duan.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
import com.fpoly.duan.dto.payos.PayOSCheckoutData;
import com.fpoly.duan.dto.payos.PayOSCreatePaymentLinkRequest;
import com.fpoly.duan.entity.CinemaProduct;
import com.fpoly.duan.entity.Movie;
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
import com.fpoly.duan.repository.ProductRepository;
import com.fpoly.duan.repository.SeatRepository;
import com.fpoly.duan.repository.ShowtimeRepository;
import com.fpoly.duan.repository.TicketRepository;
import com.fpoly.duan.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketCheckoutService {

    private static final int ORDER_STATUS_PENDING = 0;
    private static final int ORDER_STATUS_PAID = 1;
    private static final int TICKET_STATUS_PENDING = 0;
    private static final int TICKET_STATUS_PAID = 1;
    private static final int FOOD_STATUS_PENDING = 0;
    private static final int FOOD_STATUS_PAID = 1;

    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;
    private final OrderOnlineRepository orderOnlineRepository;
    private final UserRepository userRepository;
    private final PayOSService payOSService;
    private final CinemaProductRepository cinemaProductRepository;
    private final ProductRepository productRepository;
    private final OrderDetailFoodRepository orderDetailFoodRepository;
    private final CinemaRepository cinemaRepository;
    private final EphemeralSeatHoldService ephemeralSeatHoldService;

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

        List<Seat> seats = seatRepository.findAllById(seatIdSet);
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

        double unitBase = computeUnitBasePrice(showtime);
        List<Double> linePrices = new ArrayList<>();
        int ticketVnd = 0;
        for (Seat seat : seats) {
            double surcharge = 0.0;
            SeatType st = seat.getSeatType();
            if (st != null && st.getSurcharge() != null) {
                surcharge = st.getSurcharge();
            }
            double line = unitBase + surcharge;
            linePrices.add(line);
            ticketVnd += (int) Math.round(line);
        }
        double ticketDouble = linePrices.stream().mapToDouble(Double::doubleValue).sum();

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
            t.setPrice(linePrices.get(i));
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

        order.setOriginalAmount(totalDouble);
        order.setDiscountAmount(0.0);
        order.setFinalAmount(totalDouble);
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

        return finalizePayos(user, payosOrderCode, amountVnd, description, req.getReturnUrl(), req.getCancelUrl(), order);
    }

    /**
     * Khách hủy thanh toán PayOS — xóa đơn chờ, vé chờ, chi tiết đồ ăn chờ để trả ghế.
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

        ticketRepository.deleteAll(tickets);
        orderDetailFoodRepository.deleteAll(foods);
        orderOnlineRepository.delete(o);

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
            int lineV = (int) Math.round(lineD);
            vndTotal += lineV;
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
}
