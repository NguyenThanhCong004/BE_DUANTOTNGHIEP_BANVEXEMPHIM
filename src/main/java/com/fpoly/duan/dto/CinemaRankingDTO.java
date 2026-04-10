package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CinemaRankingDTO {
    private String cinemaName;
    private Double revenue;
    private Long ticketCount;
}
