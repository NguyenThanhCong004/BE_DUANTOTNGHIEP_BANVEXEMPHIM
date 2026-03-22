package com.fpoly.duan.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderOnlineDTO {
    private Integer id;
    private String orderCode;
    private LocalDateTime createdAt;
    private Double originalAmount;
    private Double discountAmount;
    private Double finalAmount;
    private Integer status;
    private Integer userId;
    private String customerName;
    private String customerEmail;
}
