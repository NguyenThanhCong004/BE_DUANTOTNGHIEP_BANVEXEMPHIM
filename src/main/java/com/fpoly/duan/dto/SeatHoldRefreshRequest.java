package com.fpoly.duan.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Record: accessor {@code showtimeId()} (IDE/Java luôn thấy), Jackson + {@code @Valid} hỗ trợ đầy đủ.
 */
@Schema(description = "Gia hạn ghế đang chọn (tab khác thấy qua GET peer-holds)")
public record SeatHoldRefreshRequest(
        @NotNull @Positive @Schema(description = "ID suất chiếu") Integer showtimeId,

        @NotBlank @Size(max = 80) @Schema(description = "UUID phiên trình duyệt (FE lưu sessionStorage)", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890") String holderId,

        @NotNull @Size(max = 24) @Schema(description = "Danh sách seat_id đang chọn") List<Integer> seatIds) {
}
