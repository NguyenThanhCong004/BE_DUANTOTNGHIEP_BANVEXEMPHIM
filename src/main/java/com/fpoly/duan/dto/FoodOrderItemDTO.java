package com.fpoly.duan.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FoodOrderItemDTO {
    private String productName;
    private Integer quantity;
    private Double unitPrice;
}
