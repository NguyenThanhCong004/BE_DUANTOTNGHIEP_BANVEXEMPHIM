package com.fpoly.duan.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class CounterCheckoutRequest {
    @NotNull(message = "Thiếu ID suất chiếu")
    private Integer showtimeId;

    private List<Integer> seatIds; // Danh sách ID ghế chọn

    private List<ProductItem> products; // Danh sách bắp nước

    @NotEmpty(message = "Vui lòng chọn phương thức thanh toán")
    private String paymentMethod; // CASH, TRANSFER

    private Integer userId; // ID khách hàng (nếu có thẻ thành viên)

    @Data
    public static class ProductItem {
        private Integer productId;
        private Integer quantity;
    }
}
