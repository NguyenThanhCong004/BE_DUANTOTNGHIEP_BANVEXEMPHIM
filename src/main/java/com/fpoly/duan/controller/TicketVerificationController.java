package com.fpoly.duan.controller;

import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.TicketQrVerificationDTO;
import com.fpoly.duan.dto.TicketQrVerifyRequest;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.service.TicketVerificationService;
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
public class TicketVerificationController {
    private final TicketVerificationService ticketVerificationService;

    @PostMapping("/verify-ticket")
    @Operation(summary = "Xác thực QR vé", description = "Chỉ nhân viên/quản trị rạp được quét. QR được giải mã và đối chiếu lại với dữ liệu vé.")
    public ResponseEntity<ApiResponse<TicketQrVerificationDTO>> verify(
            Authentication authentication, @Valid @RequestBody TicketQrVerifyRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)
                || details.getStaff() == null) {
            return ResponseEntity.status(401).body(ApiResponse.<TicketQrVerificationDTO>builder()
                    .status(401).message("Chưa đăng nhập nhân viên").build());
        }
        TicketQrVerificationDTO data = ticketVerificationService.verify(details.getStaff(), request.getQrToken());
        return ResponseEntity.ok(ApiResponse.<TicketQrVerificationDTO>builder()
                .status(200).message("QR vé hợp lệ").data(data).build());
    }
}
