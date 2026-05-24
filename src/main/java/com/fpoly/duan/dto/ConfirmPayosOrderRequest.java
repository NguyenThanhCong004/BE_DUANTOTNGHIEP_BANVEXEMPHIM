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
@Schema(description = "Xác nhận lại trạng thái thanh toán PayOS")
public class ConfirmPayosOrderRequest {

    @NotNull
    @Positive
    @Schema(description = "Mã orderCode PayOS (long) đã nhận khi tạo link")
    private Long payosOrderCode;
}
