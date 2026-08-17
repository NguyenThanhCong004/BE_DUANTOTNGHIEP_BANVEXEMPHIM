package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatLayoutItem {
    private Integer x;
    private Integer y;
    private String row;
    private String number;
    private String seatTypeName;

    /** available, locked, maintenance — null = giữ nguyên trạng thái cũ (ghế đã có) hoặc "available" (ghế mới). */
    private String status;
}

