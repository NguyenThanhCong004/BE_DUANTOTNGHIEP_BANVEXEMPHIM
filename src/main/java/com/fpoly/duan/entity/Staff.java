package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "staff")
public class Staff {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "staff_id")
    private Integer staffId;

    private String email;
    private String password;
    private String fullname;
    private Integer status;
    private String phone;
    private LocalDate birthday;
    private String role;
    private String avatar;

    @ManyToOne
    @JoinColumn(name = "cinema_id")
    private Cinema cinema;
}
