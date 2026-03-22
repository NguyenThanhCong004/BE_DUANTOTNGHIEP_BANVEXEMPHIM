package com.fpoly.duan.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherDTO {
    private Integer id;
    private String code;
    private String discountType;
    private Double value;
    private Double minOrderValue;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer pointVoucher;
    /** 1 = Active, 0 = Inactive */
    private Integer status;
}
