package com.fpoly.duan.config;

import java.time.LocalDate;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.repository.StaffRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Chỉ tạo tài khoản superadmin mặc định khi {@code app.data.seed=true}.
 */
@Slf4j
@Service
public class DatabaseSeedService {

    private static final String SUPERADMIN_USERNAME = "superadmin";
    private static final String SUPERADMIN_EMAIL = "superadmin@gmail.com";
    private static final String SUPERADMIN_PHONE = "0901111111";
    private static final String SUPERADMIN_PASSWORD = "Staff@123";

    private final PasswordEncoder passwordEncoder;
    private final StaffRepository staffRepository;

    public DatabaseSeedService(
            PasswordEncoder passwordEncoder,
            StaffRepository staffRepository) {
        this.passwordEncoder = passwordEncoder;
        this.staffRepository = staffRepository;
    }

    @Transactional
    public void seedIfEnabled() {
        if (Boolean.TRUE.equals(staffRepository.existsByUsername(SUPERADMIN_USERNAME))) {
            log.info("[DataSeed] Đã có tài khoản '{}' -> bỏ qua tạo mới.", SUPERADMIN_USERNAME);
            return;
        }
        Staff superAdmin = new Staff();
        superAdmin.setEmail(SUPERADMIN_EMAIL);
        superAdmin.setUsername(SUPERADMIN_USERNAME);
        superAdmin.setFullname("Super Admin");
        superAdmin.setRole("SUPER_ADMIN");
        superAdmin.setPassword(passwordEncoder.encode(SUPERADMIN_PASSWORD));
        superAdmin.setAvatar("https://i.pravatar.cc/150?u=superadmin");
        superAdmin.setPhone(SUPERADMIN_PHONE);
        superAdmin.setBirthday(LocalDate.of(1995, 5, 20));
        superAdmin.setStatus(1);

        staffRepository.save(superAdmin);
        log.info("[DataSeed] Đã tạo tài khoản superadmin mặc định: {} / {}", SUPERADMIN_USERNAME, SUPERADMIN_PASSWORD);
    }
}
