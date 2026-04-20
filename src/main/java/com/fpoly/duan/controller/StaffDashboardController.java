package com.fpoly.duan.controller;

import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.OrderDetailDTO;
import com.fpoly.duan.dto.ProductSoldBreakdown;
import com.fpoly.duan.dto.RevenueBreakdownDTO;
import com.fpoly.duan.dto.StaffDashboardStats;
import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.service.StaffDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff/dashboard-stats")
@RequiredArgsConstructor
@Tag(name = "13. Dashboard Nhân viên", description = "Thống kê hiệu suất làm việc của nhân viên theo ca.")
@CrossOrigin(origins = "*", maxAge = 3600)
public class StaffDashboardController {

    private final StaffDashboardService staffDashboardService;

    @GetMapping
    @Operation(summary = "Lấy thống kê dashboard cho nhân viên trong ca hiện tại")
    public ResponseEntity<ApiResponse<StaffDashboardStats>> getStats(
            Authentication authentication,
            @RequestParam(required = false) String date) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return ResponseEntity.status(401).body(ApiResponse.<StaffDashboardStats>builder()
                    .status(401)
                    .message("Chưa đăng nhập hoặc phiên làm việc hết hạn")
                    .build());
        }
        Integer staffId = details.getStaff().getStaffId();
        
        StaffDashboardStats stats = staffDashboardService.getDashboardStats(staffId);
        
        return ResponseEntity.ok(ApiResponse.<StaffDashboardStats>builder()
                .status(200)
                .message("Lấy thống kê thành công")
                .data(stats)
                .build());
    }

    @GetMapping("/products-breakdown")
    @Operation(summary = "Lấy chi tiết danh sách bắp nước đã bán")
    public ResponseEntity<ApiResponse<List<ProductSoldBreakdown>>> getProductsBreakdown(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return ResponseEntity.status(401).body(ApiResponse.<List<ProductSoldBreakdown>>builder().status(401).message("Unauthorized").build());
        }
        Integer staffId = details.getStaff().getStaffId();
        
        List<ProductSoldBreakdown> list = staffDashboardService.getProductsBreakdown(staffId);
        
        return ResponseEntity.ok(ApiResponse.<List<ProductSoldBreakdown>>builder()
                .status(200)
                .message("Lấy thống kê sản phẩm thành công")
                .data(list)
                .build());
    }

    @GetMapping("/revenue-breakdown")
    @Operation(summary = "Lấy chi tiết doanh thu theo phương thức thanh toán")
    public ResponseEntity<ApiResponse<List<RevenueBreakdownDTO>>> getRevenueBreakdown(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return ResponseEntity.status(401).body(ApiResponse.<List<RevenueBreakdownDTO>>builder().status(401).message("Unauthorized").build());
        }
        Integer staffId = details.getStaff().getStaffId();
        
        List<RevenueBreakdownDTO> list = staffDashboardService.getRevenueBreakdown(staffId);
        
        return ResponseEntity.ok(ApiResponse.<List<RevenueBreakdownDTO>>builder()
                .status(200)
                .message("Lấy thống kê doanh thu thành công")
                .data(list)
                .build());
    }

    @GetMapping("/recent-orders")
    @Operation(summary = "Lấy danh sách 10 hóa đơn gần nhất của nhân viên")
    public ResponseEntity<ApiResponse<List<OrderOnline>>> getRecentOrders(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return ResponseEntity.status(401).body(ApiResponse.<List<OrderOnline>>builder().status(401).message("Unauthorized").build());
        }
        Integer staffId = details.getStaff().getStaffId();
        
        List<OrderOnline> orders = staffDashboardService.getRecentOrders(staffId);
        
        return ResponseEntity.ok(ApiResponse.<List<OrderOnline>>builder()
                .status(200)
                .message("Lấy danh sách hóa đơn thành công")
                .data(orders)
                .build());
    }

    @GetMapping("/orders/{orderCode}")
    @Operation(summary = "Lấy chi tiết một hóa đơn theo mã")
    public ResponseEntity<ApiResponse<OrderDetailDTO>> getOrderDetail(@PathVariable String orderCode) {
        OrderDetailDTO detail = staffDashboardService.getOrderDetail(orderCode);
        
        return ResponseEntity.ok(ApiResponse.<OrderDetailDTO>builder()
                .status(200)
                .message("Lấy chi tiết hóa đơn thành công")
                .data(detail)
                .build());
    }
}
