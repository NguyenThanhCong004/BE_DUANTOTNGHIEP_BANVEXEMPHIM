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
@Schema(name = "LoginRequest", description = "Đăng nhập user hoặc staff — trường username nhận email/username")
public class LoginRequest {
    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Schema(example = "user01", description = "Username hoặc email (staff đăng nhập bằng email)")
    private String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Schema(example = "Password123!")
    private String password;
}
