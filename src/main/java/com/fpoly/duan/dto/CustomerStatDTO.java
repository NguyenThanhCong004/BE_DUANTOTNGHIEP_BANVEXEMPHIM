package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerStatDTO {
    private Integer userId;
    private String fullName;
    private String email;
    private String phone;
    private String membershipRank;
    private Long totalOrders;
    private Double totalSpending;
    private Integer points;
    private Integer status;
}
