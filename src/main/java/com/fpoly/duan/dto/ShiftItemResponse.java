package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftItemResponse {
    private Integer id; // staffShiftId
    private String staffName;
    private String role; // Bán vé / Soát vé / Phục vụ
    private String phone;
    private String date; // yyyy-MM-dd
    private String shiftType; // Ca 1 / Ca 2 / Ca 3
    private String startTime; // HH:mm
    private String endTime; // HH:mm
    private String status; // Đang làm / Sắp tới / Đã xong
    private String cinemaName;
}

