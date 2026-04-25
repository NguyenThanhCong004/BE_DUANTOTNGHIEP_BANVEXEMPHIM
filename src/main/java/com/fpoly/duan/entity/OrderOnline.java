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
}
