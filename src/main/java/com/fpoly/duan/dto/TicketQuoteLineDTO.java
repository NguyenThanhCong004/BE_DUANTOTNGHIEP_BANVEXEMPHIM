package com.fpoly.duan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dòng giá vé theo ghế (đã tính khuyến mãi + giảm hạng)")
public class TicketQuoteLineDTO {

    @Schema(description = "seat_id")
    private Integer seatId;

    @Schema(description = "Nhãn ghế (VD: A10) nếu có")
    private String seatLabel;

    @Schema(description = "Loại ghế")
    private String seatTypeName;

    @Schema(description = "Giá gốc (trước mọi giảm)")
    private Double originalPrice;

    @Schema(description = "Giảm do khuyến mãi phim (VND)")
    private Double promotionDiscount;

    @Schema(description = "Giảm do hạng thành viên (VND)")
    private Double membershipDiscount;

    @Schema(description = "Giá cuối cùng của dòng vé (VND)")
    private Double finalPrice;
}

