package com.fpoly.duan.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.AuthResponse;
import com.fpoly.duan.dto.LoginRequest;
import com.fpoly.duan.dto.UserRequest;
import com.fpoly.duan.service.AuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest loginRequest) {
        AuthResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .status(200)
                .message("Đăng nhập thành công")
                .data(response)
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@RequestBody UserRequest userRequest) {
        AuthResponse response = authService.register(userRequest);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .status(200)
                .message("Đăng ký thành công")
                .data(response)
                .build());
    }
}
