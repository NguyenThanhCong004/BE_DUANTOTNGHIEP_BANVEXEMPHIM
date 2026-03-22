package com.fpoly.duan.dto.payos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Phản hồi từ PayOS sau khi tạo link — FE redirect tới checkoutUrl")
public class PayOSCheckoutData {
    @Schema(description = "URL trang thanh toán PayOS", example = "https://pay.payos.vn/web/...")
    private String checkoutUrl;

    @Schema(description = "Mã link PayOS")
    private String paymentLinkId;

    @Schema(description = "Chuỗi QR VietQR")
    private String qrCode;

    private Long orderCode;
    private Long amount;
    private String currency;
    private String status;
    private String description;
}
