package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordResponse {

    /** FE gửi lại ở bước xác minh OTP và đặt mật khẩu mới. */
    private String resetSessionToken;

    /** Email đã che (chỉ có khi tài khoản tồn tại và không bị khóa). */
    private String maskedEmail;
}
