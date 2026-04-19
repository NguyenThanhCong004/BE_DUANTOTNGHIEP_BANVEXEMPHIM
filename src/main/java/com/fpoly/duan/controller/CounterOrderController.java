package com.fpoly.duan.controller;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.CounterCheckoutRequest;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.service.CounterCheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/counter-orders")
@RequiredArgsConstructor
@Tag(name = "12. Đặt vé tại quầy (POS)", description = "Dành cho nhân viên bán vé tại quầy (STAFF/ADMIN).")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
@CrossOrigin(origins = "*", maxAge = 3600)
public class CounterOrderController {

    private final CounterCheckoutService counterCheckoutService;

    @PostMapping("/checkout")
    @Operation(summary = "Thanh toán hóa đơn tại quầy", description = "Chỉ nhân viên được thực hiện. Tạo đơn PAID ngay lập tức.")
    public ResponseEntity<ApiResponse<Object>> checkout(
            Authentication authentication,
            @Valid @RequestBody CounterCheckoutRequest request) {

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // Chỉ nhân viên hoặc Admin được thực hiện
        if (details.getStaff() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ApiResponse.builder()
                            .status(HttpStatus.FORBIDDEN.value())
                            .message("Chỉ nhân viên mới được bán hàng tại quầy")
                            .build());
        }

        Integer staffId = details.getStaff().getStaffId();
        Object data = counterCheckoutService.checkout(staffId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.builder()
                        .status(HttpStatus.CREATED.value())
                        .message("Thanh toán thành công")
                        .data(data)
                        .build());
    }

    @PostMapping("/{orderCode}/confirm-paid")
    @Operation(summary = "Xác nhận đơn hàng đã thanh toán (Dành cho chuyển khoản tại quầy)")
    public ResponseEntity<ApiResponse<com.fpoly.duan.entity.OrderOnline>> confirmPaid(@PathVariable String orderCode) {
        com.fpoly.duan.entity.OrderOnline order = counterCheckoutService.confirmPaid(orderCode);
        return ResponseEntity.ok(ApiResponse.<com.fpoly.duan.entity.OrderOnline>builder()
                .status(200)
                .message("Xác nhận thanh toán thành công")
                .data(order)
                .build());
    }

    @PostMapping("/{orderCode}/cancel")
    @Operation(summary = "Hủy đơn hàng đang chờ thanh toán (Giải phóng ghế)")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(@PathVariable String orderCode) {
        counterCheckoutService.cancelOrder(orderCode);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(200)
                .message("Đã hủy đơn hàng và giải phóng ghế")
                .build());
    }
}
