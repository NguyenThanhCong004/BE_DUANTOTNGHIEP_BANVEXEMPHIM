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
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.repository.PasswordResetTokenRepository;
import com.fpoly.duan.repository.StaffRepository;
import com.fpoly.duan.repository.UserRepository;
import com.fpoly.duan.service.EmailBrandKit;
import com.fpoly.duan.service.EmailService;
import com.fpoly.duan.service.PasswordResetService;
import com.fpoly.duan.service.StaffService;
import com.fpoly.duan.service.UserService;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final String OTP_SUBJECT = "[MovieZone] Mã xác nhận đặt lại mật khẩu";

    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final UserService userService;
    private final StaffService staffService;
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

        ResolvedAccount account = resolveAccount(raw).orElse(null);
        if (account == null || account.isLocked()) {
            if (account != null) {
                log.debug("Bỏ qua quên mật khẩu — tài khoản đã khóa: {}={}", account.idLabel(), account.id());
            }
            return ForgotPasswordResponse.builder()
                    .resetSessionToken(sessionToken)
                    .maskedEmail(null)
                    .build();
        }

        if (account.isUser()) {
            tokenRepository.deleteUnusedByUserId(account.user().getUserId());
        } else {
            tokenRepository.deleteUnusedByStaffId(account.staff().getStaffId());
        }

        String otp = generateOtp();
        Instant expiresAt = Instant.now().plusSeconds(otpValidityMinutes * 60);
        PasswordResetToken.PasswordResetTokenBuilder tokenBuilder = PasswordResetToken.builder()
                .token(sessionToken)
                .expiresAt(expiresAt)
                .otpHash(passwordEncoder.encode(otp))
                .otpVerifiedAt(null);
        tokenRepository.save(account.isUser() ? tokenBuilder.user(account.user()).build() : tokenBuilder.staff(account.staff()).build());

        try {
            emailService.sendHtml(account.email(), OTP_SUBJECT, buildOtpEmailHtml(account.fullname(), otp));
        } catch (MessagingException e) {
            log.error("Không gửi được email OTP quên mật khẩu tới {}: {}", account.email(), e.getMessage());
        }

        return ForgotPasswordResponse.builder()
                .resetSessionToken(sessionToken)
                .maskedEmail(maskEmail(account.email()))
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

        ResolvedAccount account = accountOf(row);
        if (account.isLocked()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản đã bị khóa");
        }

        String otp = generateOtp();
        row.setOtpHash(passwordEncoder.encode(otp));
        row.setOtpVerifiedAt(null);
        row.setExpiresAt(Instant.now().plusSeconds(otpValidityMinutes * 60));
        tokenRepository.save(row);

        try {
            emailService.sendHtml(account.email(), OTP_SUBJECT, buildOtpEmailHtml(account.fullname(), otp));
        } catch (MessagingException e) {
            log.error("Không gửi được email OTP (gửi lại) tới {}: {}", account.email(), e.getMessage());
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

        ResolvedAccount account = accountOf(row);
        if (account.isLocked()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản đã bị khóa");
        }

        if (row.getOtpHash() != null && row.getOtpVerifiedAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng xác minh mã OTP trước khi đặt lại mật khẩu");
        }

        if (account.isUser()) {
            userService.resetPasswordByUserId(account.user().getUserId(), request.getNewPassword());
        } else {
            staffService.resetPasswordByStaffId(account.staff().getStaffId(), request.getNewPassword());
        }
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
        if (accountOf(row).isLocked()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản đã bị khóa");
        }
        return row;
    }

    /** Tài khoản có thể là khách hàng (User) hoặc nhân viên (Staff) — đúng 1 trong 2 được set trên mỗi token. */
    private record ResolvedAccount(User user, Staff staff) {
        boolean isUser() {
            return user != null;
        }

        String idLabel() {
            return isUser() ? "userId" : "staffId";
        }

        Object id() {
            return isUser() ? user.getUserId() : staff.getStaffId();
        }

        String email() {
            return isUser() ? user.getEmail() : staff.getEmail();
        }

        String fullname() {
            return isUser() ? user.getFullname() : staff.getFullname();
        }

        boolean isLocked() {
            Integer status = isUser() ? user.getStatus() : staff.getStatus();
            return status != null && status == 0;
        }
    }

    private ResolvedAccount accountOf(PasswordResetToken row) {
        if (row.getUser() != null) {
            return new ResolvedAccount(row.getUser(), null);
        }
        return new ResolvedAccount(null, row.getStaff());
    }

    private Optional<ResolvedAccount> resolveAccount(String raw) {
        boolean isEmail = raw.contains("@");
        Optional<User> userOpt = isEmail
                ? userRepository.findFirstByEmailIgnoreCaseOrderByUserIdAsc(raw)
                : userRepository.findFirstByUsernameIgnoreCaseOrderByUserIdAsc(raw);
        if (userOpt.isPresent()) {
            return Optional.of(new ResolvedAccount(userOpt.get(), null));
        }

        Optional<Staff> staffOpt = isEmail
                ? staffRepository.findFirstByEmailIgnoreCaseOrderByStaffIdAsc(raw)
                : staffRepository.findFirstByUsernameIgnoreCaseOrderByStaffIdAsc(raw);
        return staffOpt.map(staff -> new ResolvedAccount(null, staff));
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
        String body = """
                <p style="margin:0 0 16px;">Xin chào <strong>%s</strong>,</p>
                <p style="margin:0 0 20px;">Có yêu cầu đặt lại mật khẩu cho tài khoản MovieZone của bạn. Nhập mã xác nhận bên dưới để tiếp tục:</p>
                <div style="text-align:center;margin:0 0 20px;">
                  <span style="display:inline-block;padding:14px 28px;background:rgba(212,255,0,0.1);border:1px solid rgba(212,255,0,0.4);border-radius:10px;font-size:30px;font-weight:800;letter-spacing:8px;color:#d4ff00;">%s</span>
                </div>
                <p style="margin:0;color:rgba(240,240,255,0.5);font-size:12px;">Mã có hiệu lực trong khoảng %d phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.</p>
                """.formatted(name, otp, otpValidityMinutes);
        return EmailBrandKit.wrap("Mã xác nhận đặt lại mật khẩu của bạn là " + otp, body);
    }
}
