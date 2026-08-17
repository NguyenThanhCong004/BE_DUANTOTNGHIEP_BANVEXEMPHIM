package com.fpoly.duan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kết quả gia hạn giữ ghế")
public record SeatHoldRefreshResponse(
        @Schema(description = "True nếu tài khoản đang giữ/huỷ ghế bất thường nhiều lần — FE hiện cảnh báo") boolean warning) {
}
