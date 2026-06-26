package com.fpoly.duan.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fpoly.duan.dto.AuthResponse;
import com.fpoly.duan.dto.LoginRequest;
import com.fpoly.duan.dto.UserDTO;
import com.fpoly.duan.dto.UserRequest;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.repository.RevokedTokenRepository;
import com.fpoly.duan.repository.UserRepository;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.security.CustomUserDetailsService;
import com.fpoly.duan.security.JwtService;
import com.fpoly.duan.service.UserService;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserService userService;
    @Mock
    private RevokedTokenRepository revokedTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void loginReturnsTokensAndCustomerWhenCredentialsAreValid() {
        User user = User.builder()
                .userId(7)
                .username("customer01")
                .password("encoded-password")
                .status(1)
                .build();
        CustomUserDetails details = CustomUserDetails.builder().user(user).build();
        UserDTO customer = UserDTO.builder().userId(7).username("customer01").build();

        when(userDetailsService.loadUserAccount("customer01")).thenReturn(details);
        when(passwordEncoder.matches("Password1!", "encoded-password")).thenReturn(true);
        when(jwtService.generateToken(details)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(details)).thenReturn("refresh-token");
        when(userService.getUserById(7)).thenReturn(customer);

        AuthResponse response = authService.login(LoginRequest.builder()
                .username("  customer01  ")
                .password("Password1!")
                .build());

        assertEquals("access-token", response.getToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(customer, response.getUser());
        verify(userDetailsService).loadUserAccount("customer01");
        verify(userService).getUserById(7);
    }

    @Test
    void loginHidesUnknownAccountDetails() {
        when(userDetailsService.loadUserAccount("missing"))
                .thenThrow(new UsernameNotFoundException("missing"));

        BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                () -> authService.login(LoginRequest.builder().username("missing").password("Password1!").build()));

        assertEquals("Sai tài khoản hoặc mật khẩu", exception.getMessage());
        verify(jwtService, never()).generateToken(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registerRejectsWeakPasswordBeforeDatabaseLookups() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> authService.register(UserRequest.builder()
                        .username("customer01")
                        .password("weak")
                        .fullname("Khách Hàng")
                        .email("customer@example.com")
                        .build()));

        assertTrue(exception.getMessage().contains("Mật khẩu"));
        verify(userRepository, never()).existsByUsername(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void registerCreatesCustomerAndReturnsTokens() {
        UserRequest request = UserRequest.builder()
                .username("customer01")
                .password("Password1!")
                .fullname("Khách Hàng")
                .email("customer@example.com")
                .phone("0900000000")
                .build();
        UserDTO created = UserDTO.builder()
                .userId(12)
                .username("customer01")
                .fullname("Khách Hàng")
                .email("customer@example.com")
                .phone("0900000000")
                .status(1)
                .build();
        CustomUserDetails details = CustomUserDetails.builder()
                .user(User.builder().userId(12).username("customer01").password("encoded").status(1).build())
                .build();

        when(userRepository.existsByUsername("customer01")).thenReturn(false);
        when(userRepository.existsByEmail("customer@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("0900000000")).thenReturn(false);
        when(userService.createUser(org.mockito.ArgumentMatchers.any(UserDTO.class), org.mockito.ArgumentMatchers.eq("Password1!")))
                .thenReturn(created);
        when(userDetailsService.loadUserAccount("customer01")).thenReturn(details);
        when(jwtService.generateToken(details)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(details)).thenReturn("refresh-token");

        AuthResponse response = authService.register(request);

        assertEquals(created, response.getUser());
        assertEquals("access-token", response.getToken());
        assertEquals("refresh-token", response.getRefreshToken());
        verify(userService).createUser(org.mockito.ArgumentMatchers.argThat(dto ->
                dto.getUsername().equals("customer01") && dto.getFullname().equals("Khách Hàng") && dto.getStatus() == 1),
                org.mockito.ArgumentMatchers.eq("Password1!"));
    }
}
