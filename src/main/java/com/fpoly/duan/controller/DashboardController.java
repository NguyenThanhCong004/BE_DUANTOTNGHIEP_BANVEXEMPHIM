package com.fpoly.duan.controller;

import com.fpoly.duan.dto.CinemaRankingDTO;
import com.fpoly.duan.dto.DashboardSummaryDTO;
import com.fpoly.duan.dto.RevenueChartDTO;
import com.fpoly.duan.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/super-admin/dashboard")
@RequiredArgsConstructor
@CrossOrigin("*")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<DashboardSummaryDTO> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    @GetMapping("/revenue-chart")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<RevenueChartDTO>> getRevenueChart(@RequestParam(required = false) Integer year) {
        if (year == null) {
            year = LocalDate.now().getYear();
        }
        return ResponseEntity.ok(dashboardService.getMonthlyRevenue(year));
    }

    @GetMapping("/cinema-ranking")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<CinemaRankingDTO>> getCinemaRanking() {
        return ResponseEntity.ok(dashboardService.getCinemaRankings());
    }
}
