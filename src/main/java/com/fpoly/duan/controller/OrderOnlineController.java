package com.fpoly.duan.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.OrderOnlineDTO;
import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.repository.OrderDetailFoodRepository;
import com.fpoly.duan.repository.OrderOnlineRepository;
import com.fpoly.duan.repository.TicketRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/orders-online")
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Table: order_online", description = "Danh sách và quản lý đơn đặt online (bảng order_online).")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
@RequiredArgsConstructor
public class OrderOnlineController {

    private final OrderOnlineRepository orderOnlineRepository;
    private final TicketRepository ticketRepository;
    private final OrderDetailFoodRepository orderDetailFoodRepository;

    @GetMapping
    @Operation(summary = "Danh sách đơn online (có lọc theo rạp)")
    public ResponseEntity<ApiResponse<List<OrderOnlineDTO>>> list(@RequestParam(required = false) Integer cinemaId) {
        List<OrderOnline> orders;
        if (cinemaId != null) {
            // Lọc các đơn có chứa ít nhất 1 vé thuộc rạp cinemaId HOẶC do nhân viên rạp đó bán
            orders = orderOnlineRepository.findAll().stream()
                    .filter(o -> {
                        // Check staff
                        if (o.getStaff() != null && o.getStaff().getCinema() != null
                                && o.getStaff().getCinema().getCinemaId().equals(cinemaId)) {
                            return true;
                        }
                        // Check tickets in this order
                        return ticketRepository.findByOrderOnline_OrderOnlineId(o.getOrderOnlineId()).stream()
                                .anyMatch(t -> t.getShowtime() != null && t.getShowtime().getRoom() != null
                                        && t.getShowtime().getRoom().getCinema() != null
                                        && t.getShowtime().getRoom().getCinema().getCinemaId().equals(cinemaId));
                    })
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .collect(Collectors.toList());
        } else {
            orders = orderOnlineRepository.findAll().stream()
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .collect(Collectors.toList());
        }

        List<OrderOnlineDTO> data = orders.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.<List<OrderOnlineDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(data)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết đơn")
    public ResponseEntity<ApiResponse<OrderOnlineDTO>> getById(@PathVariable Integer id) {
        OrderOnline o = orderOnlineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn với id: " + id));
        return ResponseEntity.ok(ApiResponse.<OrderOnlineDTO>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(toDTO(o))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa đơn online (admin)")
    public ResponseEntity<ApiResponse<Void>> deleteOrderOnline(@PathVariable Integer id) {
        if (!orderOnlineRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy đơn với id: " + id);
        }
        orderOnlineRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Đã xóa đơn")
                .data(null)
                .build());
    }

    private OrderOnlineDTO toDTO(OrderOnline o) {
        User u = o.getUser();
        Staff s = o.getStaff();

        String name = u != null ? (u.getFullname() != null && !u.getFullname().isBlank()
                ? u.getFullname()
                : u.getUsername()) : "Khách vãng lai";
        String email = u != null && u.getEmail() != null ? u.getEmail() : "—";

        // Lấy thông tin vé
        List<OrderOnlineDTO.TicketInfoDTO> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(o.getOrderOnlineId())
                .stream().map(t -> OrderOnlineDTO.TicketInfoDTO.builder()
                        .movieTitle(t.getShowtime() != null && t.getShowtime().getMovie() != null
                                ? t.getShowtime().getMovie().getTitle()
                                : "N/A")
                        .showtime(t.getShowtime() != null ? t.getShowtime().getStartTime() : null)
                        .seatNumber(t.getSeat() != null
                                ? (t.getSeat().getRow() + t.getSeat().getNumber())
                                : "N/A")
                        .originalPrice(t.getOriginalPrice() != null ? t.getOriginalPrice() : t.getPrice())
                        .promotionDiscount(t.getPromotionDiscount() != null ? t.getPromotionDiscount() : 0.0)
                        .price(t.getPrice())
                        .build())
                .collect(Collectors.toList());

        // Lấy thông tin đồ ăn
        List<OrderOnlineDTO.FoodInfoDTO> foods = orderDetailFoodRepository.findByOrderOnline_OrderOnlineId(o.getOrderOnlineId())
                .stream().map(f -> OrderOnlineDTO.FoodInfoDTO.builder()
                        .productName(f.getProduct() != null ? f.getProduct().getName() : "N/A")
                        .quantity(f.getQuantity())
                        .price(f.getPrice())
                        .build())
                .collect(Collectors.toList());

        // Tên rạp (Lấy từ vé đầu tiên hoặc từ nhân viên)
        String cinemaName = "N/A";
        if (s != null && s.getCinema() != null) {
            cinemaName = s.getCinema().getName();
        } else if (!tickets.isEmpty()) {
            // Thử lấy rạp từ Ticket đầu tiên (phải query lại entity để có cinema name)
            var firstTicket = ticketRepository.findByOrderOnline_OrderOnlineId(o.getOrderOnlineId()).get(0);
            if (firstTicket.getShowtime() != null && firstTicket.getShowtime().getRoom() != null
                    && firstTicket.getShowtime().getRoom().getCinema() != null) {
                cinemaName = firstTicket.getShowtime().getRoom().getCinema().getName();
            }
        }

        return OrderOnlineDTO.builder()
                .id(o.getOrderOnlineId())
                .orderCode(o.getOrderCode())
                .createdAt(o.getCreatedAt())
                .originalAmount(o.getOriginalAmount())
                .discountAmount(o.getDiscountAmount())
                .finalAmount(o.getFinalAmount())
                .status(o.getStatus())
                .userId(u != null ? u.getUserId() : null)
                .customerName(name)
                .customerEmail(email)
                .cinemaName(cinemaName)
                .staffName(s != null ? s.getFullname() : "Đặt Online")
                .tickets(tickets)
                .foods(foods)
                .build();
    }
}
