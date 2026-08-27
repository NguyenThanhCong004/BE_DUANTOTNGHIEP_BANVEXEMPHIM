package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieStatDTO {
    private Integer movieId;
    private String title;
    private String poster;
    private Integer status;
    private Long ticketsSold;
    private Double revenue;
}
