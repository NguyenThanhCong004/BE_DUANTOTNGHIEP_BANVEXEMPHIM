package com.fpoly.duan.service.impl;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import com.fpoly.duan.dto.AuthResponse;
import com.fpoly.duan.dto.LoginRequest;
import com.fpoly.duan.dto.RefreshRequest;
import com.fpoly.duan.dto.StaffDTO;
import com.fpoly.duan.dto.UserDTO;
import com.fpoly.duan.dto.UserRequest;
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.entity.RevokedToken;
import com.fpoly.duan.repository.RevokedTokenRepository;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.security.JwtService;
import com.fpoly.duan.security.TokenType;
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
    private final RevokedTokenRepository revokedTokenRepository;

    @Override
    public AuthResponse login(LoginRequest loginRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );
        CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserByUsername(loginRequest.getUsername());
        String token = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);
        
        AuthResponse.AuthResponseBuilder responseBuilder = AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken);

        if (userDetails.getStaff() != null) {
            responseBuilder.staff(convertToStaffDTO(userDetails.getStaff()));
        } else if (userDetails.getUser() != null) {
            responseBuilder.user(userService.getUserById(userDetails.getUser().getUserId()));
        }
        
        return responseBuilder.build();
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
                .status(1) // Default status is ACTIVE
                .build();
        
        UserDTO createdUser = userService.createUser(userDTO, userRequest.getPassword());
        
        UserDetails userDetails = userDetailsService.loadUserByUsername(createdUser.getUsername());
        String token = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);
        
        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .user(createdUser)
                .build();
    }

    @Override
    public AuthResponse refresh(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();
        String accessToken = request.getAccessToken();

        if (revokedTokenRepository.existsByToken(refreshToken) || revokedTokenRepository.existsByToken(accessToken)) {
            throw new RuntimeException("Token đã bị vô hiệu hóa");
        }

        String username;
        try {
            username = jwtService.extractUsername(refreshToken);
        } catch (Exception ex) {
            throw new RuntimeException("Refresh token không hợp lệ");
        }

        CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserByUsername(username);
        if (!jwtService.isRefreshTokenValid(refreshToken, userDetails)) {
            throw new RuntimeException("Refresh token không hợp lệ");
        }

        try {
            String accessUsername = jwtService.extractUsernameAllowExpired(accessToken);
            if (!username.equals(accessUsername)) {
                throw new RuntimeException("Access token không khớp với refresh token");
            }
            if (jwtService.extractTokenTypeAllowExpired(accessToken) != TokenType.ACCESS) {
                throw new RuntimeException("Token không phải là access token");
            }
        } catch (Exception ex) {
            throw new RuntimeException("Access token không hợp lệ");
        }

        revokeTokenIfNeeded(accessToken, TokenType.ACCESS);
        revokeTokenIfNeeded(refreshToken, TokenType.REFRESH);

        String newAccess = jwtService.generateToken(userDetails);
        String newRefresh = jwtService.generateRefreshToken(userDetails);
        
        AuthResponse.AuthResponseBuilder responseBuilder = AuthResponse.builder()
                .token(newAccess)
                .refreshToken(newRefresh);

        if (userDetails.getStaff() != null) {
            responseBuilder.staff(convertToStaffDTO(userDetails.getStaff()));
        } else if (userDetails.getUser() != null) {
            responseBuilder.user(userService.getUserById(userDetails.getUser().getUserId()));
        }

        return responseBuilder.build();
    }

    private StaffDTO convertToStaffDTO(Staff staff) {
        return StaffDTO.builder()
                .staffId(staff.getStaffId())
                .email(staff.getEmail())
                .username(staff.getUsername())
                .fullname(staff.getFullname())
                .status(staff.getStatus())
                .phone(staff.getPhone())
                .birthday(staff.getBirthday())
                .role(staff.getRole())
                .avatar(staff.getAvatar())
                .cinemaId(staff.getCinema() != null ? staff.getCinema().getCinemaId() : null)
                .build();
    }

    private void revokeTokenIfNeeded(String token, TokenType tokenType) {
        if (revokedTokenRepository.existsByToken(token)) {
            return;
        }
        RevokedToken revoked = RevokedToken.builder()
                .token(token)
                .tokenType(tokenType)
                .revokedAt(new java.util.Date())
                .expiresAt(jwtService.extractExpirationAllowExpired(token))
                .build();
        revokedTokenRepository.save(revoked);
    }
}
