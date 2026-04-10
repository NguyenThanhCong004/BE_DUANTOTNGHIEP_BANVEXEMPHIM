package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevenueChartDTO {
    private String label; // e.g., "Tháng 1", "Tháng 2"
    private Double totalAmount;
}
