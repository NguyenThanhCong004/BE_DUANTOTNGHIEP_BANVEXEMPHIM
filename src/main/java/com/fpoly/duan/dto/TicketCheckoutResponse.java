package com.fpoly.duan.dto;

import com.fpoly.duan.dto.payos.PayOSCheckoutData;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Đơn online đã tạo + dữ liệu PayOS")
public class TicketCheckoutResponse {

    @Schema(description = "ID đơn trong DB (orders_online)")
    private Integer orderOnlineId;

    @Schema(description = "Mã đơn gửi PayOS (long)")
    private Long payosOrderCode;

    @Schema(description = "Số tiền (VND)")
    private Integer amountVnd;

    @Schema(description = "Payload PayOS — redirect checkoutUrl")
    private PayOSCheckoutData payos;
}
