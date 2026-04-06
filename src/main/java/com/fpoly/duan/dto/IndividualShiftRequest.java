package com.fpoly.duan.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndividualShiftRequest {
    private Integer staffId;
    private LocalDate date;
    private String shiftType;
    private String startTime;
    private String endTime;
    private String role;
    private Integer cinemaId;
}
