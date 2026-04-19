package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "seatype")
public class SeatType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_type_id")
    private Integer seatTypeId;

    private String name;
    private Double surcharge;

    /** Màu hiển thị sơ đồ (#RRGGBB), có thể null (client dùng mặc định theo tên). */
    @Column(name = "color", length = 16)
    private String color;

    /**
     * {@code true}: loại ghế đôi (một ghế logic, layout 2 cột). Ghế đơn = {@code false}.
     * Lưu DB để truy vấn / API không phải đoán từ tên.
     */
    @Column(name = "couple_seat")
    private Boolean coupleSeat = Boolean.FALSE;
}
