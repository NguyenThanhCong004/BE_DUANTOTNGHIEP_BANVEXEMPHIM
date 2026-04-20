package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    private String username;
    private String password;
    private String fullname;
    private Integer status;
    private LocalDate birthday;
    @Column(columnDefinition = "TEXT")
    private String avatar;
    private String email;
    private String phone;
    private Integer points;
    
    @Column(name = "rank_id")
    private Integer rankId;

    @Column(name = "total_spending")
    private Double totalSpending;
}