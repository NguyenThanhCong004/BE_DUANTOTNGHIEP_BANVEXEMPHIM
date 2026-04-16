package com.fpoly.duan.service.impl;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.duan.dto.StaffDTO;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.repository.CinemaRepository;
import com.fpoly.duan.repository.StaffRepository;
import com.fpoly.duan.repository.StaffShiftRepository;
import com.fpoly.duan.repository.UserRepository;
import com.fpoly.duan.service.EmailService;
import com.fpoly.duan.service.StaffService;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class StaffServiceImpl implements StaffService {

    private static final String NEW_STAFF_EMAIL_SUBJECT = "[ERROR404] Tài khoản nhân viên mới";
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    private final StaffRepository staffRepository;
    private final CinemaRepository cinemaRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffShiftRepository staffShiftRepository;
    private final EmailService emailService;

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
                        || s.getCinema() == null
                        || cinemaId.equals(s.getCinema().getCinemaId()))
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

        // Mặc định username = email nếu FE chưa gửi
        String email = staffDTO.getEmail() != null ? staffDTO.getEmail().trim() : null;
        if (email == null || email.isEmpty()) {
            throw new RuntimeException("Email không được để trống");
        }

        if (!email.matches("(?i)^[a-z0-9._%+-]+@gmail\\.com$")) {
            throw new RuntimeException("Email phải đúng định dạng Gmail (vd: abc@gmail.com)");
        }

        String username = staffDTO.getUsername() != null ? staffDTO.getUsername().trim() : null;
        if (username == null || username.isEmpty()) {
            username = email;
        }
        if (username.length() < 6 || username.length() > 50) {
            throw new RuntimeException("Tên đăng nhập phải từ 6 đến 50 ký tự");
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
        if (staffRepository.existsByEmail(email)) {
            throw new RuntimeException("Email đã tồn tại");
        }
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email đã được dùng cho tài khoản khách hàng");
        }
        if (staffRepository.existsByUsername(username)) {
            throw new RuntimeException("Username đã tồn tại");
        }
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Tên đăng nhập đã được dùng cho tài khoản khách hàng");
        }
        if (Boolean.TRUE.equals(staffRepository.existsByPhone(phone))) {
            throw new RuntimeException("Số điện thoại đã tồn tại");
        }
        if (Boolean.TRUE.equals(userRepository.existsByPhone(phone))) {
            throw new RuntimeException("Số điện thoại đã được dùng cho tài khoản khách hàng");
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
        staff.setUsername(username);
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

        if (sendPasswordByEmail) {
            try {
                emailService.sendHtml(
                        email,
                        NEW_STAFF_EMAIL_SUBJECT,
                        buildNewStaffCredentialsHtml(
                                staff.getFullname(),
                                email,
                                username,
                                plainPassword));
            } catch (MessagingException e) {
                log.error("Không gửi được email mật khẩu nhân viên tới {}: {}", email, e.getMessage());
                throw new RuntimeException(
                        "Đã tạo tài khoản nhưng không gửi được email. Kiểm tra cấu hình SMTP hoặc thử lại sau.");
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

    private static String buildNewStaffCredentialsHtml(
            String fullname, String email, String username, String plainPassword) {
        String name = fullname != null && !fullname.isBlank() ? fullname : "bạn";
        return """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"></head><body style="font-family:sans-serif;line-height:1.6;color:#111;">
                <p>Xin chào %s,</p>
                <p>Tài khoản nhân viên của bạn đã được tạo trên hệ thống ERROR404.</p>
                <p><strong>Đăng nhập bằng email:</strong> %s</p>
                <p><strong>Username:</strong> %s</p>
                <p><strong>Mật khẩu tạm:</strong> <span style="font-size:18px;font-weight:bold;letter-spacing:1px;">%s</span></p>
                <p style="color:#555;font-size:14px;">Vui lòng đăng nhập và đổi mật khẩu trong phần hồ sơ nếu cần.</p>
                </body></html>
                """
                .formatted(name, email, username, plainPassword);
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
                if (Boolean.TRUE.equals(staffRepository.existsByEmailAndStaffIdNot(email, id))) {
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
                if (Boolean.TRUE.equals(staffRepository.existsByPhoneAndStaffIdNot(phone, id))) {
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

            // Logic xử lý ca làm khi khóa tài khoản (chuyển từ 1 -> 0)
            if (oldStatus == 1 && nextStatus == 0) {
                List<com.fpoly.duan.entity.StaffShift> futureShifts = staffShiftRepository.findByStaffStaffIdOrderByDateDescStartTimeAsc(id);
                java.time.LocalDate today = java.time.LocalDate.now();
                for (com.fpoly.duan.entity.StaffShift ss : futureShifts) {
                    if (ss.getDate() != null && !ss.getDate().isBefore(today)) {
                        ss.setStaff(null);
                        staffShiftRepository.save(ss);
                    }
                }
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
                        .anyMatch(s -> !s.getStaffId().equals(id));
                
                if (alreadyHasOtherAdmin) {
                    throw new RuntimeException("Rạp \"" + cinema.getName() + "\" đã có một Admin khác đang hoạt động. Vui lòng tạm ngưng tài khoản Admin kia trước khi kích hoạt tài khoản này.");
                }
            }
            staff.setCinema(cinema);
        } else {
            staff.setCinema(null);
        }

        // Không thay đổi password ở update.
        return convertToDTO(staffRepository.save(staff));
    }

    @Override
    public void changePassword(Integer staffId, String currentPassword, String newPassword) {
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
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên với mã: " + staffId));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, staff.getPassword())) {
            throw new RuntimeException("Mật khẩu hiện tại không đúng");
        }
        staff.setPassword(passwordEncoder.encode(newPassword));
        staffRepository.save(staff);
    }

    @Override
    public void deleteStaff(Integer id) {
        if (id == null) {
            throw new RuntimeException("Mã nhân viên không hợp lệ");
        }
        if (!staffRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy nhân viên với mã: " + id);
        }
        staffRepository.deleteById(id);
    }

    private StaffDTO convertToDTO(Staff staff) {
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
}

