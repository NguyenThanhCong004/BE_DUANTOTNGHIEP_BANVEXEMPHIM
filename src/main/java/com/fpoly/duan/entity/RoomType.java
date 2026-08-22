package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "room_types")
public class RoomType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_type_id")
    private Integer roomTypeId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "standard_seat_count", nullable = false)
    private Integer standardSeatCount;

    @Column(name = "vip_seat_count", nullable = false)
    private Integer vipSeatCount;

    @Column(name = "couple_seat_count", nullable = false)
    private Integer coupleSeatCount;
}
