package com.fpoly.duan.dto.payos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tạo link thanh toán PayOS (ký HMAC phía BE)")
public class PayOSCreatePaymentLinkRequest {

    @NotNull
    @Schema(description = "Mã đơn hàng phía bạn (số nguyên, duy nhất mỗi giao dịch)", example = "1732000123456")
    private Long orderCode;

    @NotNull
    @Positive
    @Schema(description = "Số tiền (VND)", example = "90000")
    private Integer amount;

    @NotBlank
    @Schema(description = "Mô tả hiển thị / QR", example = "Ve xem phim #123")
    private String description;

    @NotBlank
    @Schema(description = "URL FE khi thanh toán thành công", example = "http://localhost:5173/payment/success")
    private String returnUrl;

    @NotBlank
    @Schema(description = "URL khi người dùng hủy", example = "http://localhost:5173/payment/cancel")
    private String cancelUrl;

    @Schema(description = "Tên người mua (tuỳ chọn)")
    private String buyerName;

    @Schema(description = "Email người mua (tuỳ chọn)")
    private String buyerEmail;

    @Schema(description = "SĐT người mua (tuỳ chọn)")
    private String buyerPhone;

    @Schema(description = "Unix timestamp hết hạn link (tuỳ chọn)")
    private Long expiredAt;
}
