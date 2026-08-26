package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffStatDTO {
    private Integer staffId;
    private String fullName;
    private String email;
    private String phone;
    private String role;
    private String cinemaName;
    private Long totalOrders;
    private Double totalRevenue;
    private Integer status;
}
