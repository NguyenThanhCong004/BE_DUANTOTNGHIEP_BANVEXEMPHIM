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
import com.fpoly.duan.service.EphemeralSeatHoldService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/showtime-seat-holds")
@RequiredArgsConstructor
@Tag(name = "6b. Giữ ghế tạm (xem chéo)", description = "Không cần JWT — FE gửi holderId (UUID) + seatIds; tab khác poll peer.")
public class ShowtimeSeatHoldController {

    private final EphemeralSeatHoldService ephemeralSeatHoldService;

    @PostMapping("/refresh")
    @Operation(summary = "Gia hạn ghế đang chọn (~45s)")
    public ResponseEntity<ApiResponse<Void>> refresh(@Valid @RequestBody SeatHoldRefreshRequest body) {
        ephemeralSeatHoldService.refresh(body.showtimeId(), body.holderId(), body.seatIds());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
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
}
