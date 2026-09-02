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
    /** Mã QR riêng của đơn cho phần bắp nước, để khách xuất trình tại quầy — có ở MỌI đơn có món ăn
     * kèm theo, kể cả khi đơn đó cũng có vé (2 mã QR độc lập: vé soát ở rạp, bắp nước nhận ở quầy).
     * Null nếu đơn không có món ăn nào hoặc chưa thanh toán. */
    private String receiptToken;
}
