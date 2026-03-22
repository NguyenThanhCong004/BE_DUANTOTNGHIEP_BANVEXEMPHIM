package com.fpoly.duan.service;

import com.fpoly.duan.dto.AuthResponse;
import com.fpoly.duan.dto.LoginRequest;
import com.fpoly.duan.dto.RefreshRequest;
import com.fpoly.duan.dto.UserRequest;

public interface AuthService {
    AuthResponse login(LoginRequest loginRequest);
    AuthResponse register(UserRequest userRequest);
    AuthResponse refresh(RefreshRequest refreshRequest);
}
