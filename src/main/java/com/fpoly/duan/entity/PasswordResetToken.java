package com.fpoly.duan.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Thời điểm dùng token để đổi mật khẩu; null = chưa dùng. */
    private Instant usedAt;

    /**
     * Băm OTP 6 số gửi qua email (luồng quên mật khẩu trên FE). Null = token cũ chỉ dùng qua liên kết email.
     */
    @Column(length = 255)
    private String otpHash;

    /** Đã nhập đúng OTP; cho phép gọi đặt lại mật khẩu trong {@code expiresAt}. */
    private Instant otpVerifiedAt;
}
