package com.fpoly.duan.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.CancelPendingOrderRequest;
import com.fpoly.duan.dto.FoodOnlyCheckoutRequest;
import com.fpoly.duan.dto.TicketCheckoutResponse;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.service.TicketCheckoutService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/food-orders")
@RequiredArgsConstructor
@Tag(name = "11b. Đặt bắp nước riêng", description = "Đơn chỉ gồm sản phẩm rạp + PayOS (JWT khách — không phải nhân viên).")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class FoodOrderController {

    private final TicketCheckoutService ticketCheckoutService;

    @PostMapping("/checkout")
    @Operation(summary = "Checkout bắp nước (không vé)", description = """
            JWT khách hàng. Sản phẩm phải đang bật bán tại rạp (`cinema_products`).
            Trả `payos.checkoutUrl` để FE redirect.
            """)
    public ResponseEntity<ApiResponse<TicketCheckoutResponse>> checkout(
            Authentication authentication,
            @Valid @RequestBody FoodOnlyCheckoutRequest request) {

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (details.getStaff() != null || details.getUser() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ApiResponse.<TicketCheckoutResponse>builder()
                            .status(HttpStatus.FORBIDDEN.value())
                            .message("Chỉ tài khoản khách hàng được đặt hàng online")
                            .build());
        }

        Integer userId = details.getUser().getUserId();
        TicketCheckoutResponse data = ticketCheckoutService.checkoutFoodOnly(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<TicketCheckoutResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message("Tạo đơn bắp nước và link thanh toán thành công")
                        .data(data)
                        .build());
    }

    @PostMapping("/cancel-pending")
    @Operation(summary = "Hủy đơn bắp nước chờ PayOS", description = "Xóa đơn chờ (cùng bảng đơn online với vé).")
    public ResponseEntity<ApiResponse<Void>> cancelPending(
            Authentication authentication,
            @Valid @RequestBody CancelPendingOrderRequest request) {

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (details.getStaff() != null || details.getUser() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ApiResponse.<Void>builder()
                            .status(HttpStatus.FORBIDDEN.value())
                            .message("Chỉ tài khoản khách hàng được thao tác")
                            .build());
        }
        ticketCheckoutService.cancelPendingOrderByPayosCode(details.getUser().getUserId(), request.getPayosOrderCode());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Đã hủy đơn chờ")
                .build());
    }
}
