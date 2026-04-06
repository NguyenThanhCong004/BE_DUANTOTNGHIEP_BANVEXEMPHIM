package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "vouchers")
public class Voucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vouchers_id")
    private Integer vouchersId;

    @Column(name = "code", unique = true, nullable = false)
    private String code;

    @Column(name = "discount_type")
    private String discountType;

    @Column(name = "value")
    private Double value;

    @Column(name = "min_order_value")
    private Double minOrderValue;

    @Column(name = "max_discount_amount")
    private Double maxDiscountAmount;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "point_voucher")
    private Integer pointVoucher;

    @Column(name = "status")
    private Integer status;
}
