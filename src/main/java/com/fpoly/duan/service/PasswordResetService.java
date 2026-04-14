package com.fpoly.duan.service;

import com.fpoly.duan.dto.ForgotPasswordRequest;
import com.fpoly.duan.dto.ForgotPasswordResponse;
import com.fpoly.duan.dto.ResendForgotOtpRequest;
import com.fpoly.duan.dto.ResetPasswordRequest;
import com.fpoly.duan.dto.VerifyForgotOtpRequest;

public interface PasswordResetService {

    /**
     * Bắt đầu quên mật khẩu: nếu tài khoản khách hợp lệ thì gửi OTP qua email.
     * Luôn trả {@link ForgotPasswordResponse} (không lộ tài khoản có tồn tại hay không).
     */
    ForgotPasswordResponse requestReset(ForgotPasswordRequest request);

    void verifyOtp(VerifyForgotOtpRequest request);

    void resendOtp(ResendForgotOtpRequest request);

    /** Đặt lại mật khẩu: luồng OTP (đã xác minh) hoặc token cũ từ liên kết email (không OTP). */
    void resetPassword(ResetPasswordRequest request);
}
