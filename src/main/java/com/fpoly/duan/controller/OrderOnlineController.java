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
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.OrderOnlineDTO;
import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.repository.OrderOnlineRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
@RestController
@RequestMapping("/api/v1/orders-online")
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Table: order_online", description = "Danh sách và quản lý đơn đặt online (bảng order_online).")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class OrderOnlineController {

    private final OrderOnlineRepository orderOnlineRepository;

    public OrderOnlineController(OrderOnlineRepository orderOnlineRepository) {
        this.orderOnlineRepository = orderOnlineRepository;
    }

    @GetMapping
    @Operation(summary = "Danh sách đơn online")
    public ResponseEntity<ApiResponse<List<OrderOnlineDTO>>> list() {
        List<OrderOnlineDTO> data = orderOnlineRepository.findAll().stream()
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
        String name = u != null ? (u.getFullname() != null && !u.getFullname().isBlank()
                ? u.getFullname()
                : u.getUsername()) : "—";
        String email = u != null && u.getEmail() != null ? u.getEmail() : "—";
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
                .build();
    }
}
