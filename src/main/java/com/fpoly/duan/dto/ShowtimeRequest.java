package com.fpoly.duan.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowtimeRequest {
    private Integer movieId;
    private Integer roomId;
    private LocalDateTime startTime;
    private Double surcharge;
}

