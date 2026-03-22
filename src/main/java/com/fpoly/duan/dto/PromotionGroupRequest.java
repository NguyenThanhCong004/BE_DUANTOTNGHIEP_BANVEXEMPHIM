package com.fpoly.duan.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionGroupRequest {
    private String title;
    private Double discount_percent;
    private LocalDate start_date;
    private LocalDate end_date;
    private List<Integer> selectedMovieIds;
    private String description; // FE có field, BE hiện chưa lưu; backend sẽ bỏ qua
    private Integer cinemaId;
}

