package com.fpoly.duan.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StaffDashboardStats {
    private Double totalRevenue;
    private Long totalTicketsSold;
    private Long totalProductsSold;
    private String shiftName;
    private String cinemaName;
}
