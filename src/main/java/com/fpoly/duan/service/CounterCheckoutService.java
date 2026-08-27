package com.fpoly.duan.service;

import com.fpoly.duan.dto.CounterCheckoutRequest;
import com.fpoly.duan.dto.payos.PayOSCheckoutData;
import com.fpoly.duan.dto.payos.PayOSCreatePaymentLinkRequest;
import com.fpoly.duan.entity.*;
import com.fpoly.duan.repository.*;
import com.fpoly.duan.util.SeatLabel;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CounterCheckoutService {
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long PENDING_SEAT_HOLD_MINUTES = 5;

    @Value("${app.password-reset.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    private final OrderOnlineRepository orderOnlineRepository;
    private final TicketRepository ticketRepository;
    private final OrderDetailFoodRepository orderDetailFoodRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final ProductRepository productRepository;
    private final CinemaProductRepository cinemaProductRepository;
    private final PayOSService payOSService;
    private final MembershipRankRepository membershipRankRepository;
    private final PointsHistoryRepository pointsHistoryRepository;
    private final TicketQrService ticketQrService;
    private final TicketEmailService ticketEmailService;
    private final EphemeralSeatHoldService ephemeralSeatHoldService;
    private final CinemaScopeService cinemaScopeService;

    public CounterCheckoutService(
            OrderOnlineRepository orderOnlineRepository,
            TicketRepository ticketRepository,
            OrderDetailFoodRepository orderDetailFoodRepository,
            StaffRepository staffRepository,
            UserRepository userRepository,
            ShowtimeRepository showtimeRepository,
            SeatRepository seatRepository,
            ProductRepository productRepository,
            CinemaProductRepository cinemaProductRepository,
            PayOSService payOSService,
            MembershipRankRepository membershipRankRepository,
            PointsHistoryRepository pointsHistoryRepository,
            TicketQrService ticketQrService,
            TicketEmailService ticketEmailService,
            EphemeralSeatHoldService ephemeralSeatHoldService,
            CinemaScopeService cinemaScopeService) {
        this.orderOnlineRepository = orderOnlineRepository;
        this.ticketRepository = ticketRepository;
        this.orderDetailFoodRepository = orderDetailFoodRepository;
        this.staffRepository = staffRepository;
        this.userRepository = userRepository;
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.productRepository = productRepository;
        this.cinemaProductRepository = cinemaProductRepository;
        this.payOSService = payOSService;
        this.membershipRankRepository = membershipRankRepository;
        this.pointsHistoryRepository = pointsHistoryRepository;
        this.ticketQrService = ticketQrService;
        this.ticketEmailService = ticketEmailService;
        this.ephemeralSeatHoldService = ephemeralSeatHoldService;
        this.cinemaScopeService = cinemaScopeService;
    }

    @Transactional
    public Object checkout(Integer staffId, CounterCheckoutRequest request) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên"));
        Integer staffCinemaId = requireStaffCinema(staff);
        requireOperationalCinema(staff.getCinema());

        boolean hasSeats = request.getSeatIds() != null && !request.getSeatIds().isEmpty();
        boolean hasProducts = request.getProducts() != null && !request.getProducts().isEmpty();
        if (!hasSeats && !hasProducts) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ghế hoặc bắp nước");
        }

        Showtime showtime = null;
        if (hasSeats) {
            if (request.getShowtimeId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu ID suất chiếu khi bán vé");
            }
            showtime = showtimeRepository.findByIdForUpdate(request.getShowtimeId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy suất chiếu"));
            assertSameCinema(staffCinemaId, showtimeCinemaId(showtime), "Suất chiếu không thuộc rạp của nhân viên");
            cinemaScopeService.requireRoomOperational(showtime.getRoom());
        }

        User customer = null;
        if (request.getUserId() != null) {
            customer = userRepository.findById(request.getUserId()).orElse(null);
        }

        double totalTicketsPrice = 0;
        List<Seat> selectedSeats = new ArrayList<>();
        if (hasSeats) {
            Integer showtimeRoomId = showtime.getRoom() != null ? showtime.getRoom().getRoomId() : null;
            for (Integer seatId : request.getSeatIds()) {
                Seat seat = seatRepository.findById(seatId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy ghế ID: " + seatId));

                if (showtimeRoomId != null
                        && (seat.getRoom() == null || !showtimeRoomId.equals(seat.getRoom().getRoomId()))) {
                    throw new RuntimeException("Ghế " + seat.getRow() + seat.getNumber() + " không thuộc phòng của suất chiếu");
                }
                
                LocalDateTime pendingSince = nowInAppZone().minusMinutes(PENDING_SEAT_HOLD_MINUTES);
                if (ticketRepository.countHeldOrPaidTicketsForSeats(showtime.getShowtimeId(), List.of(seatId), pendingSince) > 0) {
                    throw new RuntimeException("Ghế " + seat.getRow() + seat.getNumber() + " đã được giữ hoặc đã bán");
                }

                double ticketBasePrice = (showtime.getMovie() != null && showtime.getMovie().getBasePrice() != null) 
                                 ? showtime.getMovie().getBasePrice() : 0.0;
                double showtimeSurcharge = (showtime.getSurcharge() != null) ? showtime.getSurcharge() : 0.0;
                double seatTypeSurcharge = (seat.getSeatType() != null && seat.getSeatType().getSurcharge() != null) 
                                 ? seat.getSeatType().getSurcharge() : 0.0;

                totalTicketsPrice += (ticketBasePrice + showtimeSurcharge + seatTypeSurcharge);
                selectedSeats.add(seat);
            }

            if (showtimeRoomId != null) {
                List<Seat> allRoomSeats = seatRepository.findByRoom_RoomId(showtimeRoomId);
                LocalDateTime pendingSince = nowInAppZone().minusMinutes(PENDING_SEAT_HOLD_MINUTES);
                List<Integer> dbHeldSeatIds = ticketRepository.findHeldSeatIdsByShowtime(
                        showtime.getShowtimeId(), pendingSince);
                Set<Integer> newlySelectedSeatIds = selectedSeats.stream()
                        .map(Seat::getSeatId)
                        .collect(Collectors.toSet());
                SeatLayoutRules.assertNoNewSingleSeatOrphanInRows(allRoomSeats,
                        new HashSet<>(dbHeldSeatIds), newlySelectedSeatIds);
            }
        }

        double totalFoodPrice = 0;
        List<OrderDetailFood> foodDetails = new ArrayList<>();
        if (hasProducts) {
            for (CounterCheckoutRequest.ProductItem item : request.getProducts()) {
                CinemaProduct cinemaProduct = cinemaProductRepository
                        .findByCinema_CinemaIdAndProduct_ProductId(staffCinemaId, item.getProductId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Sản phẩm chưa được bán tại rạp này: " + item.getProductId()));
                if (!Boolean.TRUE.equals(cinemaProduct.getIsActive())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Sản phẩm đang hết hàng tại rạp này: " + item.getProductId());
                }
                Product product = cinemaProduct.getProduct();
                if (product == null) {
                    throw new RuntimeException("Không tìm thấy sản phẩm ID: " + item.getProductId());
                }
                Integer productStatus = product.getStatus() != null ? product.getStatus() : 1;
                if (productStatus != 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Sản phẩm đã bị khóa: " + item.getProductId());
                }
                
                totalFoodPrice += (product.getPrice() != null ? product.getPrice() : 0.0) * item.getQuantity();

                OrderDetailFood detail = new OrderDetailFood();
                detail.setProduct(product);
                detail.setQuantity(item.getQuantity());
                detail.setPrice(product.getPrice());
                detail.setStatus(1);
                foodDetails.add(detail);
            }
        }

        double totalAmount = totalTicketsPrice + totalFoodPrice;
        boolean isTransfer = "TRANSFER".equalsIgnoreCase(request.getPaymentMethod());

        OrderOnline order = new OrderOnline();
        long payOsCode = isTransfer ? allocateUniquePosPayosCode() : 0L;
        order.setOrderCode("POS-" + (isTransfer ? payOsCode : UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
        order.setCreatedAt(nowInAppZone());
        order.setOriginalAmount(totalAmount);
        order.setFinalAmount(totalAmount);
        order.setDiscountAmount(0.0);
        order.setStatus(isTransfer ? 0 : 1);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setStaff(staff);
        order.setCinema(staff.getCinema());
        order.setUser(customer);
        
        OrderOnline savedOrder = orderOnlineRepository.save(order);

        List<Ticket> ticketsToSave = new ArrayList<>();
        for (Seat seat : selectedSeats) {
            Ticket ticket = new Ticket();
            ticket.setShowtime(showtime);
            ticket.setSeat(seat);
            ticket.setSeatLabel(SeatLabel.of(seat));
            ticket.setOrderOnline(savedOrder);

            double ticketBasePrice = (showtime.getMovie() != null && showtime.getMovie().getBasePrice() != null)
                                     ? showtime.getMovie().getBasePrice() : 0.0;
            double showtimeSurcharge = (showtime.getSurcharge() != null) ? showtime.getSurcharge() : 0.0;
            double seatTypeSurcharge = (seat.getSeatType() != null && seat.getSeatType().getSurcharge() != null)
                                     ? seat.getSeatType().getSurcharge() : 0.0;

            ticket.setPrice(ticketBasePrice + showtimeSurcharge + seatTypeSurcharge);
            ticket.setStatus(isTransfer ? 0 : 1);
            ticketsToSave.add(ticket);
        }
        ticketRepository.saveAll(ticketsToSave);
        ticketQrService.assignSecureCodes(ticketsToSave);
        ticketRepository.saveAll(ticketsToSave);

        for (OrderDetailFood detail : foodDetails) {
            detail.setOrderOnline(savedOrder);
        }
        orderDetailFoodRepository.saveAll(foodDetails);

        // Bán thành công (đã tạo đơn/vé thật) — nhả các ghế "giữ tạm" ephemeral của nhân viên này khỏi
        // bộ đếm chống phá, tránh bị tính nhầm là "giữ ghế rồi bỏ" khi ca bán vé kéo dài lâu hơn ngưỡng.
        if (hasSeats) {
            Set<Integer> soldSeatIds = selectedSeats.stream().map(Seat::getSeatId).collect(Collectors.toSet());
            ephemeralSeatHoldService.releaseSeats(showtime.getShowtimeId(), soldSeatIds, null, staffId);
        }

        if (!isTransfer) {
            ticketEmailService.sendPaidTicketEmailIfNeeded(savedOrder);
            if (customer != null) {
                addPointsForOrder(savedOrder);
            }
        }

        if (isTransfer) {
            try {
                PayOSCreatePaymentLinkRequest payReq = PayOSCreatePaymentLinkRequest.builder()
                        .orderCode(payOsCode)
                        .amount((int) totalAmount)
                        .description("TT POS " + payOsCode)
                        .cancelUrl(frontendBaseUrl + "/payment/cancel")
                        .returnUrl(frontendBaseUrl + "/payment/success")
                        .buyerName(customer != null ? customer.getFullname() : "Khach POS")
                        .expiredAt(System.currentTimeMillis() / 1000 + 300)
                        .build();
                return payOSService.createPaymentLink(payReq);
            } catch (Exception e) {
                throw new RuntimeException("PayOS Error: " + e.getMessage());
            }
        }

        return savedOrder;
    }

    @Transactional
    public OrderOnline checkStatus(Integer staffId, String orderCode) {
        Staff actor = staffRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên"));
        final String searchCode = normalizePosOrderCode(orderCode);
        final String rawCode = rawPosPayosCode(searchCode);
        OrderOnline order = orderOnlineRepository.findByOrderCode(searchCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng: " + searchCode));
        assertOrderAccessible(actor, order);

        if (order.getStatus() == 1) return order;

        if ("TRANSFER".equalsIgnoreCase(order.getPaymentMethod()) && rawCode.matches("\\d+")) {
            try {
                PayOSCheckoutData payosInfo = payOSService.getPaymentInformation(Long.parseLong(rawCode));
                if ("PAID".equalsIgnoreCase(payosInfo.getStatus())) {
                    return markPaid(order);
                }
            } catch (Exception e) {
                // Có thể PayOS chưa tạo kịp link hoặc lỗi mạng, ta cứ để PENDING
                log.warn("Check PayOS status error for POS order {}: {}", searchCode, e.getMessage());
            }
        }

        return order;
    }

    @Transactional
    public OrderOnline confirmPaid(Integer staffId, String orderCode) {
        Staff actor = staffRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên"));
        final String searchCode = normalizePosOrderCode(orderCode);
        final String rawCode = rawPosPayosCode(searchCode);
        OrderOnline order = orderOnlineRepository.findByOrderCode(searchCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng: " + searchCode));
        assertOrderAccessible(actor, order);
        
        if (order.getStatus() == 1) return order;

        if (!"TRANSFER".equalsIgnoreCase(order.getPaymentMethod())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ xác nhận PayOS cho đơn chuyển khoản");
        }
        if (rawCode == null || !rawCode.matches("\\d+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã đơn PayOS không hợp lệ");
        }
        try {
            PayOSCheckoutData payosInfo = payOSService.getPaymentInformation(Long.parseLong(rawCode));
            if (!"PAID".equalsIgnoreCase(payosInfo.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PayOS chưa xác nhận đơn đã thanh toán");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không kiểm tra được trạng thái PayOS: " + e.getMessage());
        }

        return markPaid(order);
    }

    private OrderOnline markPaid(OrderOnline order) {
        order.setStatus(1);
        OrderOnline savedOrder = orderOnlineRepository.save(order);
        ticketQrService.assignReceiptToken(savedOrder);
        savedOrder = orderOnlineRepository.save(savedOrder);

        List<Ticket> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId());
        tickets.forEach(t -> t.setStatus(1));
        ticketRepository.saveAll(tickets);

        ticketEmailService.sendPaidTicketEmailIfNeeded(savedOrder);

        // Cộng điểm cho user
        if (order.getUser() != null) {
            addPointsForOrder(order);
        }

        return savedOrder;
    }

    /** Chỉ sinh barcode cho token hóa đơn đang được lưu, tránh mã tự nhập/giả. */
    @Transactional(readOnly = true)
    public byte[] getReceiptBarcode(String receiptToken) {
        if (receiptToken == null || receiptToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã vạch hóa đơn không hợp lệ");
        }
        OrderOnline order = orderOnlineRepository.findByReceiptToken(receiptToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hóa đơn hợp lệ"));
        if (order.getStatus() == null || order.getStatus() == 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hóa đơn đã hủy, mã vạch không còn hiệu lực");
        }
        return ticketQrService.toBarcodePng(order.getReceiptToken());
    }

    /** QR thanh toán PayOS tại quầy: BE sinh ảnh để FE không phụ thuộc dịch vụ QR bên ngoài. */
    public byte[] getPaymentQr(String qrCode) {
        if (qrCode == null || qrCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu dữ liệu QR thanh toán");
        }
        if (qrCode.length() > 1200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dữ liệu QR thanh toán quá dài");
        }
        return ticketQrService.toPaymentQrPng(qrCode);
    }

    /**
     * Cộng điểm cho user sau khi thanh toán thành công tại quầy
     * Quy tắc: 1k = 1 điểm + điểm bonus theo rank
     */
    private void addPointsForOrder(OrderOnline order) {
        try {
            User user = order.getUser();
            if (user == null) {
                log.debug("Skip adding counter-order points because order {} has no user", order.getOrderCode());
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
                log.debug("Skip adding non-positive counter-order points for order {}", order.getOrderCode());
                return;
            }
            
            // Cộng điểm vào user + cập nhật tổng chi tiêu
            int currentPoints = user.getPoints() != null ? user.getPoints() : 0;
            user.setPoints(currentPoints + totalPoints);
            double currentSpending = user.getTotalSpending() != null ? user.getTotalSpending() : 0.0;
            user.setTotalSpending(currentSpending + finalAmount);
            userRepository.save(user);
            
            // Lưu lịch sử điểm
            PointsHistory pointsHistory = new PointsHistory();
            pointsHistory.setUser(user);
            pointsHistory.setDate(todayInAppZone());
            pointsHistory.setDescription("Tích điểm từ đơn " + order.getOrderCode() + 
                                       " (" + pointsFromAmount + " điểm từ số tiền" + 
                                       (bonusPoints > 0 ? " + " + bonusPoints + " điểm bonus" : "") + ")");
            pointsHistory.setPoints(totalPoints);
            pointsHistoryRepository.save(pointsHistory);
            log.debug("Added {} points for counter order {} and user {}", totalPoints, order.getOrderCode(), user.getUserId());
        } catch (Exception e) {
            log.error("Error adding points for counter order {}", order.getOrderCode(), e);
        }
    }

    /**
     * Xác định rank hiện tại của user dựa trên tổng chi tiêu trong năm
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

    @Transactional
    public void cancelOrder(Integer staffId, String orderCode) {
        Staff actor = staffRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên"));
        final String searchCode = normalizePosOrderCode(orderCode);
        OrderOnline order = orderOnlineRepository.findByOrderCode(searchCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng: " + searchCode));
        assertOrderAccessible(actor, order);
        
        // Chỉ cho phép hủy đơn đang chờ thanh toán (status 0)
        if (order.getStatus() != 0) {
            throw new RuntimeException("Chỉ có thể hủy đơn hàng đang chờ thanh toán");
        }

        order.setStatus(2); // 2: CANCELED
        orderOnlineRepository.save(order);

        // Giải phóng ghế bằng cách đặt trạng thái vé về 0
        List<Ticket> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId());
        for (Ticket t : tickets) {
            t.setStatus(2); // 2: CANCELLED
            ticketRepository.save(t);
        }

        // Huỷ chủ động của nhân viên — không tính là "giữ ghế rồi bỏ" (khác với đơn bị hệ thống tự huỷ).
        Integer stId = tickets.stream()
                .filter(t -> t.getShowtime() != null)
                .map(t -> t.getShowtime().getShowtimeId())
                .findFirst().orElse(null);
        if (stId != null) {
            Set<Integer> seatIds = tickets.stream()
                    .filter(t -> t.getSeat() != null)
                    .map(t -> t.getSeat().getSeatId())
                    .collect(Collectors.toSet());
            ephemeralSeatHoldService.releaseSeats(stId, seatIds, null, staffId);
        }
    }

    private Integer requireStaffCinema(Staff staff) {
        Integer cinemaId = staff != null && staff.getCinema() != null ? staff.getCinema().getCinemaId() : null;
        if (cinemaId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nhân viên chưa được gán rạp");
        }
        return cinemaId;
    }

    private void requireOperationalCinema(Cinema cinema) {
        if (cinema == null || (cinema.getStatus() != null && cinema.getStatus() != 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rạp đang tạm ngừng hoạt động, không thể bán vé hoặc bắp nước tại quầy");
        }
    }

    private void assertSameCinema(Integer staffCinemaId, Integer targetCinemaId, String message) {
        if (targetCinemaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không xác định được rạp");
        }
        if (!staffCinemaId.equals(targetCinemaId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private Integer showtimeCinemaId(Showtime showtime) {
        return showtime != null && showtime.getRoom() != null && showtime.getRoom().getCinema() != null
                ? showtime.getRoom().getCinema().getCinemaId()
                : null;
    }

    private void assertOrderAccessible(Staff actor, OrderOnline order) {
        if (hasStaffRole(actor, "SUPER_ADMIN")) {
            return;
        }
        Integer actorCinemaId = requireStaffCinema(actor);
        Integer orderCinemaId = resolveOrderCinemaId(order);
        if (orderCinemaId == null || !actorCinemaId.equals(orderCinemaId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền thao tác đơn của rạp khác");
        }
    }

    private Integer resolveOrderCinemaId(OrderOnline order) {
        if (order == null) return null;
        if (order.getCinema() != null) {
            return order.getCinema().getCinemaId();
        }
        Integer idFromTicket = ticketRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId()).stream()
                .filter(t -> t.getShowtime() != null && t.getShowtime().getRoom() != null
                        && t.getShowtime().getRoom().getCinema() != null)
                .map(t -> t.getShowtime().getRoom().getCinema().getCinemaId())
                .findFirst()
                .orElse(null);
        if (idFromTicket != null) {
            return idFromTicket;
        }
        return order.getStaff() != null && order.getStaff().getCinema() != null
                ? order.getStaff().getCinema().getCinemaId()
                : null;
    }

    private boolean hasStaffRole(Staff staff, String role) {
        if (staff == null || staff.getRole() == null) {
            return false;
        }
        String normalized = staff.getRole().trim().toUpperCase().replaceFirst("^ROLE_", "");
        return normalized.equals(role);
    }

    private String normalizePosOrderCode(String orderCode) {
        if (orderCode == null || orderCode.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu mã đơn hàng");
        }
        String trimmed = orderCode.trim();
        if (!trimmed.startsWith("POS-") && trimmed.matches("\\d+")) {
            return "POS-" + trimmed;
        }
        return trimmed;
    }

    private String rawPosPayosCode(String normalizedOrderCode) {
        return normalizedOrderCode != null && normalizedOrderCode.startsWith("POS-")
                ? normalizedOrderCode.substring(4)
                : normalizedOrderCode;
    }

    private long allocateUniquePosPayosCode() {
        for (int attempt = 0; attempt < 50; attempt++) {
            long candidate = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000_000L);
            if (!orderOnlineRepository.existsByOrderCode("POS-" + candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Không sinh được mã đơn POS PayOS duy nhất");
    }

    private LocalDateTime nowInAppZone() {
        return LocalDateTime.now(APP_ZONE);
    }

    private LocalDate todayInAppZone() {
        return LocalDate.now(APP_ZONE);
    }
}
