package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "cinemas") 
public class Cinema {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cinema_id")
    private Integer cinemaId;

    private String name;
    private String address;
    private Integer status;
}
