package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatOccupancyDTO {
    private Long soldSeats;
    private Long totalSeats;
    /** 0..100 */
    private Double ratio;
}
