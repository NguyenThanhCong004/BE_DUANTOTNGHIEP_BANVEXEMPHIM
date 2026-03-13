package com.fpoly.duan.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "username", unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "fullname", nullable = false, length = 100)
    private String fullname;

    @Column(name = "status")
    private Integer status = 1;

    @Column(name = "birthday")
    private LocalDate birthday;

    @Column(name = "avatar", length = 255)
    private String avatar;

    @Column(name = "email", unique = true, nullable = false, length = 100)
    private String email;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "points")
    private Integer points = 0;

    @Column(name = "total_spending")
    private Double totalSpending = 0.0;

    @Column(name = "role")
    private Integer role = 0; // 0: USER, 1: STAFF, 2: ADMIN, 3: SUPER_ADMIN
}
