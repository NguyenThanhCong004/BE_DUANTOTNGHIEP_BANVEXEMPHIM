package com.fpoly.duan.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.AdminDashboardSummaryDTO;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.HourlyTicketsDTO;
import com.fpoly.duan.dto.NotificationDTO;
import com.fpoly.duan.dto.RevenueChartDTO;
import com.fpoly.duan.dto.SeatTypeRatioDTO;
import com.fpoly.duan.dto.TopMovieDTO;
import com.fpoly.duan.service.AdminDashboardService;
import com.fpoly.duan.service.CinemaScopeService;
import com.fpoly.duan.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Dashboard cho Admin — Admin luôn bị giới hạn về rạp mình quản lý (qua {@link CinemaScopeService}),
 * Super Admin có thể truyền {@code cinemaId} để xem trước dữ liệu của một rạp cụ thể.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Dashboard Admin", description = "Số liệu giới hạn theo rạp đang quản lý.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final NotificationService notificationService;
    private final CinemaScopeService cinemaScopeService;

    @GetMapping("/summary")
    @Operation(summary = "Tổng quan rạp hôm nay")
    public ResponseEntity<ApiResponse<AdminDashboardSummaryDTO>> getSummary(
            @RequestParam(required = false) Integer cinemaId) {
        Integer effectiveCinemaId = cinemaScopeService.effectiveCinemaId(cinemaId);
        return ResponseEntity.ok(ApiResponse.<AdminDashboardSummaryDTO>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(adminDashboardService.getSummary(effectiveCinemaId))
                .build());
    }

    @GetMapping("/revenue-by-day")
    @Operation(summary = "Doanh thu theo ngày (mặc định 14 ngày gần nhất)")
    public ResponseEntity<ApiResponse<List<RevenueChartDTO>>> getRevenueByDay(
            @RequestParam(required = false) Integer cinemaId,
            @RequestParam(required = false, defaultValue = "14") Integer days) {
        Integer effectiveCinemaId = cinemaScopeService.effectiveCinemaId(cinemaId);
        return ResponseEntity.ok(ApiResponse.<List<RevenueChartDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(adminDashboardService.getRevenueByDay(effectiveCinemaId, days))
                .build());
    }

    @GetMapping("/tickets-by-hour")
    @Operation(summary = "Vé bán theo giờ trong ngày (mặc định hôm nay)")
    public ResponseEntity<ApiResponse<List<HourlyTicketsDTO>>> getTicketsByHour(
            @RequestParam(required = false) Integer cinemaId,
            @RequestParam(required = false) LocalDate date) {
        Integer effectiveCinemaId = cinemaScopeService.effectiveCinemaId(cinemaId);
        return ResponseEntity.ok(ApiResponse.<List<HourlyTicketsDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(adminDashboardService.getTicketsByHour(effectiveCinemaId, date))
                .build());
    }

    @GetMapping("/top-movies")
    @Operation(summary = "Top phim bán chạy tại rạp")
    public ResponseEntity<ApiResponse<List<TopMovieDTO>>> getTopMovies(
            @RequestParam(required = false) Integer cinemaId,
            @RequestParam(required = false, defaultValue = "5") Integer limit) {
        Integer effectiveCinemaId = cinemaScopeService.effectiveCinemaId(cinemaId);
        return ResponseEntity.ok(ApiResponse.<List<TopMovieDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(adminDashboardService.getTopMovies(effectiveCinemaId, limit))
                .build());
    }

    @GetMapping("/seat-type-ratio")
    @Operation(summary = "Tỷ lệ ghế VIP/thường/đôi đã bán hôm nay")
    public ResponseEntity<ApiResponse<List<SeatTypeRatioDTO>>> getSeatTypeRatio(
            @RequestParam(required = false) Integer cinemaId) {
        Integer effectiveCinemaId = cinemaScopeService.effectiveCinemaId(cinemaId);
        return ResponseEntity.ok(ApiResponse.<List<SeatTypeRatioDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(adminDashboardService.getSeatTypeRatioToday(effectiveCinemaId))
                .build());
    }

    @GetMapping("/notifications")
    @Operation(summary = "Thông báo nội bộ cho rạp")
    public ResponseEntity<ApiResponse<List<NotificationDTO>>> getNotifications(
            @RequestParam(required = false) Integer cinemaId,
            @RequestParam(required = false) Integer limit) {
        Integer effectiveCinemaId = cinemaScopeService.effectiveCinemaId(cinemaId);
        return ResponseEntity.ok(ApiResponse.<List<NotificationDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(notificationService.listForCinema(effectiveCinemaId, limit))
                .build());
    }
}
