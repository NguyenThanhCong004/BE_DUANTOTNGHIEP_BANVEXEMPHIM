package com.fpoly.duan.controller;

import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.FoodOrderVerificationDTO;
import com.fpoly.duan.dto.FoodOrderVerifyRequest;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.service.FoodOrderVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class FoodOrderVerificationController {
    private final FoodOrderVerificationService foodOrderVerificationService;

    @PostMapping("/verify-food-order")
    @Operation(summary = "Soát QR bắp nước", description = "Chỉ nhân viên/quản trị rạp được quét. Quét là xác nhận giao hàng luôn trong một bước; quét lại đơn đã giao sẽ báo lỗi.")
    public ResponseEntity<ApiResponse<FoodOrderVerificationDTO>> verify(
            Authentication authentication, @Valid @RequestBody FoodOrderVerifyRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)
                || details.getStaff() == null) {
            return ResponseEntity.status(401).body(ApiResponse.<FoodOrderVerificationDTO>builder()
                    .status(401).message("Chưa đăng nhập nhân viên").build());
        }
        FoodOrderVerificationDTO data = foodOrderVerificationService.verify(details.getStaff(), request.getReceiptToken());
        return ResponseEntity.ok(ApiResponse.<FoodOrderVerificationDTO>builder()
                .status(200).message("Đã xác nhận giao bắp nước").data(data).build());
    }
}
