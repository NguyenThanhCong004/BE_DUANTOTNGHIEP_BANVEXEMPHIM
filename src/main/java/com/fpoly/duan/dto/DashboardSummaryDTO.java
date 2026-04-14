package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDTO {
    private Double totalRevenue;
    private Long totalTicketsSold;
    private Long totalUsers;
    private Long totalCinemas;
    private Long totalStaff;
    private Long totalMovies;
    private Double revenueGrowth; 
}
