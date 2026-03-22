package com.fpoly.duan.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftGroupResponse {
    private String shiftType;
    private LocalDate date;
    private Integer staffBanveId;
    private Integer staffSoatVeId;
    private Integer staffPhucVuId;
}

