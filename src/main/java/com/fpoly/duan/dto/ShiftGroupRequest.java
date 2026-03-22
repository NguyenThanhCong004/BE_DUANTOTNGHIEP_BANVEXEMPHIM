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
public class ShiftGroupRequest {
    private LocalDate date;
    private String shiftType; // Ca 1 / Ca 2 / Ca 3
    private Integer staffBanveId;
    private Integer staffSoatVeId;
    private Integer staffPhucVuId;
}

