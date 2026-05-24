package com.fpoly.duan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.util.List;

@Data
public class CounterCheckoutRequest {

    @NotNull(message = "Thiếu ID suất chiếu")
    private Integer showtimeId;

    private List<Integer> seatIds;

    @Valid
    private List<ProductItem> products;

    @NotEmpty(message = "Vui lòng chọn phương thức thanh toán")
    @Pattern(regexp = "^(CASH|TRANSFER)$", message = "Phương thức thanh toán chỉ được là CASH hoặc TRANSFER")
    private String paymentMethod;

    private Integer userId;

    @Data
    public static class ProductItem {
        @NotNull(message = "Thiếu ID sản phẩm")
        private Integer productId;

        @NotNull(message = "Thiếu số lượng")
        @Min(value = 1, message = "Số lượng phải ít nhất là 1")
        private Integer quantity;
    }
}
