package com.fpoly.duan.service.impl;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import com.fpoly.duan.dto.AuthResponse;
import com.fpoly.duan.dto.LoginRequest;
import com.fpoly.duan.dto.UserDTO;
import com.fpoly.duan.dto.UserRequest;
import com.fpoly.duan.security.JwtService;
import com.fpoly.duan.service.AuthService;
import com.fpoly.duan.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final UserService userService;

    @Override
    public AuthResponse login(LoginRequest loginRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );
        UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getUsername());
        String token = jwtService.generateToken(userDetails);
        UserDTO userDTO = userService.getUserByUsername(loginRequest.getUsername());
        
        return AuthResponse.builder()
                .token(token)
                .user(userDTO)
                .build();
    }

    @Override
    public AuthResponse register(UserRequest userRequest) {
        UserDTO userDTO = UserDTO.builder()
                .username(userRequest.getUsername())
                .fullname(userRequest.getFullname())
                .email(userRequest.getEmail())
                .phone(userRequest.getPhone())
                .birthday(userRequest.getBirthday())
                .avatar(userRequest.getAvatar())
                .role(0) // Default role is USER
                .status(1) // Default status is ACTIVE
                .build();
        
        UserDTO createdUser = userService.createUser(userDTO, userRequest.getPassword());
        
        UserDetails userDetails = userDetailsService.loadUserByUsername(createdUser.getUsername());
        String token = jwtService.generateToken(userDetails);
        
        return AuthResponse.builder()
                .token(token)
                .user(createdUser)
                .build();
    }
}
