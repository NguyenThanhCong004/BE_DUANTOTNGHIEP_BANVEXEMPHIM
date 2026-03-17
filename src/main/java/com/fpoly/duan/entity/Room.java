package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "rooms")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Integer roomId;

    private String name;
    private Integer status;

    @ManyToOne
    @JoinColumn(name = "cinema_id")
    private Cinema cinema;
}
