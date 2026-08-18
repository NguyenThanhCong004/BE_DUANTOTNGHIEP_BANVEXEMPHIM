package com.fpoly.duan.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 1 đơn hàng có vé bị ảnh hưởng bởi việc đóng phòng — hiển thị ở màn hình admin xử lý dời vé. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AffectedOrderDTO {
    private Integer orderOnlineId;
    private String orderCode;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private Double finalAmount;
    private List<AffectedTicketDTO> tickets;
}
