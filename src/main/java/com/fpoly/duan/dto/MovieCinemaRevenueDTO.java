package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieCinemaRevenueDTO {
    private Integer cinemaId;
    private String cinemaName;
    private Long ticketsSold;
    private Double revenue;
}
