package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MembershipRankDTO {
    private Integer id;
    private String rankName;
    private Double minSpending;
    private String description;
    private Double discountPercent;
    private Integer bonusPoint;
}
