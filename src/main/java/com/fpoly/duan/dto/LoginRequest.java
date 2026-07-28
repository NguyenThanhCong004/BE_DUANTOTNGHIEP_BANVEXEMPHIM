package com.fpoly.duan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LoginRequest", description = "Đăng nhập theo từng luồng riêng: user dùng /login, staff dùng /staff-login")
public class LoginRequest {
    @NotBlank(message = "Thông tin đăng nhập không được để trống")
    @Schema(example = "customer@gmail.com", description = "Email hoặc số điện thoại (khách và staff đều đăng nhập bằng email/SĐT)")
    private String account;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Schema(example = "Password123!")
    private String password;
}
