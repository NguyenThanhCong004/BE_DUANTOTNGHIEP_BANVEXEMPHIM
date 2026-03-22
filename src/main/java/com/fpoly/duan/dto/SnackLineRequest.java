package com.fpoly.duan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Một dòng sản phẩm (bắp nước) trong đơn")
public class SnackLineRequest {

    @NotNull
    @Schema(description = "ID sản phẩm (products.product_id)")
    private Integer productId;

    @NotNull
    @Min(1)
    @Max(99)
    @Schema(description = "Số lượng")
    private Integer quantity;
}
