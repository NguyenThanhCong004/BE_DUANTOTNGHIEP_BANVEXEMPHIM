package com.fpoly.duan.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendForgotOtpRequest {

    @NotBlank(message = "Phiên đặt lại mật khẩu không hợp lệ")
    private String resetSessionToken;
}
