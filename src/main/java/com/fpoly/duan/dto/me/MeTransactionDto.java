package com.fpoly.duan.dto.me;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeTransactionDto {
    /** Khóa duy nhất cho FE (đơn: orderOnlineId, điểm: ph-{id}) */
    private String id;
    private String orderCode;
    /** ticket_online | food | points */
    private String type;
    /** pending | completed | cancelled */
    private String status;
    private List<MeTransactionItemDto> items;
    private double originalAmount;
    private double discountAmount;
    private double finalAmount;
    private LocalDateTime createdAt;
    private int pointsEarned;
    private String voucherCode;
}
