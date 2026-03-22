package com.fpoly.duan.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.AuthResponse;
import com.fpoly.duan.dto.LoginRequest;
import com.fpoly.duan.dto.RefreshRequest;
import com.fpoly.duan.dto.UserRequest;
import com.fpoly.duan.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "1. Xác thực (Auth)", description = """
        Đăng nhập / đăng ký / refresh — **không cần JWT**.
        FE: `Login.jsx`, `Register.jsx`, `LoginEmployee.jsx` gọi `/login` và `/register`.
        """)
public class AuthController {

    private final AuthService authService;

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
}
