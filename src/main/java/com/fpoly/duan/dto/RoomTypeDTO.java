package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomTypeDTO {
    private Integer roomTypeId;
    private String name;
    private Integer standardSeatCount;
    private Integer vipSeatCount;
    private Integer coupleSeatCount;
}
