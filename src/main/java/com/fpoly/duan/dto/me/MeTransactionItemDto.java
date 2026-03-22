package com.fpoly.duan.dto.me;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeTransactionItemDto {
    private String label;
    private String sub;
    private double price;
    private int qty;
    private String icon;
}
