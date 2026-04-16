package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatTypeDTO {
    private Integer seatTypeId;
    private String name;
    private Double surcharge;
    /** {@code true} nếu đây là loại ghế đôi (chiếm 2 ô trên sơ đồ). */
    private Boolean coupleSeat;
    /** Màu nền ghế trên sơ đồ (#RRGGBB). */
    private String color;
}

