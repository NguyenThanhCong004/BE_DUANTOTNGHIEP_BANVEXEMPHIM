package com.fpoly.duan.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Báo giá checkout vé (không tạo đơn)")
public class TicketQuoteResponse {

    @Schema(description = "Danh sách dòng vé theo ghế")
    private List<TicketQuoteLineDTO> ticketLines;

    @Schema(description = "Tổng tiền vé (sau KM + giảm hạng)")
    private Double ticketTotal;

    @Schema(description = "Tổng tiền bắp nước")
    private Double snackTotal;

    @Schema(description = "Giảm voucher (VND)")
    private Double voucherDiscount;

    @Schema(description = "Tổng thanh toán cuối cùng (VND)")
    private Double finalAmount;

    @Schema(description = "Tên hạng hiện tại (để hiển thị), nếu có")
    private String rankName;

    @Schema(description = "% giảm theo hạng (0 nếu không có)")
    private Double membershipDiscountPercent;
}

