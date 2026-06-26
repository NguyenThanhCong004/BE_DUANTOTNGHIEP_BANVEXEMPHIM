package com.fpoly.duan.controller;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.CounterCheckoutRequest;
import com.fpoly.duan.entity.StaffShift;
import com.fpoly.duan.repository.StaffShiftRepository;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.service.CounterCheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/counter-orders")
@RequiredArgsConstructor
@Tag(name = "12. Đặt vé tại quầy (POS)", description = "Chỉ dành cho nhân viên đang trong ca Bán vé.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
@CrossOrigin(origins = "*", maxAge = 3600)
public class CounterOrderController {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CounterCheckoutService counterCheckoutService;
    private final StaffShiftRepository staffShiftRepository;

    @PostMapping("/checkout")
    @Operation(summary = "Thanh toán hóa đơn tại quầy", description = "Chỉ nhân viên được thực hiện. Tạo đơn PAID ngay lập tức.")
    public ResponseEntity<ApiResponse<Object>> checkout(
            Authentication authentication,
            @Valid @RequestBody CounterCheckoutRequest request) {

        Integer staffId = requireStaffId(authentication);
        Object data = counterCheckoutService.checkout(staffId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.builder()
                        .status(HttpStatus.CREATED.value())
                        .message("Thanh toán thành công")
                        .data(data)
                        .build());
    }

    @GetMapping(value = "/receipt-barcode/{receiptToken}", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Ảnh mã vạch hóa đơn", description = "Mã Code 128 được sinh từ HMAC đã lưu trong hóa đơn; dùng để in bill.")
    public ResponseEntity<byte[]> receiptBarcode(@PathVariable String receiptToken) {
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(counterCheckoutService.getReceiptBarcode(receiptToken));
    }

    @GetMapping(value = "/payment-qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Ảnh QR thanh toán tại quầy", description = "BE tự sinh ảnh QR PNG từ payload PayOS để FE không gọi dịch vụ QR bên ngoài.")
    public ResponseEntity<byte[]> paymentQr(
            Authentication authentication,
            @RequestParam("data") String qrCode) {
        requireStaffId(authentication);
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(counterCheckoutService.getPaymentQr(qrCode));
    }

    @GetMapping("/{orderCode}/status")
    @Operation(summary = "Kiểm tra trạng thái đơn hàng (Dành cho chuyển khoản tại quầy)")
    public ResponseEntity<ApiResponse<com.fpoly.duan.entity.OrderOnline>> checkStatus(
            Authentication authentication,
            @PathVariable String orderCode) {
        Integer staffId = requireStaffId(authentication);
        com.fpoly.duan.entity.OrderOnline order = counterCheckoutService.checkStatus(staffId, orderCode);
        return ResponseEntity.ok(ApiResponse.<com.fpoly.duan.entity.OrderOnline>builder()
                .status(200)
                .message("Lấy trạng thái đơn hàng thành công")
                .data(order)
                .build());
    }

    @PostMapping("/{orderCode}/confirm-paid")
    @Operation(summary = "Xác nhận đơn hàng đã thanh toán (Dành cho chuyển khoản tại quầy)")
    public ResponseEntity<ApiResponse<com.fpoly.duan.entity.OrderOnline>> confirmPaid(
            Authentication authentication,
            @PathVariable String orderCode) {
        Integer staffId = requireStaffId(authentication);
        com.fpoly.duan.entity.OrderOnline order = counterCheckoutService.confirmPaid(staffId, orderCode);
        return ResponseEntity.ok(ApiResponse.<com.fpoly.duan.entity.OrderOnline>builder()
                .status(200)
                .message("Xác nhận thanh toán thành công")
                .data(order)
                .build());
    }

    @PostMapping("/{orderCode}/cancel")
    @Operation(summary = "Hủy đơn hàng đang chờ thanh toán (Giải phóng ghế)")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
            Authentication authentication,
            @PathVariable String orderCode) {
        Integer staffId = requireStaffId(authentication);
        counterCheckoutService.cancelOrder(staffId, orderCode);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(200)
                .message("Đã hủy đơn hàng và giải phóng ghế")
                .build());
    }

    private Integer requireStaffId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        if (details.getStaff() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ nhân viên mới được bán hàng tại quầy");
        }
        Integer staffId = details.getStaff().getStaffId();
        StaffShift activeShift = staffShiftRepository.findActiveShift(staffId, LocalDateTime.now(APP_ZONE))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Bạn chưa trong ca làm việc, không thể bán hàng tại quầy"));
        if (!isSalesShift(activeShift.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chỉ nhân viên đang trong ca Bán vé mới được bán hàng tại quầy");
        }
        return staffId;
    }

    private boolean isSalesShift(String role) {
        String normalized = normalize(role);
        return "ban ve".equals(normalized)
                || "ban hang".equals(normalized)
                || "sales".equals(normalized)
                || "cashier".equals(normalized);
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
