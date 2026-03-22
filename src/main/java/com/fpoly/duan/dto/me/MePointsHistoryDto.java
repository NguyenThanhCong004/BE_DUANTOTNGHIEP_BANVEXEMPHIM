package com.fpoly.duan.dto.me;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MePointsHistoryDto {
    private Integer pointHistoryId;
    private LocalDate date;
    private String description;
    private Integer points;
}
