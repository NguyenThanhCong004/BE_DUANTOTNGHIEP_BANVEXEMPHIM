package com.fpoly.duan.service.impl;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import com.fpoly.duan.repository.UserRepository;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.security.CustomUserDetailsService;
import com.fpoly.duan.security.JwtService;
import com.fpoly.duan.security.TokenType;
import com.fpoly.duan.service.AuditLogService;
import com.fpoly.duan.service.AuthService;
import com.fpoly.duan.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private static final String STRONG_PASSWORD_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9]).{8,}$";
    private static final String EMAIL_REGEX = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
    private static final String PHONE_REGEX = "^[0-9]{10}$";

    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final UserService userService;
    private final RevokedTokenRepository revokedTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Override
    public AuthResponse login(LoginRequest loginRequest) {
        String loginKey = loginRequest.getAccount() != null ? loginRequest.getAccount().trim() : "";
        CustomUserDetails userDetails;
        try {
            userDetails = userDetailsService.loadCustomerLoginAccount(loginKey);
        } catch (UsernameNotFoundException ex) {
            throw new BadCredentialsException("Sai tài khoản hoặc mật khẩu");
        }
        authenticatePassword(loginRequest.getPassword(), userDetails);
        assertStaffCinemaOperational(userDetails);

        String token = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .user(userService.getUserById(userDetails.getUser().getUserId()))
                .build();
    }

    @Override
    public AuthResponse staffLogin(LoginRequest loginRequest) {
        String loginKey = loginRequest.getAccount() != null ? loginRequest.getAccount().trim() : "";
        CustomUserDetails userDetails;
        try {
            userDetails = userDetailsService.loadStaffAccount(loginKey);
        } catch (UsernameNotFoundException ex) {
            throw new BadCredentialsException("Sai tài khoản hoặc mật khẩu");
        }
        authenticatePassword(loginRequest.getPassword(), userDetails);

        String token = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);
        auditLogService.log(userDetails.getStaff(), "LOGIN", "STAFF", userDetails.getStaff().getStaffId(),
                "Đăng nhập thành công");

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .staff(convertToStaffDTO(userDetails.getStaff()))
                .build();
    }

    @Override
    public AuthResponse register(UserRequest userRequest) {
        if (userRequest == null) {
            throw new RuntimeException("Dữ liệu đăng ký không hợp lệ");
        }
        String email = userRequest.getEmail() != null ? userRequest.getEmail().trim() : "";
        String phone = userRequest.getPhone() != null ? userRequest.getPhone().replaceAll("\\s+", "") : "";
        String password = userRequest.getPassword() != null ? userRequest.getPassword() : "";
        String fullname = userRequest.getFullname() != null ? userRequest.getFullname().trim() : "";

        if (fullname.isBlank()) {
            throw new RuntimeException("Họ tên không được để trống");
        }
        if (email.isBlank()) {
            throw new RuntimeException("Email không được để trống");
        }
        if (!email.matches(EMAIL_REGEX)) {
            throw new RuntimeException("Email không đúng định dạng");
        }
        if (!phone.matches(PHONE_REGEX)) {
            throw new RuntimeException("Số điện thoại không hợp lệ");
        }

        if (!password.matches(STRONG_PASSWORD_REGEX)) {
            throw new RuntimeException("Mật khẩu phải có ít nhất 8 ký tự, gồm chữ thường, chữ hoa và ký tự đặc biệt");
        }

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email đã tồn tại");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new RuntimeException("Số điện thoại đã tồn tại");
        }

        UserDTO userDTO = UserDTO.builder()
                .fullname(fullname)
                .email(email)
                .phone(phone)
                .birthday(userRequest.getBirthday())
                .avatar(userRequest.getAvatar())
                .status(1) // Default status is ACTIVE
                .build();
        
        UserDTO createdUser = userService.createUser(userDTO, password);

        CustomUserDetails userDetails = userDetailsService.loadUserAccount(createdUser.getEmail());
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

        String accountType;
        try {
            accountType = jwtService.extractAccountType(refreshToken);
        } catch (Exception ex) {
            accountType = null;
        }

        CustomUserDetails userDetails;
        try {
            userDetails = userDetailsService.loadByUsernameAndAccountType(username, accountType);
        } catch (UsernameNotFoundException ex) {
            throw new RuntimeException("Refresh token không hợp lệ");
        }
        if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
            throw new DisabledException("Tài khoản đã bị khóa");
        }
        assertStaffCinemaOperational(userDetails);
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
            String accessAccountType = jwtService.extractAccountTypeAllowExpired(accessToken);
            if (accountType != null && accessAccountType != null && !accountType.equalsIgnoreCase(accessAccountType)) {
                throw new RuntimeException("Access token không khớp với refresh token");
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

    private void authenticatePassword(String rawPassword, CustomUserDetails userDetails) {
        if (userDetails == null || rawPassword == null || !passwordEncoder.matches(rawPassword, userDetails.getPassword())) {
            throw new BadCredentialsException("Sai tài khoản hoặc mật khẩu");
        }
        if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
            throw new DisabledException("Tài khoản đã bị khóa");
        }
    }

    private void assertStaffCinemaOperational(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getStaff() == null) {
            return;
        }
        Staff staff = userDetails.getStaff();
        if ("SUPER_ADMIN".equalsIgnoreCase(staff.getRole())) {
            return;
        }
        if (staff.getCinema() != null && staff.getCinema().getStatus() != null && staff.getCinema().getStatus() != 1) {
            throw new DisabledException("Rạp của tài khoản đang bị khóa. Vui lòng liên hệ Super Admin.");
        }
    }

    private StaffDTO convertToStaffDTO(Staff staff) {
        return StaffDTO.builder()
                .staffId(staff.getStaffId())
                .email(staff.getEmail())
                .fullname(staff.getFullname())
                .status(staff.getStatus())
                .phone(staff.getPhone())
                .birthday(staff.getBirthday())
                .role(staff.getRole())
                .avatar(staff.getAvatar())
                .cinemaId(staff.getCinema() != null ? staff.getCinema().getCinemaId() : null)
                .cinemaName(staff.getCinema() != null ? staff.getCinema().getName() : null)
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
