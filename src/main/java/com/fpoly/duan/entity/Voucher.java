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

    private String code;

    @Column(name = "discount_type")
    private String discountType;

    private Double value;

    @Column(name = "min_order_value")
    private Double minOrderValue;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "point_voucher")
    private Integer pointVoucher;

    private Integer status;
}
