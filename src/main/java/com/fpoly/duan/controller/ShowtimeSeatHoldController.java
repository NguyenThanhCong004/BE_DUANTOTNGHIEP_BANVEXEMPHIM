package com.fpoly.duan.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.SeatHoldRefreshRequest;
import com.fpoly.duan.dto.SeatHoldRefreshResponse;
import com.fpoly.duan.security.JwtService;
import com.fpoly.duan.service.EphemeralSeatHoldService;
import com.fpoly.duan.service.SeatHoldAbuseService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/showtime-seat-holds")
@RequiredArgsConstructor
@Tag(name = "6b. Giữ ghế tạm (xem chéo)", description = "Không cần JWT — FE gửi holderId (UUID) + seatIds; tab khác poll peer.")
public class ShowtimeSeatHoldController {

    private final EphemeralSeatHoldService ephemeralSeatHoldService;
    private final SeatHoldAbuseService seatHoldAbuseService;
    private final JwtService jwtService;

    @PostMapping("/refresh")
    @Operation(summary = "Gia hạn ghế đang chọn (~45s)")
    public ResponseEntity<ApiResponse<SeatHoldRefreshResponse>> refresh(
            @Valid @RequestBody SeatHoldRefreshRequest body, HttpServletRequest request) {
        Integer userId = resolveCustomerUserId(request);
        ephemeralSeatHoldService.refresh(body.showtimeId(), body.holderId(), body.seatIds(), userId);
        boolean warning = seatHoldAbuseService.shouldWarn(userId);
        return ResponseEntity.ok(ApiResponse.<SeatHoldRefreshResponse>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(new SeatHoldRefreshResponse(warning))
                .build());
    }

    @GetMapping("/peer")
    @Operation(summary = "Ghế đang được người khác chọn (trừ holderId của mình)")
    public ResponseEntity<ApiResponse<List<Integer>>> peer(
            @RequestParam int showtimeId,
            @RequestParam(required = false) String excludeHolder) {
        List<Integer> ids = ephemeralSeatHoldService.peerHeldSeatIds(showtimeId, excludeHolder);
        return ResponseEntity.ok(ApiResponse.<List<Integer>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(ids)
                .build());
    }

    /**
     * Nhận diện khách hàng đã đăng nhập một cách "best-effort" từ JWT (nếu FE có gửi kèm) — chỉ để
     * theo dõi chống phá (giữ ghế/đơn hàng bất thường), KHÔNG dùng để chặn truy cập endpoint này
     * (endpoint vẫn công khai như thiết kế ban đầu). Token thiếu/hết hạn/không hợp lệ → coi như ẩn danh,
     * không tính vào bộ đếm, không ảnh hưởng request. Chỉ tính tài khoản khách hàng, bỏ qua nhân viên.
     */
    private Integer resolveCustomerUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        try {
            String accountType = jwtService.extractAccountType(token);
            if (accountType != null && !"USER".equalsIgnoreCase(accountType)) {
                return null;
            }
            return jwtService.extractUserId(token);
        } catch (Exception e) {
            return null;
        }
    }
}
