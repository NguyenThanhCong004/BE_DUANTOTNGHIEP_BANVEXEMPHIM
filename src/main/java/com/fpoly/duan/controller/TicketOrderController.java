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
import com.fpoly.duan.dto.TicketCheckoutRequest;
import com.fpoly.duan.dto.TicketCheckoutResponse;
import com.fpoly.duan.dto.TicketQuoteResponse;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.service.TicketCheckoutService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/ticket-orders")
@RequiredArgsConstructor
@Tag(name = "11. Đặt vé & PayOS", description = "Tạo đơn vé online + link thanh toán PayOS (JWT khách — không phải nhân viên).")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class TicketOrderController {

    private final TicketCheckoutService ticketCheckoutService;

    @PostMapping("/quote")
    @Operation(summary = "Báo giá vé (không tạo đơn)", description = """
            Bắt buộc JWT **khách hàng**. Trả báo giá theo đúng công thức BE:
            khuyến mãi phim + giảm theo hạng + voucher (nếu có). Không tạo order/ticket.
            """)
    public ResponseEntity<ApiResponse<TicketQuoteResponse>> quote(
            Authentication authentication,
            @RequestBody TicketCheckoutRequest request) {

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (details.getStaff() != null || details.getUser() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ApiResponse.<TicketQuoteResponse>builder()
                            .status(HttpStatus.FORBIDDEN.value())
                            .message("Chỉ tài khoản khách hàng được thao tác")
                            .build());
        }

        Integer userId = details.getUser().getUserId();
        TicketQuoteResponse data = ticketCheckoutService.quote(userId, request);

        return ResponseEntity.ok(ApiResponse.<TicketQuoteResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Báo giá thành công")
                .data(data)
                .build());
    }

    @PostMapping("/checkout")
    @Operation(summary = "Checkout vé + tạo link PayOS", description = """
            Bắt buộc JWT **khách hàng** (không phải nhân viên). Tạo đơn chờ thanh toán, vé chờ;
            trả `payos.checkoutUrl` — FE redirect người dùng tới URL đó.
            Sau khi PayOS thanh toán, webhook cập nhật đơn/vé sang trạng thái đã thanh toán.
            """)
    public ResponseEntity<ApiResponse<TicketCheckoutResponse>> checkout(
            Authentication authentication,
            @Valid @RequestBody TicketCheckoutRequest request) {

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (details.getStaff() != null || details.getUser() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ApiResponse.<TicketCheckoutResponse>builder()
                            .status(HttpStatus.FORBIDDEN.value())
                            .message("Chỉ tài khoản khách hàng được đặt vé online")
                            .build());
        }

        Integer userId = details.getUser().getUserId();
        TicketCheckoutResponse data = ticketCheckoutService.checkout(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<TicketCheckoutResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message("Tạo đơn và link thanh toán thành công")
                        .data(data)
                        .build());
    }

    @org.springframework.web.bind.annotation.PostMapping("/cancel-pending")
    @Operation(summary = "Hủy đơn chờ PayOS", description = "Giữ đơn ở trạng thái hủy và trả ghế về kho bán.")
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
                .message("Đã hủy đơn, lưu lịch sử và trả ghế")
                .build());
    }
}
