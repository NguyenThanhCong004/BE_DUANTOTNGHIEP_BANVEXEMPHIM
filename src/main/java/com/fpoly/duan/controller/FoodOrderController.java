package com.fpoly.duan.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.CancelPendingOrderRequest;
import com.fpoly.duan.dto.ConfirmPayosOrderRequest;
import com.fpoly.duan.dto.FoodOnlyCheckoutRequest;
import com.fpoly.duan.dto.TicketCheckoutResponse;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.service.TicketCheckoutService;
import com.fpoly.duan.service.TicketQrService;

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
    private final TicketQrService ticketQrService;

    @GetMapping(value = "/payment-qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Ảnh QR thanh toán PayOS cho đơn bắp nước", description = "BE tự sinh ảnh QR PNG từ payload PayOS (payos.qrCode) để app hiển thị ngay trong app, không cần mở trình duyệt ngoài.")
    public ResponseEntity<byte[]> paymentQr(Authentication authentication, @RequestParam("data") String qrCode) {
        requireCustomer(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(ticketQrService.toPaymentQrPng(qrCode));
    }

    @GetMapping(value = "/receipt-qr/{receiptToken}", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Ảnh QR riêng của đơn bắp nước", description = "Khách tự xem/xuất trình QR này tại quầy để nhân viên quét xác nhận giao hàng. Công khai như QR vé — bảo mật bằng chính receiptToken khó đoán.")
    public ResponseEntity<byte[]> receiptQr(@PathVariable String receiptToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(ticketCheckoutService.getOrderReceiptQrPng(receiptToken));
    }

    @GetMapping("/payos/{payosOrderCode}/status")
    @Operation(summary = "Kiểm tra trạng thái đơn PayOS bắp nước (dùng để poll trong khi hiển thị QR trong app)")
    public ResponseEntity<ApiResponse<TicketCheckoutResponse>> payosStatus(
            Authentication authentication, @PathVariable Long payosOrderCode) {
        CustomUserDetails details = requireCustomer(authentication);
        TicketCheckoutResponse data = ticketCheckoutService.checkPayosStatus(details.getUser().getUserId(), payosOrderCode);
        return ResponseEntity.ok(ApiResponse.<TicketCheckoutResponse>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(data)
                .build());
    }

    private CustomUserDetails requireCustomer(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        if (details.getStaff() != null || details.getUser() == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chỉ tài khoản khách hàng được thao tác");
        }
        return details;
    }

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

    @PostMapping("/confirm-payos")
    @Operation(summary = "Xác nhận thanh toán PayOS cho đơn bắp nước", description = """
            Dùng khi PayOS redirect về FE nhưng webhook không gọi được BE local/LAN.
            BE sẽ truy vấn PayOS, chỉ chốt đơn và cộng điểm khi PayOS trả trạng thái PAID.
            """)
    public ResponseEntity<ApiResponse<TicketCheckoutResponse>> confirmPayos(
            Authentication authentication,
            @Valid @RequestBody ConfirmPayosOrderRequest request) {

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (details.getStaff() != null || details.getUser() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ApiResponse.<TicketCheckoutResponse>builder()
                            .status(HttpStatus.FORBIDDEN.value())
                            .message("Chỉ tài khoản khách hàng được thao tác")
                            .build());
        }

        TicketCheckoutResponse data = ticketCheckoutService.confirmPaidOrderByPayosCode(
                details.getUser().getUserId(),
                request.getPayosOrderCode());

        return ResponseEntity.ok(ApiResponse.<TicketCheckoutResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Đã xác nhận thanh toán PayOS")
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
        boolean paid = ticketCheckoutService.cancelPendingOrderByPayosCode(
                details.getUser().getUserId(),
                request.getPayosOrderCode());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message(paid
                        ? "Đơn đã thanh toán trên PayOS, hệ thống đã cập nhật trạng thái"
                        : "Đã hủy đơn chờ")
                .build());
    }
}
