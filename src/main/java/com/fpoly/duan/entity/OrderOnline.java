package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "orders_online")
public class OrderOnline {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_online_id")
    private Integer orderOnlineId;

    @Column(name = "order_code")
    private String orderCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "original_amount")
    private Double originalAmount;

    @Column(name = "discount_amount")
    private Double discountAmount;

    @Column(name = "final_amount")
    private Double finalAmount;

    private Integer status;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "user_vouchers_id")
    private UserVoucher userVoucher;

    @ManyToOne
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne
    @JoinColumn(name = "cinema_id")
    private Cinema cinema;

    @Column(name = "payment_method")
    private String paymentMethod; // CASH, TRANSFER, PAYOS

    /** Mốc gửi email vé; tránh gửi lặp khi PayOS webhook được gọi lại. */
    @Column(name = "ticket_email_sent_at")
    private LocalDateTime ticketEmailSentAt;

    /** Mã HMAC bất đối xứng dùng để sinh mã vạch hóa đơn tại quầy, không lộ mã đơn hoặc thông tin rạp. */
    @Column(name = "receipt_token", length = 1500, unique = true)
    private String receiptToken;

    /** Mốc xử lý đơn do phòng chiếu bị đóng (đã dời sang suất khác hoặc đã hủy để hoàn tiền). */
    @Column(name = "rescheduled_at")
    private LocalDateTime rescheduledAt;

    /** Mốc nhân viên quét QR bắp nước xác nhận khách đã nhận hàng tại quầy. */
    @Column(name = "food_delivered_at")
    private LocalDateTime foodDeliveredAt;
}
