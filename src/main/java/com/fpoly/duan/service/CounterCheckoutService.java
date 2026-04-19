package com.fpoly.duan.service;

import com.fpoly.duan.config.PayOSProperties;
import com.fpoly.duan.dto.CounterCheckoutRequest;
import com.fpoly.duan.dto.payos.PayOSCheckoutData;
import com.fpoly.duan.dto.payos.PayOSCreatePaymentLinkRequest;
import com.fpoly.duan.entity.*;
import com.fpoly.duan.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
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

    @Transactional
    public Object checkout(Integer staffId, CounterCheckoutRequest request) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên"));

        Showtime showtime = showtimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy suất chiếu"));

        User customer = null;
        if (request.getUserId() != null) {
            customer = userRepository.findById(request.getUserId())
                    .orElse(null);
        }

        // 1. Tính toán giá vé và ghế
        double totalTicketsPrice = 0;
        List<Seat> selectedSeats = new ArrayList<>();
        if (request.getSeatIds() != null) {
            for (Integer seatId : request.getSeatIds()) {
                Seat seat = seatRepository.findById(seatId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy ghế ID: " + seatId));
                
                // Kiểm tra xem ghế đã có vé cho suất chiếu này chưa
                if (ticketRepository.existsByShowtime_ShowtimeIdAndSeat_SeatIdAndStatus(showtime.getShowtimeId(), seatId, 1)) {
                    throw new RuntimeException("Ghế " + seat.getRow() + seat.getNumber() + " đã được bán");
                }

                double ticketBasePrice = (showtime.getMovie() != null && showtime.getMovie().getBasePrice() != null) 
                                 ? showtime.getMovie().getBasePrice() : 0.0;
                double showtimeSurcharge = (showtime.getSurcharge() != null) ? showtime.getSurcharge() : 0.0;
                double seatTypeSurcharge = (seat.getSeatType() != null && seat.getSeatType().getSurcharge() != null) 
                                 ? seat.getSeatType().getSurcharge() : 0.0;

                double finalSeatPrice = ticketBasePrice + showtimeSurcharge + seatTypeSurcharge;
                totalTicketsPrice += finalSeatPrice;
                selectedSeats.add(seat);
            }
        }

        // 2. Tính toán giá bắp nước
        double totalFoodPrice = 0;
        List<OrderDetailFood> foodDetails = new ArrayList<>();
        if (request.getProducts() != null) {
            for (CounterCheckoutRequest.ProductItem item : request.getProducts()) {
                Product product = productRepository.findById(item.getProductId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm ID: " + item.getProductId()));
                
                double itemPrice = (product.getPrice() != null ? product.getPrice() : 0.0) * item.getQuantity();
                totalFoodPrice += itemPrice;

                OrderDetailFood detail = new OrderDetailFood();
                detail.setProduct(product);
                detail.setQuantity(item.getQuantity());
                detail.setPrice(product.getPrice());
                detail.setStatus(1); // Đã nhận/Đã bán
                foodDetails.add(detail);
            }
        }

        double totalAmount = totalTicketsPrice + totalFoodPrice;
        boolean isTransfer = "TRANSFER".equalsIgnoreCase(request.getPaymentMethod());

        // 3. Tạo hóa đơn (OrderOnline)
        OrderOnline order = new OrderOnline();
        long payOsCode = System.currentTimeMillis() / 1000; 
        order.setOrderCode("POS-" + (isTransfer ? payOsCode : UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
        order.setCreatedAt(LocalDateTime.now());
        order.setOriginalAmount(totalAmount);
        order.setFinalAmount(totalAmount);
        order.setDiscountAmount(0.0);
        order.setStatus(isTransfer ? 0 : 1); // 0: PENDING (cho Transfer), 1: PAID (cho Cash)
        order.setPaymentMethod(request.getPaymentMethod());
        order.setStaff(staff);
        order.setUser(customer);
        
        OrderOnline savedOrder = orderOnlineRepository.save(order);

        // 4. Lưu vé
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
            ticket.setStatus(isTransfer ? 0 : 1); // 0: INVALID/PENDING, 1: VALID
            ticketRepository.save(ticket);
        }

        // 5. Lưu chi tiết bắp nước
        for (OrderDetailFood detail : foodDetails) {
            detail.setOrderOnline(savedOrder);
            orderDetailFoodRepository.save(detail);
        }

        // 6. Nếu là chuyển khoản, tạo link PayOS
        if (isTransfer) {
            try {
                // Hardcode URL POS để đảm bảo luôn chạy
                String cancelUrl = "http://localhost:5173/payment/cancel";
                String returnUrl = "http://localhost:5173/payment/success";

                PayOSCreatePaymentLinkRequest payReq = PayOSCreatePaymentLinkRequest.builder()
                        .orderCode(payOsCode)
                        .amount((int) totalAmount)
                        .description("TT ve POS " + order.getOrderCode())
                        .cancelUrl(cancelUrl)
                        .returnUrl(returnUrl)
                        .buyerName(customer != null ? customer.getFullname() : "Khach tai quay")
                        .build();
                
                return payOSService.createPaymentLink(payReq);
            } catch (Exception e) {
                throw new RuntimeException("Lỗi kết nối PayOS: " + e.getMessage());
            }
        }

        return savedOrder;
    }

    @Transactional
    public OrderOnline confirmPaid(String orderCode) {
        OrderOnline order = orderOnlineRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng: " + orderCode));
        
        if (order.getStatus() == 1) return order;

        order.setStatus(1);
        OrderOnline savedOrder = orderOnlineRepository.save(order);

        // Cập nhật trạng thái các vé liên quan
        List<Ticket> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId());
        for (Ticket t : tickets) {
            t.setStatus(1);
            ticketRepository.save(t);
        }

        return savedOrder;
    }
}
