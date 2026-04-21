package com.fpoly.duan.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.me.FavoriteMovieIdRequest;
import com.fpoly.duan.dto.me.MeFavoriteMovieDto;
import com.fpoly.duan.dto.me.MePointsHistoryDto;
import com.fpoly.duan.dto.me.MeTransactionDto;
import com.fpoly.duan.dto.me.MeUserVoucherRowDto;
import com.fpoly.duan.dto.me.VoucherRedeemRequest;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.service.CustomerMeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "12. Tài khoản khách (Me)", description = "JWT khách hàng — lịch sử giao dịch, yêu thích, voucher, điểm.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class CustomerMeController {

    private final CustomerMeService customerMeService;
    private final com.fpoly.duan.service.TicketCheckoutService ticketCheckoutService;

    private Integer requireCustomerUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Chưa đăng nhập");
        }
        if (details.getStaff() != null || details.getUser() == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chỉ tài khoản khách hàng được dùng API này");
        }
        Integer userId = details.getUser().getUserId();
        // Reload hạng rank mỗi khi truy cập web
        try {
            java.lang.reflect.Method method = ticketCheckoutService.getClass().getDeclaredMethod("recalculateUserRankFromPaidOrders", com.fpoly.duan.entity.User.class);
            method.setAccessible(true);
            method.invoke(ticketCheckoutService, details.getUser());
        } catch (Exception e) {
            System.err.println("Error reloading rank: " + e.getMessage());
        }
        return userId;
    }

    @GetMapping("/transactions")
    @Operation(summary = "Lịch sử giao dịch (đơn + điểm)")
    public ResponseEntity<ApiResponse<List<MeTransactionDto>>> transactions(Authentication authentication) {
        Integer uid = requireCustomerUserId(authentication);
        List<MeTransactionDto> data = customerMeService.listTransactions(uid);
        return ResponseEntity.ok(ApiResponse.<List<MeTransactionDto>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(data)
                .build());
    }

    @GetMapping("/favorites")
    @Operation(summary = "Phim yêu thích")
    public ResponseEntity<ApiResponse<List<MeFavoriteMovieDto>>> favorites(Authentication authentication) {
        Integer uid = requireCustomerUserId(authentication);
        return ResponseEntity.ok(ApiResponse.<List<MeFavoriteMovieDto>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(customerMeService.listFavorites(uid))
                .build());
    }

    @PostMapping("/favorites")
    @Operation(summary = "Thêm phim yêu thích")
    public ResponseEntity<ApiResponse<Void>> addFavorite(Authentication authentication,
            @Valid @RequestBody FavoriteMovieIdRequest body) {
        Integer uid = requireCustomerUserId(authentication);
        customerMeService.addFavorite(uid, body);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Đã thêm yêu thích")
                .build());
    }

    @DeleteMapping("/favorites/{movieId}")
    @Operation(summary = "Bỏ yêu thích theo movieId")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(Authentication authentication,
            @PathVariable Integer movieId) {
        Integer uid = requireCustomerUserId(authentication);
        customerMeService.removeFavorite(uid, movieId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Đã xóa yêu thích")
                .build());
    }

    @GetMapping("/vouchers")
    @Operation(summary = "Voucher trong ví")
    public ResponseEntity<ApiResponse<List<MeUserVoucherRowDto>>> myVouchers(Authentication authentication) {
        Integer uid = requireCustomerUserId(authentication);
        return ResponseEntity.ok(ApiResponse.<List<MeUserVoucherRowDto>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(customerMeService.listUserVouchers(uid))
                .build());
    }

    @PostMapping("/vouchers/redeem")
    @Operation(summary = "Đổi voucher bằng điểm")
    public ResponseEntity<ApiResponse<Void>> redeem(Authentication authentication,
            @Valid @RequestBody VoucherRedeemRequest body) {
        Integer uid = requireCustomerUserId(authentication);
        customerMeService.redeemVoucher(uid, body.voucherId());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Đổi voucher thành công")
                .build());
    }

    @GetMapping("/points-history")
    @Operation(summary = "Lịch sử điểm")
    public ResponseEntity<ApiResponse<List<MePointsHistoryDto>>> pointsHistory(Authentication authentication) {
        Integer uid = requireCustomerUserId(authentication);
        return ResponseEntity.ok(ApiResponse.<List<MePointsHistoryDto>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(customerMeService.listPointsHistory(uid))
                .build());
    }
}
