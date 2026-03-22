package com.fpoly.duan.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Đặt bắp nước riêng (không kèm vé) + PayOS")
public class FoodOnlyCheckoutRequest {

    @NotNull
    @Positive
    @Schema(description = "Rạp lấy hàng (menu cinema_products)")
    private Integer cinemaId;

    @NotEmpty
    @Size(max = 30)
    @Valid
    @Schema(description = "Danh sách sản phẩm + số lượng")
    private List<SnackLineRequest> items;

    @NotNull
    @Schema(description = "URL FE khi PayOS redirect thành công")
    private String returnUrl;

    @NotNull
    @Schema(description = "URL khi hủy thanh toán")
    private String cancelUrl;
}
