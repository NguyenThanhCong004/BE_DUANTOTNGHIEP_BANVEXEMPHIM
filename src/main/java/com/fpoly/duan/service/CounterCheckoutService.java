package com.fpoly.duan.service;

import com.fpoly.duan.dto.CounterCheckoutRequest;
import com.fpoly.duan.dto.payos.PayOSCheckoutData;
import com.fpoly.duan.dto.payos.PayOSCreatePaymentLinkRequest;
import com.fpoly.duan.entity.*;
import com.fpoly.duan.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class CounterCheckoutService {

    private final OrderOnlineRepository orderOnlineRepository;
    private final TicketRepository ticketRepository;
    private final OrderDetailFoodRepository orderDetailFoodRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final ProductRepository productRepository;
    private final PayOSService payOSService;
    private final MembershipRankRepository membershipRankRepository;
    private final PointsHistoryRepository pointsHistoryRepository;

    public CounterCheckoutService(
            OrderOnlineRepository orderOnlineRepository,
            TicketRepository ticketRepository,
            OrderDetailFoodRepository orderDetailFoodRepository,
            StaffRepository staffRepository,
            UserRepository userRepository,
            ShowtimeRepository showtimeRepository,
            SeatRepository seatRepository,
            ProductRepository productRepository,
            PayOSService payOSService,
            MembershipRankRepository membershipRankRepository,
            PointsHistoryRepository pointsHistoryRepository) {
        this.orderOnlineRepository = orderOnlineRepository;
        this.ticketRepository = ticketRepository;
        this.orderDetailFoodRepository = orderDetailFoodRepository;
        this.staffRepository = staffRepository;
        this.userRepository = userRepository;
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.productRepository = productRepository;
        this.payOSService = payOSService;
        this.membershipRankRepository = membershipRankRepository;
        this.pointsHistoryRepository = pointsHistoryRepository;
    }

    @Transactional
    public Object checkout(Integer staffId, CounterCheckoutRequest request) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên"));

        Showtime showtime = showtimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy suất chiếu"));

        User customer = null;
        if (request.getUserId() != null) {
            customer = userRepository.findById(request.getUserId()).orElse(null);
        }

        double totalTicketsPrice = 0;
        List<Seat> selectedSeats = new ArrayList<>();
        if (request.getSeatIds() != null) {
            for (Integer seatId : request.getSeatIds()) {
                Seat seat = seatRepository.findById(seatId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy ghế ID: " + seatId));
                
                if (ticketRepository.existsByShowtime_ShowtimeIdAndSeat_SeatIdAndStatus(showtime.getShowtimeId(), seatId, 1)) {
                    throw new RuntimeException("Ghế " + seat.getRow() + seat.getNumber() + " đã được bán");
                }

                double ticketBasePrice = (showtime.getMovie() != null && showtime.getMovie().getBasePrice() != null) 
                                 ? showtime.getMovie().getBasePrice() : 0.0;
                double showtimeSurcharge = (showtime.getSurcharge() != null) ? showtime.getSurcharge() : 0.0;
                double seatTypeSurcharge = (seat.getSeatType() != null && seat.getSeatType().getSurcharge() != null) 
                                 ? seat.getSeatType().getSurcharge() : 0.0;

                totalTicketsPrice += (ticketBasePrice + showtimeSurcharge + seatTypeSurcharge);
                selectedSeats.add(seat);
            }
        }

        double totalFoodPrice = 0;
        List<OrderDetailFood> foodDetails = new ArrayList<>();
        if (request.getProducts() != null) {
            for (CounterCheckoutRequest.ProductItem item : request.getProducts()) {
                Product product = productRepository.findById(item.getProductId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm ID: " + item.getProductId()));
                
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
        long payOsCode = System.currentTimeMillis() / 1000; 
        order.setOrderCode("POS-" + (isTransfer ? payOsCode : UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
        order.setCreatedAt(LocalDateTime.now());
        order.setOriginalAmount(totalAmount);
        order.setFinalAmount(totalAmount);
        order.setDiscountAmount(0.0);
        order.setStatus(isTransfer ? 0 : 1);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setStaff(staff);
        order.setCinema(staff.getCinema());
        order.setUser(customer);
        
        OrderOnline savedOrder = orderOnlineRepository.save(order);

        for (Seat seat : selectedSeats) {
            Ticket ticket = new Ticket();
            ticket.setShowtime(showtime);
            ticket.setSeat(seat);
            ticket.setOrderOnline(savedOrder);
            
            double ticketBasePrice = (showtime.getMovie() != null && showtime.getMovie().getBasePrice() != null) 
                                     ? showtime.getMovie().getBasePrice() : 0.0;
            double showtimeSurcharge = (showtime.getSurcharge() != null) ? showtime.getSurcharge() : 0.0;
            double seatTypeSurcharge = (seat.getSeatType() != null && seat.getSeatType().getSurcharge() != null) 
                                     ? seat.getSeatType().getSurcharge() : 0.0;
            
            ticket.setPrice(ticketBasePrice + showtimeSurcharge + seatTypeSurcharge);
            ticket.setStatus(isTransfer ? 0 : 1);
            ticketRepository.save(ticket);
        }

        for (OrderDetailFood detail : foodDetails) {
            detail.setOrderOnline(savedOrder);
            orderDetailFoodRepository.save(detail);
        }

        if (isTransfer) {
            try {
                PayOSCreatePaymentLinkRequest payReq = PayOSCreatePaymentLinkRequest.builder()
                        .orderCode(payOsCode)
                        .amount((int) totalAmount)
                        .description("TT POS " + payOsCode)
                        .cancelUrl("http://localhost:5173/payment/cancel")
                        .returnUrl("http://localhost:5173/payment/success")
                        .buyerName(customer != null ? customer.getFullname() : "Khach POS")
                        .build();
                return payOSService.createPaymentLink(payReq);
            } catch (Exception e) {
                throw new RuntimeException("PayOS Error: " + e.getMessage());
            }
        }

        return savedOrder;
    }

    @Transactional
    public OrderOnline checkStatus(String orderCode) {
        final String searchCode;
        final String rawCode;
        if (orderCode != null && !orderCode.startsWith("POS-") && orderCode.matches("\\d+")) {
            searchCode = "POS-" + orderCode;
            rawCode = orderCode;
        } else if (orderCode != null && orderCode.startsWith("POS-")) {
            searchCode = orderCode;
            rawCode = orderCode.substring(4);
        } else {
            searchCode = orderCode;
            rawCode = orderCode;
        }

        OrderOnline order = orderOnlineRepository.findByOrderCode(searchCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng: " + searchCode));

        if (order.getStatus() == 1) return order;

        if ("TRANSFER".equalsIgnoreCase(order.getPaymentMethod()) && rawCode.matches("\\d+")) {
            try {
                PayOSCheckoutData payosInfo = payOSService.getPaymentInformation(Long.parseLong(rawCode));
                if ("PAID".equalsIgnoreCase(payosInfo.getStatus())) {
                    return confirmPaid(orderCode);
                }
            } catch (Exception e) {
                // Có thể PayOS chưa tạo kịp link hoặc lỗi mạng, ta cứ để PENDING
                System.err.println("Check PayOS status error: " + e.getMessage());
            }
        }

        return order;
    }

    @Transactional
    public OrderOnline confirmPaid(String orderCode) {
        final String searchCode;
        if (orderCode != null && !orderCode.startsWith("POS-") && orderCode.matches("\\d+")) {
            searchCode = "POS-" + orderCode;
        } else {
            searchCode = orderCode;
        }

        OrderOnline order = orderOnlineRepository.findByOrderCode(searchCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng: " + searchCode));
        
        if (order.getStatus() == 1) return order;

        order.setStatus(1);
        OrderOnline savedOrder = orderOnlineRepository.save(order);

        // Cập nhật trạng thái các vé liên quan thành hợp lệ (1)
        List<Ticket> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId());
        for (Ticket t : tickets) {
            t.setStatus(1);
            ticketRepository.save(t);
        }

        // Cộng điểm cho user
        if (order.getUser() != null) {
            addPointsForOrder(order);
        }

        return savedOrder;
    }

    /**
     * Cộng điểm cho user sau khi thanh toán thành công tại quầy
     * Quy tắc: 1k = 1 điểm + điểm bonus theo rank
     */
    private void addPointsForOrder(OrderOnline order) {
        try {
            User user = order.getUser();
            if (user == null) {
                System.out.println("CounterCheckoutService.addPointsForOrder: User is null, skipping");
                return;
            }

            double finalAmount = order.getFinalAmount() != null ? order.getFinalAmount() : 0.0;
            System.out.println("CounterCheckoutService.addPointsForOrder: orderCode=" + order.getOrderCode() + ", finalAmount=" + finalAmount + ", userId=" + user.getUserId());
            
            // Tính điểm từ số tiền: 1k = 1 điểm (làm tròn)
            int pointsFromAmount = (int) Math.round(finalAmount / 1000);
            System.out.println("CounterCheckoutService.addPointsForOrder: pointsFromAmount=" + pointsFromAmount);
            
            // Lấy điểm bonus từ rank
            MembershipRank rank = resolveEffectiveRank(user);
            int bonusPoints = (rank != null && rank.getBonusPoint() != null) ? rank.getBonusPoint() : 0;
            System.out.println("CounterCheckoutService.addPointsForOrder: rank=" + (rank != null ? rank.getRankName() : "null") + ", bonusPoints=" + bonusPoints);
            
            // Tổng điểm
            int totalPoints = pointsFromAmount + bonusPoints;
            System.out.println("CounterCheckoutService.addPointsForOrder: totalPoints=" + totalPoints);
            
            if (totalPoints <= 0) {
                System.out.println("CounterCheckoutService.addPointsForOrder: totalPoints <= 0, skipping");
                return;
            }
            
            // Cộng điểm vào user
            int currentPoints = user.getPoints() != null ? user.getPoints() : 0;
            user.setPoints(currentPoints + totalPoints);
            userRepository.save(user);
            System.out.println("CounterCheckoutService.addPointsForOrder: Updated user points from " + currentPoints + " to " + user.getPoints());
            
            // Lưu lịch sử điểm
            PointsHistory pointsHistory = new PointsHistory();
            pointsHistory.setUser(user);
            pointsHistory.setDate(LocalDate.now());
            pointsHistory.setDescription("Tích điểm từ đơn " + order.getOrderCode() + 
                                       " (" + pointsFromAmount + " điểm từ số tiền" + 
                                       (bonusPoints > 0 ? " + " + bonusPoints + " điểm bonus" : "") + ")");
            pointsHistory.setPoints(totalPoints);
            pointsHistoryRepository.save(pointsHistory);
            System.out.println("CounterCheckoutService.addPointsForOrder: Saved points history");
        } catch (Exception e) {
            System.err.println("CounterCheckoutService.addPointsForOrder: Error adding points for order " + order.getOrderCode());
            e.printStackTrace();
        }
    }

    /**
     * Xác định rank hiện tại của user dựa trên tổng chi tiêu trong năm
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

    @Transactional
    public void cancelOrder(String orderCode) {
        final String searchCode;
        if (orderCode != null && !orderCode.startsWith("POS-") && orderCode.matches("\\d+")) {
            searchCode = "POS-" + orderCode;
        } else {
            searchCode = orderCode;
        }

        OrderOnline order = orderOnlineRepository.findByOrderCode(searchCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng: " + searchCode));
        
        // Chỉ cho phép hủy đơn đang chờ thanh toán (status 0)
        if (order.getStatus() != 0) {
            throw new RuntimeException("Chỉ có thể hủy đơn hàng đang chờ thanh toán");
        }

        order.setStatus(2); // 2: CANCELED
        orderOnlineRepository.save(order);

        // Giải phóng ghế bằng cách đặt trạng thái vé về 0
        List<Ticket> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId());
        for (Ticket t : tickets) {
            t.setStatus(0); // 0: INVALID
            ticketRepository.save(t);
        }
    }
}
