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
    private Double revenueToday;
    private Long totalTicketsSold;
    private Long totalUsers;
    private Long totalCinemas;
    private Long totalRooms;
    /** Chỉ tính nhân viên có role STAFF (không gồm Admin/Super Admin). */
    private Long totalStaff;
    private Long totalAdmins;
    private Long totalMovies;
    private Double revenueGrowth;
}
