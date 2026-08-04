package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatTypeRatioDTO {
    private String seatTypeName;
    private Boolean coupleSeat;
    private Long ticketsSold;
}
