package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStatDTO {
    private Integer productId;
    private String productName;
    private String categoryName;
    private Double unitPrice;
    private Long quantitySold;
    private Double totalRevenue;
    private Integer status;
}
