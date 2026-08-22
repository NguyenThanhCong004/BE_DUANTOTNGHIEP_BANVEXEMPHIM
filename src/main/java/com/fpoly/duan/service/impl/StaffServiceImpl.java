package com.fpoly.duan.service.impl;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fpoly.duan.dto.StaffDTO;
import com.fpoly.duan.entity.PasswordResetToken;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.repository.CinemaRepository;
import com.fpoly.duan.repository.PasswordResetTokenRepository;
import com.fpoly.duan.repository.StaffRepository;
import com.fpoly.duan.repository.StaffShiftRepository;
import com.fpoly.duan.repository.UserRepository;
import com.fpoly.duan.service.AuditLogService;
import com.fpoly.duan.service.EmailBrandKit;
import com.fpoly.duan.service.EmailService;
import com.fpoly.duan.service.StaffService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class StaffServiceImpl implements StaffService {

    private static final String NEW_STAFF_EMAIL_SUBJECT = "[MovieZone] Tài khoản nhân viên mới";
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    private static final int OTP_VALIDITY_MINUTES = 10;
    private final SecureRandom secureRandom = new SecureRandom();

    private final StaffRepository staffRepository;
    private final CinemaRepository cinemaRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffShiftRepository staffShiftRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Override
    @Transactional(readOnly = true)
    public List<StaffDTO> getAllStaff() {
        return listStaffByCinema(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffDTO> getAllStaffForSuperAdmin() {
        // Lấy trực tiếp từ repository mà không dùng filter stream để tránh nhầm lẫn logic
        List<Staff> staffList = staffRepository.findAllExceptSuperAdmin();
        return staffList.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffDTO> listStaffByCinema(Integer cinemaId) {
        return staffRepository.findAll()
                .stream()
                .filter(s -> cinemaId == null
                        || (s.getCinema() != null && cinemaId.equals(s.getCinema().getCinemaId())))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public StaffDTO getStaffById(Integer id) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên với mã: " + id));
        return convertToDTO(staff);
    }

    @Override
    public StaffDTO createStaff(StaffDTO staffDTO) {
        if (staffDTO == null) {
            throw new RuntimeException("Dữ liệu nhân viên không hợp lệ");
        }
        // Not null validations (trừ cinemaId)
        if (staffDTO.getFullname() == null || staffDTO.getFullname().trim().isEmpty()) {
            throw new RuntimeException("Họ tên không được để trống");
        }

        String email = staffDTO.getEmail() != null ? staffDTO.getEmail().trim() : null;
        if (email == null || email.isEmpty()) {
            throw new RuntimeException("Email không được để trống");
        }

        if (!email.matches("(?i)^[a-z0-9._%+-]+@gmail\\.com$")) {
            throw new RuntimeException("Email phải đúng định dạng Gmail (vd: abc@gmail.com)");
        }

        if (staffDTO.getPhone() == null || staffDTO.getPhone().trim().isEmpty()) {
            throw new RuntimeException("Số điện thoại không được để trống");
        }
        String phone = staffDTO.getPhone().trim();
        if (!phone.matches("^[0-9]{10}$")) {
            throw new RuntimeException("Số điện thoại phải có 10 chữ số");
        }

        if (staffDTO.getBirthday() == null) {
            throw new RuntimeException("Ngày sinh không được để trống");
        }
        
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate birthDate = staffDTO.getBirthday();
        if (java.time.Period.between(birthDate, today).getYears() < 18) {
            throw new RuntimeException("Nhân viên phải từ đủ 18 tuổi trở lên");
        }
        if (staffDTO.getRole() == null || staffDTO.getRole().trim().isEmpty()) {
            throw new RuntimeException("Vai trò không được để trống");
        }
        if (staffDTO.getAvatar() == null || staffDTO.getAvatar().trim().isEmpty()) {
            throw new RuntimeException("Hình ảnh không được để trống");
        }

        Integer nextStatus = staffDTO.getStatus() != null ? staffDTO.getStatus() : 1; // Bit: mặc định hoạt động
        if (!(nextStatus.equals(0) || nextStatus.equals(1))) {
            throw new RuntimeException("Trạng thái không hợp lệ");
        }

        // Uniqueness
        if (staffRepository.existsByEmail(email) || userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email đã tồn tại");
        }
        if (Boolean.TRUE.equals(staffRepository.existsByPhone(phone))
                || Boolean.TRUE.equals(userRepository.existsByPhone(phone))) {
            throw new RuntimeException("Số điện thoại đã tồn tại");
        }

        String passwordField = staffDTO.getPassword() != null ? staffDTO.getPassword().trim() : "";
        final String plainPassword;
        final boolean sendPasswordByEmail;
        if (!passwordField.isEmpty()) {
            if (passwordField.length() < 6) {
                throw new RuntimeException("Mật khẩu phải có ít nhất 6 ký tự");
            }
            plainPassword = passwordField;
            sendPasswordByEmail = false;
        } else {
            plainPassword = randomPassword(12);
            sendPasswordByEmail = true;
        }

        Staff staff = new Staff();
        staff.setEmail(email);
        staff.setFullname(staffDTO.getFullname().trim());
        staff.setPhone(phone);
        staff.setBirthday(staffDTO.getBirthday());
        String finalRole = staffDTO.getRole().trim().toUpperCase();
        staff.setRole(finalRole);
        staff.setStatus(nextStatus);
        staff.setAvatar(staffDTO.getAvatar().trim());
        staff.setPassword(passwordEncoder.encode(plainPassword));

        Integer cinemaId = staffDTO.getCinemaId();
        if (cinemaId != null) {
            Cinema cinema = cinemaRepository.findById(cinemaId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy rạp với mã: " + cinemaId));
            
            // Ràng buộc: Mỗi rạp chỉ có 1 Admin hoạt động
            if ("ADMIN".equalsIgnoreCase(finalRole) && nextStatus == 1) {
                List<Staff> existingAdmins = staffRepository.findByCinema_CinemaIdAndRoleAndStatus(cinemaId, "ADMIN", 1);
                if (!existingAdmins.isEmpty()) {
                    Staff activeAdmin = existingAdmins.get(0);
                    throw new RuntimeException("Rạp \"" + cinema.getName() + "\" đã có Quản lý \"" + activeAdmin.getFullname() + "\" đang hoạt động. Vui lòng khóa tài khoản này trước.");
                }
            }
            staff.setCinema(cinema);
        }

        Staff saved = staffRepository.save(staff);
        auditLogService.log(currentActorStaff(), "CREATE_STAFF", "STAFF", saved.getStaffId(),
                "Tạo nhân viên \"" + saved.getFullname() + "\" (" + saved.getRole() + ")");

        if (sendPasswordByEmail) {
            if (!emailService.isConfigured()) {
                log.warn("Bỏ qua gửi email mật khẩu nhân viên tới {} vì SMTP chưa được cấu hình.", email);
            } else {
                try {
                    emailService.sendHtml(
                            email,
                            NEW_STAFF_EMAIL_SUBJECT,
                            buildNewStaffCredentialsHtml(
                                    staff.getFullname(),
                                    email,
                                    plainPassword));
                } catch (Exception e) {
                    log.error("Không gửi được email mật khẩu nhân viên tới {}: {}", email, e.getMessage(), e);
                }
            }
        }

        return convertToDTO(saved);
    }

    private static String randomPassword(int length) {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(PASSWORD_CHARS.charAt(r.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private String buildNewStaffCredentialsHtml(
            String fullname, String email, String plainPassword) {
        String name = fullname != null && !fullname.isBlank() ? fullname : "bạn";
        String body = """
                <p style="margin:0 0 16px;">Xin chào <strong>%s</strong>,</p>
                <p style="margin:0 0 20px;">Tài khoản nhân viên của bạn đã được tạo trên hệ thống <strong style="color:#d4ff00;">MovieZone</strong>. Thông tin đăng nhập:</p>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.08);border-radius:12px;">
                  <tr>
                    <td style="padding:14px 18px;font-size:13px;color:rgba(240,240,255,0.5);">Email đăng nhập</td>
                    <td style="padding:14px 18px;font-size:14px;font-weight:700;text-align:right;">%s</td>
                  </tr>
                  <tr>
                    <td style="padding:14px 18px;font-size:13px;color:rgba(240,240,255,0.5);border-top:1px solid rgba(255,255,255,0.08);">Mật khẩu tạm</td>
                    <td style="padding:14px 18px;font-size:18px;font-weight:800;letter-spacing:1px;text-align:right;color:#d4ff00;border-top:1px solid rgba(255,255,255,0.08);">%s</td>
                  </tr>
                </table>
                %s
                <p style="margin:20px 0 0;color:rgba(240,240,255,0.5);font-size:12px;">Vui lòng đổi mật khẩu ngay sau khi đăng nhập lần đầu.</p>
                """.formatted(name, email, plainPassword,
                EmailBrandKit.button(frontendBaseUrl + "/staff/login", "Đăng nhập ngay"));
        return EmailBrandKit.wrap("Tài khoản nhân viên MovieZone của bạn đã sẵn sàng", body);
    }

    @Override
    public StaffDTO updateStaff(Integer id, StaffDTO staffDTO) {
        if (staffDTO == null) {
            throw new RuntimeException("Dữ liệu nhân viên không hợp lệ");
        }

        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên với mã: " + id));

        // Cập nhật các trường nếu có trong DTO
        if (staffDTO.getFullname() != null && !staffDTO.getFullname().trim().isEmpty()) {
            staff.setFullname(staffDTO.getFullname().trim());
        }
        
        if (staffDTO.getEmail() != null && !staffDTO.getEmail().trim().isEmpty()) {
            String email = staffDTO.getEmail().trim();
            if (!email.matches("(?i)^[a-z0-9._%+-]+@gmail\\.com$")) {
                throw new RuntimeException("Email phải đúng định dạng Gmail");
            }
            if (!email.equalsIgnoreCase(staff.getEmail())) {
                if (Boolean.TRUE.equals(staffRepository.existsByEmailAndStaffIdNot(email, id))
                        || Boolean.TRUE.equals(userRepository.existsByEmail(email))) {
                    throw new RuntimeException("Email đã tồn tại");
                }
            }
            staff.setEmail(email);
        }

        if (staffDTO.getPhone() != null && !staffDTO.getPhone().trim().isEmpty()) {
            String phone = staffDTO.getPhone().trim();
            if (!phone.matches("^[0-9]{10}$")) {
                throw new RuntimeException("Số điện thoại phải có 10 chữ số");
            }
            if (!phone.equals(staff.getPhone())) {
                if (Boolean.TRUE.equals(staffRepository.existsByPhoneAndStaffIdNot(phone, id))
                        || Boolean.TRUE.equals(userRepository.existsByPhone(phone))) {
                    throw new RuntimeException("Số điện thoại đã tồn tại");
                }
            }
            staff.setPhone(phone);
        }

        if (staffDTO.getBirthday() != null) {
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDate birthDate = staffDTO.getBirthday();
            if (java.time.Period.between(birthDate, today).getYears() < 18) {
                throw new RuntimeException("Nhân viên phải từ đủ 18 tuổi trở lên");
            }
            staff.setBirthday(birthDate);
        }

        String nextRole = staff.getRole();
        if (staffDTO.getRole() != null) {
            nextRole = staffDTO.getRole().trim().toUpperCase();
            staff.setRole(nextRole);
        }

        if (staffDTO.getAvatar() != null) {
            staff.setAvatar(staffDTO.getAvatar().trim());
        }

        if (staffDTO.getStatus() != null) {
            Integer oldStatus = staff.getStatus();
            Integer nextStatus = staffDTO.getStatus();
            staff.setStatus(nextStatus);

            // Khi khóa tài khoản (1→0): xóa ca tương lai, giữ lịch sử
            if (Integer.valueOf(1).equals(oldStatus) && Integer.valueOf(0).equals(nextStatus)) {
                java.time.LocalDate today = java.time.LocalDate.now();
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                // Xóa tất cả ca từ ngày mai trở đi
                staffShiftRepository.deleteByStaffIdAndDateAfterOrEqual(id, today.plusDays(1));
                // Xóa ca hôm nay chưa bắt đầu (startTime > now)
                staffShiftRepository.deleteTodayUnstartedByStaffId(id, today, now);
            }
        }

        Integer cinemaId = staffDTO.getCinemaId();
        if (cinemaId != null) {
            Cinema cinema = cinemaRepository.findById(cinemaId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy rạp với mã: " + cinemaId));
            
            // Ràng buộc: Mỗi rạp chỉ có 1 Admin hoạt động
            if ("ADMIN".equalsIgnoreCase(nextRole) && staff.getStatus() == 1) {
                List<Staff> existingAdmins = staffRepository.findByCinema_CinemaIdAndRoleAndStatus(cinemaId, "ADMIN", 1);
                // Loại trừ chính mình nếu đang là admin hoạt động của rạp này
                boolean alreadyHasOtherAdmin = existingAdmins.stream()
                        .anyMatch(s -> s.getStaffId() != null && !s.getStaffId().equals(id));

                if (alreadyHasOtherAdmin) {
                    Staff other = existingAdmins.stream()
                            .filter(s -> !s.getStaffId().equals(id))
                            .findFirst().orElse(null);
                    String otherName = (other != null) ? other.getFullname() : "khác";
                    throw new RuntimeException("Rạp \"" + cinema.getName() + "\" đã có Quản lý \"" + otherName + "\" đang hoạt động. Vui lòng tạm ngưng tài khoản kia trước.");
                }
            }
            staff.setCinema(cinema);
        }

        // Không thay đổi password ở update.
        Staff saved = staffRepository.save(staff);
        auditLogService.log(currentActorStaff(), "UPDATE_STAFF", "STAFF", saved.getStaffId(),
                "Cập nhật nhân viên \"" + saved.getFullname() + "\"");
        return convertToDTO(saved);
    }

    @Override
    public void sendPasswordChangeOtp(Integer staffId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên"));
        if (staff.getEmail() == null || staff.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản chưa có email để gửi mã xác nhận");
        }
        passwordResetTokenRepository.deleteUnusedByStaffId(staffId);

        String otp = String.format("%06d", secureRandom.nextInt(900_000) + 100_000);
        Instant expiresAt = Instant.now().plusSeconds(OTP_VALIDITY_MINUTES * 60L);
        PasswordResetToken token = PasswordResetToken.builder()
                .token(UUID.randomUUID().toString().replace("-", ""))
                .staff(staff)
                .expiresAt(expiresAt)
                .otpHash(passwordEncoder.encode(otp))
                .build();
        passwordResetTokenRepository.save(token);

        String body = """
                <p style="margin:0 0 16px;">Xin chào <strong>%s</strong>,</p>
                <p style="margin:0 0 20px;">Có yêu cầu đổi mật khẩu cho tài khoản nhân viên MovieZone của bạn. Nhập mã bên dưới để xác nhận:</p>
                <div style="text-align:center;margin:0 0 20px;">
                  <span style="display:inline-block;padding:14px 28px;background:rgba(212,255,0,0.1);border:1px solid rgba(212,255,0,0.4);border-radius:10px;font-size:30px;font-weight:800;letter-spacing:8px;color:#d4ff00;">%s</span>
                </div>
                <p style="margin:0;color:rgba(240,240,255,0.5);font-size:12px;">Mã có hiệu lực trong %d phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.</p>
                """.formatted(staff.getFullname() != null ? staff.getFullname() : "bạn", otp, OTP_VALIDITY_MINUTES);
        try {
            emailService.sendHtml(staff.getEmail(), "[MovieZone] Mã xác nhận đổi mật khẩu", EmailBrandKit.wrap("Mã xác nhận đổi mật khẩu: " + otp, body));
        } catch (Exception e) {
            log.error("Không gửi được email OTP đổi mật khẩu tới {}: {}", staff.getEmail(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không gửi được email, vui lòng thử lại");
        }
    }

    @Override
    public void changePassword(Integer staffId, String currentPassword, String newPassword, String otpCode) {
        if (staffId == null) {
            throw new RuntimeException("Mã nhân viên không hợp lệ");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new RuntimeException("Mật khẩu mới tối thiểu 8 ký tự");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            throw new RuntimeException("Chưa xác thực");
        }
        if (details.getStaff() == null || !details.getStaff().getStaffId().equals(staffId)) {
            throw new RuntimeException("Chỉ được đổi mật khẩu của chính bạn");
        }

        // Xác minh OTP
        PasswordResetToken otpToken = passwordResetTokenRepository
                .findLatestActiveByStaffId(staffId, Instant.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Mã xác nhận đã hết hạn hoặc chưa được gửi. Vui lòng nhấn 'Gửi mã' lại."));
        if (otpCode == null || otpCode.isBlank() || !passwordEncoder.matches(otpCode.trim(), otpToken.getOtpHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã xác nhận không đúng");
        }

        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên với mã: " + staffId));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, staff.getPassword())) {
            throw new RuntimeException("Mật khẩu hiện tại không đúng");
        }
        staff.setPassword(passwordEncoder.encode(newPassword));
        staffRepository.save(staff);

        otpToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(otpToken);
    }

    @Override
    public void resetPasswordByStaffId(Integer staffId, String newPassword) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên"));
        staff.setPassword(passwordEncoder.encode(newPassword));
        staffRepository.save(staff);
    }

    @Override
    public void deleteStaff(Integer id) {
        if (id == null) {
            throw new RuntimeException("Mã nhân viên không hợp lệ");
        }
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên với mã: " + id));
        staffRepository.deleteById(id);
        auditLogService.log(currentActorStaff(), "DELETE_STAFF", "STAFF", id,
                "Xóa nhân viên \"" + staff.getFullname() + "\"");
    }

    private Staff currentActorStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails details) {
            return details.getStaff();
        }
        return null;
    }

    private StaffDTO convertToDTO(Staff staff) {
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
                .build();
    }
}

