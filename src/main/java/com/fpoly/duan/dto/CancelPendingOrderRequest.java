package com.fpoly.duan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Hủy đơn chờ thanh toán PayOS (trả ghế / xóa đơn)")
public class CancelPendingOrderRequest {

    @NotNull
    @Positive
    @Schema(description = "Mã orderCode PayOS (long) đã nhận khi tạo link")
    private Long payosOrderCode;
}
