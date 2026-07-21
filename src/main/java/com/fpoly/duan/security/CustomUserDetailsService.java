package com.fpoly.duan.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.repository.StaffRepository;
import com.fpoly.duan.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final StaffRepository staffRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return loadAnyAccount(username);
    }

    public CustomUserDetails loadUserAccount(String usernameOrEmail) throws UsernameNotFoundException {
        String key = usernameOrEmail != null ? usernameOrEmail.trim() : "";
        User user = userRepository.findFirstByUsernameIgnoreCaseOrderByUserIdAsc(key).orElse(null);
        if (user == null && key.contains("@")) {
            user = userRepository.findFirstByEmailIgnoreCaseOrderByUserIdAsc(key).orElse(null);
        }
        if (user == null) {
            throw new UsernameNotFoundException("User not found with username/email: " + usernameOrEmail);
        }
        return CustomUserDetails.builder().user(user).build();
    }

    public CustomUserDetails loadCustomerLoginAccount(String emailOrPhone) throws UsernameNotFoundException {
        String key = emailOrPhone != null ? emailOrPhone.trim() : "";
        User user = null;
        if (key.contains("@")) {
            user = userRepository.findFirstByEmailIgnoreCaseOrderByUserIdAsc(key).orElse(null);
        } else {
            String phone = key.replaceAll("\\s+", "");
            if (phone.matches("^[0-9]{10}$")) {
                user = userRepository.findByPhone(phone).orElse(null);
            }
        }
        if (user == null) {
            throw new UsernameNotFoundException("User not found with email/phone: " + emailOrPhone);
        }
        return CustomUserDetails.builder().user(user).build();
    }

    public CustomUserDetails loadStaffAccount(String usernameOrEmail) throws UsernameNotFoundException {
        String key = usernameOrEmail != null ? usernameOrEmail.trim() : "";
        // Staff: tránh NonUniqueResult khi trùng email/username trong DB
        Staff staff = staffRepository.findFirstByEmailIgnoreCaseOrderByStaffIdAsc(key).orElse(null);
        if (staff == null) {
            staff = staffRepository.findFirstByUsernameIgnoreCaseOrderByStaffIdAsc(key).orElse(null);
        }
        if (staff == null) {
            String phone = key.replaceAll("\\s+", "");
            if (phone.matches("^[0-9]{10}$")) {
                staff = staffRepository.findByPhone(phone).orElse(null);
            }
        }
        if (staff != null) {
            return CustomUserDetails.builder().staff(staff).build();
        }
        throw new UsernameNotFoundException("Staff not found with username/email/phone: " + usernameOrEmail);
    }

    public CustomUserDetails loadByUsernameAndAccountType(String usernameOrEmail, String accountType)
            throws UsernameNotFoundException {
        if ("STAFF".equalsIgnoreCase(accountType)) {
            return loadStaffAccount(usernameOrEmail);
        }
        if ("USER".equalsIgnoreCase(accountType)) {
            return loadUserAccount(usernameOrEmail);
        }
        return loadAnyAccount(usernameOrEmail);
    }

    private CustomUserDetails loadAnyAccount(String username) throws UsernameNotFoundException {
        String key = username != null ? username.trim() : "";
        Staff staff = staffRepository.findFirstByEmailIgnoreCaseOrderByStaffIdAsc(key).orElse(null);
        if (staff == null) {
            staff = staffRepository.findFirstByUsernameIgnoreCaseOrderByStaffIdAsc(key).orElse(null);
        }
        if (staff != null) {
            return CustomUserDetails.builder().staff(staff).build();
        }

        User user = userRepository.findFirstByUsernameOrderByUserIdAsc(key).orElse(null);
        if (user == null && key.contains("@")) {
            user = userRepository.findFirstByEmailIgnoreCaseOrderByUserIdAsc(key).orElse(null);
        }
        if (user == null) {
            throw new UsernameNotFoundException("User not found with username/email: " + username);
        }
        return CustomUserDetails.builder().user(user).build();
    }
}
