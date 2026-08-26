package com.fpoly.duan.controller;

import com.fpoly.duan.dto.AuditLogDTO;
import com.fpoly.duan.dto.CinemaCustomerStatDTO;
import com.fpoly.duan.dto.CinemaRankingDTO;
import com.fpoly.duan.dto.CinemaDetailDTO;
import com.fpoly.duan.dto.CustomerStatDTO;
import com.fpoly.duan.dto.DashboardSummaryDTO;
import com.fpoly.duan.dto.InvoiceStatDTO;
import com.fpoly.duan.dto.ProductStatDTO;
import com.fpoly.duan.dto.StaffStatDTO;
import com.fpoly.duan.dto.CinemaStatDTO;
import com.fpoly.duan.dto.MovieCinemaRevenueDTO;
import com.fpoly.duan.dto.MovieStatDTO;
import com.fpoly.duan.dto.RevenueBreakdownDTO;
import com.fpoly.duan.dto.RevenueChartDTO;
import com.fpoly.duan.dto.SeatOccupancyDTO;
import com.fpoly.duan.dto.TopMovieDTO;
import com.fpoly.duan.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/super-admin/dashboard")
@RequiredArgsConstructor
@CrossOrigin("*")
public class DashboardController {

    private final DashboardService dashboardService;

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryDTO> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/revenue-chart")
    public ResponseEntity<List<RevenueChartDTO>> getRevenueChart(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false, defaultValue = "month") String granularity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)LocalDate to) {
        if (from != null && to != null) {
            return ResponseEntity.ok(dashboardService.getDailyRevenueBetween(from, to));
        }
        if ("day".equalsIgnoreCase(granularity)) {
            return ResponseEntity.ok(dashboardService.getDailyRevenue());
        }
        if ("year".equalsIgnoreCase(granularity)) {
            return ResponseEntity.ok(dashboardService.getYearlyRevenue());
        }
        if (year == null) {
            year = LocalDate.now().getYear();
        }
        return ResponseEntity.ok(dashboardService.getMonthlyRevenue(year));
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/cinema-ranking")
    public ResponseEntity<List<CinemaRankingDTO>> getCinemaRanking(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        int targetYear = (year != null) ? year : java.time.LocalDate.now().getYear();
        int targetMonth = (month != null) ? month : java.time.LocalDate.now().getMonthValue();
        return ResponseEntity.ok(dashboardService.getCinemaRankings(targetYear, targetMonth));
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/cinema-detail/{id}")
    public ResponseEntity<CinemaDetailDTO> getCinemaDetail(@PathVariable Integer id) {
        return ResponseEntity.ok(dashboardService.getCinemaDetail(id));
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/top-movies")
    public ResponseEntity<List<TopMovieDTO>> getTopMovies(@RequestParam(required = false, defaultValue = "10") Integer limit) {
        return ResponseEntity.ok(dashboardService.getTopMovies(limit));
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/seat-occupancy")
    public ResponseEntity<SeatOccupancyDTO> getSeatOccupancy() {
        return ResponseEntity.ok(dashboardService.getSeatOccupancyToday());
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/payment-method-revenue")
    public ResponseEntity<List<RevenueBreakdownDTO>> getPaymentMethodRevenue() {
        return ResponseEntity.ok(dashboardService.getPaymentMethodRevenue());
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/movie-stats")
    public ResponseEntity<List<MovieStatDTO>> getMovieStats() {
        return ResponseEntity.ok(dashboardService.getMovieStats());
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/movie-cinema-revenue")
    public ResponseEntity<List<MovieCinemaRevenueDTO>> getMovieCinemaRevenue(
            @RequestParam Integer movieId) {
        return ResponseEntity.ok(dashboardService.getMovieCinemaRevenue(movieId));
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/cinema-movie-revenue")
    public ResponseEntity<List<TopMovieDTO>> getCinemaMovieRevenue(
            @RequestParam Integer cinemaId) {
        return ResponseEntity.ok(dashboardService.getCinemaMovieRevenue(cinemaId));
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/cinema-stats")
    public ResponseEntity<List<CinemaStatDTO>> getCinemaStats() {
        return ResponseEntity.ok(dashboardService.getCinemaStats());
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/customer-stats")
    public ResponseEntity<List<CustomerStatDTO>> getCustomerStats(
            @RequestParam(required = false) Integer cinemaId) {
        return ResponseEntity.ok(dashboardService.getCustomerStats(cinemaId));
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/staff-stats")
    public ResponseEntity<List<StaffStatDTO>> getStaffStats() {
        return ResponseEntity.ok(dashboardService.getStaffStats());
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/customer-stats/by-cinema")
    public ResponseEntity<List<CinemaCustomerStatDTO>> getCustomerStatsByCinema() {
        return ResponseEntity.ok(dashboardService.getCustomerStatsByCinema());
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/product-stats")
    public ResponseEntity<List<ProductStatDTO>> getProductStats() {
        return ResponseEntity.ok(dashboardService.getProductStats());
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/product-stats/category-revenue")
    public ResponseEntity<Map<String, Double>> getCategoryRevenue(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false, defaultValue = "0") Integer month) {
        int targetYear = (year != null && year > 0) ? year : java.time.LocalDate.now().getYear();
        int targetMonth = (month != null) ? month : 0;
        return ResponseEntity.ok(dashboardService.getCategoryRevenueByMonth(targetYear, targetMonth));
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/invoice-stats")
    public ResponseEntity<List<InvoiceStatDTO>> getInvoiceStats() {
        return ResponseEntity.ok(dashboardService.getInvoiceStats());
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/recent-admin-activity")
    public ResponseEntity<List<AuditLogDTO>> getRecentAdminActivity(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return ResponseEntity.ok(dashboardService.getRecentAdminActivity(limit));
    }

    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @GetMapping("/audit-log")
    public ResponseEntity<List<AuditLogDTO>> getAuditLog(
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return ResponseEntity.ok(dashboardService.getAuditLog(limit));
    }
}
