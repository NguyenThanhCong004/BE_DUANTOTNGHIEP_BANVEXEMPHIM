package com.fpoly.duan.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.AuthResponse;
import com.fpoly.duan.dto.ForgotPasswordRequest;
import com.fpoly.duan.dto.ForgotPasswordResponse;
import com.fpoly.duan.dto.LoginRequest;
import com.fpoly.duan.dto.RefreshRequest;
import com.fpoly.duan.dto.ResendForgotOtpRequest;
import com.fpoly.duan.dto.ResetPasswordRequest;
import com.fpoly.duan.dto.UserRequest;
import com.fpoly.duan.dto.VerifyForgotOtpRequest;
import com.fpoly.duan.service.AuthService;
import com.fpoly.duan.service.PasswordResetService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "1. Xác thực (Auth)", description = """
        Đăng nhập / đăng ký / refresh / quên mật khẩu — **không cần JWT**.
        FE: `Login.jsx`, `Register.jsx`, `LoginEmployee.jsx` gọi `/login` và `/register`.
        """)
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập", description = """
            Trả `data.token` (JWT), `data.refreshToken`, và **một trong hai**: `data.user` (khách) hoặc `data.staff` (nhân sự).
            Staff đăng nhập bằng email + mật khẩu (gửi email vào trường `username`).
            """)
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .status(200)
                .message("Đăng nhập thành công")
                .data(response)
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody UserRequest userRequest) {
        AuthResponse response = authService.register(userRequest);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .status(200)
                .message("Đăng ký thành công")
                .data(response)
                .build());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới JWT", description = "Body: `refreshToken` — trả access token mới.")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshRequest refreshRequest) {
        AuthResponse response = authService.refresh(refreshRequest);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .status(200)
                .message("Làm mới token thành công")
                .data(response)
                .build());
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Quên mật khẩu (khách) — bước 1", description = """
            Body: `usernameOrEmail`. Nếu tài khoản khách hợp lệ, gửi OTP 6 số qua email.
            Luôn trả `data.resetSessionToken` (và `maskedEmail` nếu có tài khoản) để FE tiếp tục bước 2–3.
            """)
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        ForgotPasswordResponse data = passwordResetService.requestReset(request);
        return ResponseEntity.ok(ApiResponse.<ForgotPasswordResponse>builder()
                .status(200)
                .message("Nếu tài khoản tồn tại, mã xác nhận đã được gửi tới email đăng ký.")
                .data(data)
                .build());
    }

    @PostMapping("/forgot-password/verify-otp")
    @Operation(summary = "Quên mật khẩu — bước 2 (OTP)", description = "Body: `resetSessionToken`, `otp` (6 chữ số).")
    public ResponseEntity<ApiResponse<Void>> verifyForgotOtp(@Valid @RequestBody VerifyForgotOtpRequest request) {
        passwordResetService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(200)
                .message("Xác minh mã thành công. Bạn có thể đặt lại mật khẩu.")
                .build());
    }

    @PostMapping("/forgot-password/resend-otp")
    @Operation(summary = "Quên mật khẩu — gửi lại OTP", description = "Body: `resetSessionToken`.")
    public ResponseEntity<ApiResponse<Void>> resendForgotOtp(@Valid @RequestBody ResendForgotOtpRequest request) {
        passwordResetService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(200)
                .message("Đã gửi lại mã xác nhận tới email đăng ký.")
                .build());
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Đặt lại mật khẩu bằng token", description = """
            Body: `token` (= resetSessionToken sau bước 1), `newPassword` (8–30 ký tự).
            Với luồng OTP: phải gọi verify-otp trước. Token chỉ có liên kết email (cũ): không cần OTP.
            """)
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(200)
                .message("Đặt lại mật khẩu thành công. Bạn có thể đăng nhập bằng mật khẩu mới.")
                .build());
    }
}
