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
public class PromotionGroupResponse {
    private Integer id;
    private String title;
    private Double discount_percent;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Integer cinemaId;
    private List<Integer> selectedMovieIds;
    private List<String> selectedMovieTitles;
}

