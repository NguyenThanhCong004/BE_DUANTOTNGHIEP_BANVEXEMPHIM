package com.fpoly.duan.service.impl;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fpoly.duan.dto.ForgotPasswordRequest;
import com.fpoly.duan.dto.ForgotPasswordResponse;
import com.fpoly.duan.dto.ResendForgotOtpRequest;
import com.fpoly.duan.dto.ResetPasswordRequest;
import com.fpoly.duan.dto.VerifyForgotOtpRequest;
import com.fpoly.duan.entity.PasswordResetToken;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.repository.PasswordResetTokenRepository;
import com.fpoly.duan.repository.UserRepository;
import com.fpoly.duan.service.EmailService;
import com.fpoly.duan.service.PasswordResetService;
import com.fpoly.duan.service.UserService;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final String OTP_SUBJECT = "Mã xác nhận đặt lại mật khẩu — vé xem phim";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.password-reset.otp-validity-minutes:10}")
    private long otpValidityMinutes;

    @Value("${app.password-reset.password-step-validity-minutes:15}")
    private long passwordStepValidityMinutes;

    @Override
    @Transactional
    public ForgotPasswordResponse requestReset(ForgotPasswordRequest request) {
        String sessionToken = newSessionToken();
        String raw = request.getUsernameOrEmail() != null ? request.getUsernameOrEmail().trim() : "";
        if (raw.isEmpty()) {
            return ForgotPasswordResponse.builder()
                    .resetSessionToken(sessionToken)
                    .maskedEmail(null)
                    .build();
        }

        Optional<User> userOpt = resolveUser(raw);
        if (userOpt.isEmpty()) {
            return ForgotPasswordResponse.builder()
                    .resetSessionToken(sessionToken)
                    .maskedEmail(null)
                    .build();
        }

        User user = userOpt.get();
        if (user.getStatus() != null && user.getStatus() == 0) {
            log.debug("Bỏ qua quên mật khẩu — tài khoản đã khóa: userId={}", user.getUserId());
            return ForgotPasswordResponse.builder()
                    .resetSessionToken(sessionToken)
                    .maskedEmail(null)
                    .build();
        }

        tokenRepository.deleteUnusedByUserId(user.getUserId());
        String otp = generateOtp();
        Instant expiresAt = Instant.now().plusSeconds(otpValidityMinutes * 60);
        tokenRepository.save(PasswordResetToken.builder()
                .token(sessionToken)
                .user(user)
                .expiresAt(expiresAt)
                .otpHash(passwordEncoder.encode(otp))
                .otpVerifiedAt(null)
                .build());

        try {
            emailService.sendHtml(user.getEmail(), OTP_SUBJECT, buildOtpEmailHtml(user.getFullname(), otp));
        } catch (MessagingException e) {
            log.error("Không gửi được email OTP quên mật khẩu tới {}: {}", user.getEmail(), e.getMessage());
        }

        return ForgotPasswordResponse.builder()
                .resetSessionToken(sessionToken)
                .maskedEmail(maskEmail(user.getEmail()))
                .build();
    }

    @Override
    @Transactional
    public void verifyOtp(VerifyForgotOtpRequest request) {
        PasswordResetToken row = loadActiveOtpRow(request.getResetSessionToken());
        if (row.getOtpHash() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Phiên đặt lại mật khẩu không hợp lệ");
        }
        if (!passwordEncoder.matches(request.getOtp().trim(), row.getOtpHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã xác nhận không đúng");
        }

        row.setOtpVerifiedAt(Instant.now());
        row.setExpiresAt(Instant.now().plusSeconds(passwordStepValidityMinutes * 60));
        tokenRepository.save(row);
    }

    @Override
    @Transactional
    public void resendOtp(ResendForgotOtpRequest request) {
        PasswordResetToken row = tokenRepository.findByToken(request.getResetSessionToken().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Không tìm thấy phiên đặt lại mật khẩu"));

        if (row.getUsedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phiên đặt lại mật khẩu đã được sử dụng");
        }
        if (row.getOtpHash() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể gửi lại mã cho phiên này");
        }

        User user = row.getUser();
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản đã bị khóa");
        }

        String otp = generateOtp();
        row.setOtpHash(passwordEncoder.encode(otp));
        row.setOtpVerifiedAt(null);
        row.setExpiresAt(Instant.now().plusSeconds(otpValidityMinutes * 60));
        tokenRepository.save(row);

        try {
            emailService.sendHtml(user.getEmail(), OTP_SUBJECT, buildOtpEmailHtml(user.getFullname(), otp));
        } catch (MessagingException e) {
            log.error("Không gửi được email OTP (gửi lại) tới {}: {}", user.getEmail(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không gửi được email. Vui lòng thử lại sau.");
        }
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String rawToken = request.getToken() != null ? request.getToken().trim() : "";
        if (rawToken.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã xác nhận không hợp lệ");
        }

        PasswordResetToken row = tokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Phiên đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"));

        if (row.getUsedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phiên đặt lại mật khẩu đã được sử dụng");
        }
        if (row.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phiên đặt lại mật khẩu đã hết hạn");
        }

        User user = row.getUser();
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản đã bị khóa");
        }

        if (row.getOtpHash() != null && row.getOtpVerifiedAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng xác minh mã OTP trước khi đặt lại mật khẩu");
        }

        userService.resetPasswordByUserId(user.getUserId(), request.getNewPassword());
        row.setUsedAt(Instant.now());
        tokenRepository.save(row);
    }

    private PasswordResetToken loadActiveOtpRow(String sessionToken) {
        String t = sessionToken != null ? sessionToken.trim() : "";
        PasswordResetToken row = tokenRepository.findByToken(t)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Phiên đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"));
        if (row.getUsedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phiên đặt lại mật khẩu đã được sử dụng");
        }
        if (row.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã xác nhận đã hết hạn");
        }
        User user = row.getUser();
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản đã bị khóa");
        }
        return row;
    }

    private Optional<User> resolveUser(String raw) {
        if (raw.contains("@")) {
            return userRepository.findByEmailIgnoreCase(raw);
        }
        return userRepository.findByUsernameIgnoreCase(raw);
    }

    private static String newSessionToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String generateOtp() {
        int n = secureRandom.nextInt(900_000) + 100_000;
        return String.format("%06d", n);
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return null;
        }
        int at = email.indexOf('@');
        String user = email.substring(0, at);
        String domain = email.substring(at + 1);
        if (user.length() <= 1) {
            return "****@" + domain;
        }
        return user.substring(0, Math.min(2, user.length()))
                + "****"
                + user.substring(user.length() - 1)
                + "@"
                + domain;
    }

    private String buildOtpEmailHtml(String fullname, String otp) {
        String name = fullname != null && !fullname.isBlank() ? fullname : "bạn";
        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"></head><body style="font-family:sans-serif;line-height:1.5;">
                <p>Xin chào %s,</p>
                <p>Mã xác nhận đặt lại mật khẩu của bạn là:</p>
                <p style="font-size:28px;font-weight:bold;letter-spacing:6px;">%s</p>
                <p>Mã có hiệu lực trong khoảng %d phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.</p>
                </body></html>
                """.formatted(name, otp, otpValidityMinutes);
    }
}
