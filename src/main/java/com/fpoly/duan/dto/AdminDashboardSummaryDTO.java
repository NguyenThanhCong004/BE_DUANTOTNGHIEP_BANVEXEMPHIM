package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardSummaryDTO {
    private Double revenueToday;
    private Long ticketsToday;
    private Long customersToday;
    private Long moviesShowingCount;
    private Long showtimesToday;
    private Long seatsSoldToday;
}
