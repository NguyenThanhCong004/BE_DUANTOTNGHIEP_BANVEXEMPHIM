package com.fpoly.duan.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordRequest {

    @NotBlank(message = "Tài khoản không được để trống")
    @Size(max = 255, message = "Tài khoản quá dài")
    /** Tên đăng nhập hoặc email đã đăng ký. */
    @JsonAlias("email")
    private String usernameOrEmail;
}
