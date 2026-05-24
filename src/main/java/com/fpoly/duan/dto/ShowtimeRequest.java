package com.fpoly.duan.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowtimeRequest {

    @NotNull(message = "Mã phim không được để trống")
    private Integer movieId;

    @NotNull(message = "Mã phòng chiếu không được để trống")
    private Integer roomId;

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    private LocalDateTime startTime;

    @Min(value = 0, message = "Phụ phí không được âm")
    private Double surcharge;
}

