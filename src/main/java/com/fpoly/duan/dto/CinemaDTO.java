package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CinemaDTO {
    private Integer cinemaId;
    private String name;
    private String address;
    private Integer status;
}
