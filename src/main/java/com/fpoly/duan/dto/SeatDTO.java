package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatDTO {
    private Integer seatId;
    private Integer x;
    private Integer y;
    private String row;
    private String number;
    private String seatTypeName;
    /** Phụ thu loại ghế (cộng vào giá vé suất chiếu) */
    private Double seatTypeSurcharge;
}

