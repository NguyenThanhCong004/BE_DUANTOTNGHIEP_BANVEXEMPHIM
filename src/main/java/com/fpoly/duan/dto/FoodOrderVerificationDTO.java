package com.fpoly.duan.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FoodOrderVerificationDTO {
    private String orderCode;
    private String customerName;
    private String cinemaName;
    private List<FoodOrderItemDTO> items;
    private Double totalAmount;
    private Boolean delivered;
    private String deliveredAt;
}
