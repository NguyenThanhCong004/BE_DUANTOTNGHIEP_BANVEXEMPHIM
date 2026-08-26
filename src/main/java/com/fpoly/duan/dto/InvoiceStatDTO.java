package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceStatDTO {
    private Integer orderId;
    private String orderCode;
    private String customerName;
    private Integer cinemaId;
    private String cinemaName;
    private Boolean isCounter;
    private String paymentMethod;
    private Double originalAmount;
    private Double discountAmount;
    private Double finalAmount;
    private Integer status;
    private LocalDateTime createdAt;
}
