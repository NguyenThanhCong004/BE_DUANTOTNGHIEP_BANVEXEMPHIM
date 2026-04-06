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

    @Column(nullable = false, columnDefinition = "NVARCHAR(255)")
    private String email;

    @Column(nullable = false, columnDefinition = "NVARCHAR(100)")
    private String username;

    @Column(nullable = false, columnDefinition = "NVARCHAR(255)")
    private String password;

    // Dùng NVARCHAR để lưu tên có dấu (tiếng Việt)
    @Column(nullable = false, columnDefinition = "NVARCHAR(200)")
    private String fullname;

    // Bit: 0/1 (tương ứng Khóa/Hoạt động). Mặc định = 1 (Hoạt động)
    @Column(nullable = false)
    private Integer status = 1;

    @Column(nullable = false, columnDefinition = "NVARCHAR(20)")
    private String phone;

    @Column(nullable = false)
    private LocalDate birthday;

    @Column(nullable = false, columnDefinition = "NVARCHAR(50)")
    private String role;

    /** Ảnh có thể là URL hoặc data URL (base64) từ FE — cần độ dài lớn, không giới hạn 500 ký tự. */
    @Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String avatar;

    @ManyToOne
    @JoinColumn(name = "cinema_id", nullable = true)
    private Cinema cinema;
}
