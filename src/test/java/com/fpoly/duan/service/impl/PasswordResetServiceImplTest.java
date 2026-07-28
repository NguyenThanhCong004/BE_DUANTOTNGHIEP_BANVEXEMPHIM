package com.fpoly.duan.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.fpoly.duan.dto.ForgotPasswordRequest;
import com.fpoly.duan.dto.ForgotPasswordResponse;
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.repository.PasswordResetTokenRepository;
import com.fpoly.duan.repository.StaffRepository;
import com.fpoly.duan.repository.UserRepository;
import com.fpoly.duan.service.EmailService;
import com.fpoly.duan.service.StaffService;
import com.fpoly.duan.service.UserService;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private UserService userService;
    @Mock
    private StaffService staffService;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordResetServiceImpl passwordResetService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "otpValidityMinutes", 10L);
        ReflectionTestUtils.setField(passwordResetService, "passwordStepValidityMinutes", 15L);
    }

    @Test
    void requestResetAcceptsCustomerPhoneNumber() throws Exception {
        User user = User.builder()
                .userId(7)
                .fullname("Customer One")
                .email("customer@example.com")
                .phone("0900000000")
                .status(1)
                .build();

        when(userRepository.findByPhone("0900000000")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("otp-hash");

        ForgotPasswordResponse response = passwordResetService.requestReset(ForgotPasswordRequest.builder()
                .account(" 0900000000 ")
                .build());

        assertNotNull(response.getResetSessionToken());
        assertEquals("cu****r@example.com", response.getMaskedEmail());
        verify(tokenRepository).deleteUnusedByUserId(7);
        verify(tokenRepository).save(argThat(token ->
                token.getUser() == user && token.getStaff() == null && "otp-hash".equals(token.getOtpHash())));
        verify(emailService).sendHtml(eq("customer@example.com"), anyString(), contains("Customer One"));
    }

    @Test
    void requestResetAcceptsStaffPhoneNumberWhenNoCustomerUsesIt() throws Exception {
        Staff staff = new Staff();
        staff.setStaffId(3);
        staff.setFullname("Staff One");
        staff.setEmail("staff@example.com");
        staff.setPhone("0911000001");
        staff.setStatus(1);

        when(userRepository.findByPhone("0911000001")).thenReturn(Optional.empty());
        when(staffRepository.findByPhone("0911000001")).thenReturn(Optional.of(staff));
        when(passwordEncoder.encode(anyString())).thenReturn("otp-hash");

        ForgotPasswordResponse response = passwordResetService.requestReset(ForgotPasswordRequest.builder()
                .account("0911000001")
                .build());

        assertNotNull(response.getResetSessionToken());
        assertEquals("st****f@example.com", response.getMaskedEmail());
        verify(tokenRepository).deleteUnusedByStaffId(3);
        verify(tokenRepository).save(argThat(token ->
                token.getUser() == null && token.getStaff() == staff && "otp-hash".equals(token.getOtpHash())));
        verify(emailService).sendHtml(eq("staff@example.com"), anyString(), contains("Staff One"));
    }
}
