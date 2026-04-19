package com.fpoly.duan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyForgotOtpRequest {

    @NotBlank(message = "Phiên đặt lại mật khẩu không hợp lệ")
    private String resetSessionToken;

    @NotBlank(message = "Mã xác nhận không được để trống")
    @Pattern(regexp = "^\\d{6}$", message = "Mã xác nhận phải gồm 6 chữ số")
    private String otp;
}
