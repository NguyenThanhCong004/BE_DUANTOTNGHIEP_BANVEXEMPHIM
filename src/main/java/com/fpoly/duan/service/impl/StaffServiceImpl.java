package com.fpoly.duan.service.impl;

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
import com.fpoly.duan.repository.UserRepository;
import com.fpoly.duan.service.StaffService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class StaffServiceImpl implements StaffService {
    private final StaffRepository staffRepository;
    private final CinemaRepository cinemaRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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

        // Password mặc định phải > 6 ký tự
        String defaultPassword = "12345678";
        if (defaultPassword.length() <= 6) {
            throw new RuntimeException("Mật khẩu mặc định phải trên 6 ký tự");
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
        staff.setPassword(passwordEncoder.encode(defaultPassword));

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

        return convertToDTO(staffRepository.save(staff));
    }

    @Override
    public StaffDTO updateStaff(Integer id, StaffDTO staffDTO) {
        if (staffDTO == null) {
            throw new RuntimeException("Dữ liệu nhân viên không hợp lệ");
        }

        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên với mã: " + id));

        // Vì form admin bắt buộc nhập (trừ cinemaId), xử lý theo hướng kiểm tra not-null để dữ liệu không bị thiếu.
        if (staffDTO.getFullname() == null || staffDTO.getFullname().trim().isEmpty()) {
            throw new RuntimeException("Họ tên không được để trống");
        }
        if (staffDTO.getEmail() == null || staffDTO.getEmail().trim().isEmpty()) {
            throw new RuntimeException("Email không được để trống");
        }
        String email = staffDTO.getEmail().trim();
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
        if (staffDTO.getRole() == null || staffDTO.getRole().trim().isEmpty()) {
            throw new RuntimeException("Vai trò không được để trống");
        }
        if (staffDTO.getAvatar() == null || staffDTO.getAvatar().trim().isEmpty()) {
            throw new RuntimeException("Hình ảnh không được để trống");
        }
        if (staffDTO.getStatus() == null) {
            throw new RuntimeException("Trạng thái không được để trống");
        }
        if (!(staffDTO.getStatus().equals(0) || staffDTO.getStatus().equals(1))) {
            throw new RuntimeException("Trạng thái không hợp lệ");
        }

        // KHÔNG cho phép cập nhật username để đảm bảo tính nhất quán của định danh (Chống hack F12)
        /*
        if (staffDTO.getUsername() != null && !staffDTO.getUsername().trim().isEmpty()) {
            String nextUsername = staffDTO.getUsername().trim();
            if (!nextUsername.equals(staff.getUsername())) {
                if (Boolean.TRUE.equals(staffRepository.existsByUsernameAndStaffIdNot(nextUsername, id))) {
                    throw new RuntimeException("Username đã tồn tại");
                }
                if (userRepository.existsByUsername(nextUsername)) {
                    throw new RuntimeException("Tên đăng nhập đã được dùng cho tài khoản khách hàng");
                }
            }
            staff.setUsername(nextUsername);
        }
        */

        if (!email.equalsIgnoreCase(staff.getEmail() == null ? "" : staff.getEmail())) {
            if (Boolean.TRUE.equals(staffRepository.existsByEmailAndStaffIdNot(email, id))) {
                throw new RuntimeException("Email đã tồn tại");
            }
            if (userRepository.existsByEmail(email)) {
                throw new RuntimeException("Email đã được dùng cho tài khoản khách hàng");
            }
        }

        if (!phone.equals(staff.getPhone() == null ? "" : staff.getPhone().trim())) {
            if (Boolean.TRUE.equals(staffRepository.existsByPhoneAndStaffIdNot(phone, id))) {
                throw new RuntimeException("Số điện thoại đã tồn tại");
            }
            if (Boolean.TRUE.equals(userRepository.existsByPhone(phone))) {
                throw new RuntimeException("Số điện thoại đã được dùng cho tài khoản khách hàng");
            }
        }

        staff.setEmail(email);
        staff.setFullname(staffDTO.getFullname().trim());
        staff.setPhone(phone);
        staff.setBirthday(staffDTO.getBirthday());
        String nextRole = staffDTO.getRole().trim().toUpperCase();
        staff.setRole(nextRole);
        staff.setStatus(staffDTO.getStatus());
        staff.setAvatar(staffDTO.getAvatar().trim());

        Integer cinemaId = staffDTO.getCinemaId();
        if (cinemaId != null) {
            Cinema cinema = cinemaRepository.findById(cinemaId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy rạp với mã: " + cinemaId));
            
            // Ràng buộc: Mỗi rạp chỉ có 1 Admin hoạt động
            if ("ADMIN".equalsIgnoreCase(nextRole) && staffDTO.getStatus() == 1) {
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

