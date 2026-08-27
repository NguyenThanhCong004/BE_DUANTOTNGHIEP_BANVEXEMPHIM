package com.fpoly.duan.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.me.FavoriteMovieIdRequest;
import com.fpoly.duan.dto.me.MeFavoriteMovieDto;
import com.fpoly.duan.dto.me.MeMovieReviewDto;
import com.fpoly.duan.dto.me.MeMovieReviewStatusDto;
import com.fpoly.duan.dto.me.MePointsHistoryDto;
import com.fpoly.duan.dto.me.MeTransactionDto;
import com.fpoly.duan.dto.me.MeUserVoucherRowDto;
import com.fpoly.duan.dto.me.MovieReviewRequest;
import com.fpoly.duan.dto.me.PagedResponse;
import com.fpoly.duan.dto.me.VoucherRedeemRequest;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.service.CustomerMeService;
import com.fpoly.duan.util.SearchUtils;

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
            ticketCheckoutService.recalculateUserRankFromPaidOrders(details.getUser());
        } catch (Exception e) {
            System.err.println("Error reloading rank: " + e.getMessage());
        }
        return userId;
    }

    @GetMapping("/transactions")
    @Operation(summary = "Lịch sử giao dịch (đơn + điểm) — phân trang, page 0-based")
    public ResponseEntity<ApiResponse<PagedResponse<MeTransactionDto>>> transactions(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Integer uid = requireCustomerUserId(authentication);
        String term = SearchUtils.pick(search, keyword, q);
        PagedResponse<MeTransactionDto> data = customerMeService.listTransactions(uid, page, size);
        if (term != null && !term.isBlank()) {
            data.setContent(data.getContent().stream()
                    .filter(tx -> SearchUtils.matches(term,
                            tx.getId(), tx.getOrderCode(), tx.getType(), tx.getStatus(), tx.getFinalAmount(),
                            tx.getVoucherCode(), tx.getItems() != null ? tx.getItems().stream()
                                    .map(i -> String.join(" ", String.valueOf(i.getLabel()), String.valueOf(i.getSub())))
                                    .toList() : null))
                    .toList());
        }
        return ResponseEntity.ok(ApiResponse.<PagedResponse<MeTransactionDto>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(data)
                .build());
    }

    @GetMapping("/favorites")
    @Operation(summary = "Phim yêu thích — phân trang, page 0-based")
    public ResponseEntity<ApiResponse<PagedResponse<MeFavoriteMovieDto>>> favorites(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Integer uid = requireCustomerUserId(authentication);
        String term = SearchUtils.pick(search, keyword, q);
        PagedResponse<MeFavoriteMovieDto> data = customerMeService.listFavorites(uid, page, size);
        if (term != null && !term.isBlank()) {
            data.setContent(data.getContent().stream()
                    .filter(f -> SearchUtils.matches(term, f.getFavoriteId(), f.getMovieId(), f.getTitle(), f.getStatus()))
                    .toList());
        }
        return ResponseEntity.ok(ApiResponse.<PagedResponse<MeFavoriteMovieDto>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(data)
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

    @PutMapping("/movie-reviews/{movieId}")
    @Operation(summary = "Bình luận / đánh giá phim đã mua vé")
    public ResponseEntity<ApiResponse<MeMovieReviewDto>> saveMovieReview(Authentication authentication,
            @PathVariable Integer movieId,
            @Valid @RequestBody MovieReviewRequest body) {
        Integer uid = requireCustomerUserId(authentication);
        MeMovieReviewDto data = customerMeService.saveMovieReview(uid, movieId, body);
        return ResponseEntity.ok(ApiResponse.<MeMovieReviewDto>builder()
                .status(HttpStatus.OK.value())
                .message("Đã lưu đánh giá phim")
                .data(data)
                .build());
    }

    @GetMapping("/movie-reviews/{movieId}")
    @Operation(summary = "Trạng thái đánh giá phim của khách hiện tại")
    public ResponseEntity<ApiResponse<MeMovieReviewStatusDto>> getMovieReviewStatus(Authentication authentication,
            @PathVariable Integer movieId) {
        Integer uid = requireCustomerUserId(authentication);
        MeMovieReviewStatusDto data = customerMeService.getMovieReviewStatus(uid, movieId);
        return ResponseEntity.ok(ApiResponse.<MeMovieReviewStatusDto>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(data)
                .build());
    }

    @GetMapping("/vouchers")
    @Operation(summary = "Voucher trong ví — phân trang, page 0-based")
    public ResponseEntity<ApiResponse<PagedResponse<MeUserVoucherRowDto>>> myVouchers(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Integer uid = requireCustomerUserId(authentication);
        String term = SearchUtils.pick(search, keyword, q);
        PagedResponse<MeUserVoucherRowDto> data = customerMeService.listUserVouchers(uid, page, size);
        if (term != null && !term.isBlank()) {
            data.setContent(data.getContent().stream()
                    .filter(v -> SearchUtils.matches(term,
                            v.getUserVoucherId(), v.getStatus(),
                            v.getVoucher() != null ? v.getVoucher().getCode() : null,
                            v.getVoucher() != null ? v.getVoucher().getValue() : null,
                            v.getVoucher() != null ? v.getVoucher().getPointVoucher() : null))
                    .toList());
        }
        return ResponseEntity.ok(ApiResponse.<PagedResponse<MeUserVoucherRowDto>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(data)
                .build());
    }

    @PostMapping("/vouchers/redeem")
    @Operation(summary = "Đổi/nhận voucher — voucher 0 điểm sẽ không trừ điểm")
    public ResponseEntity<ApiResponse<Void>> redeem(Authentication authentication,
            @Valid @RequestBody VoucherRedeemRequest body) {
        Integer uid = requireCustomerUserId(authentication);
        customerMeService.redeemVoucher(uid, body.voucherId());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Nhận voucher thành công")
                .build());
    }

    @GetMapping("/points-history")
    @Operation(summary = "Lịch sử điểm — phân trang, page 0-based")
    public ResponseEntity<ApiResponse<PagedResponse<MePointsHistoryDto>>> pointsHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Integer uid = requireCustomerUserId(authentication);
        return ResponseEntity.ok(ApiResponse.<PagedResponse<MePointsHistoryDto>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(customerMeService.listPointsHistory(uid, page, size))
                .build());
    }
}
