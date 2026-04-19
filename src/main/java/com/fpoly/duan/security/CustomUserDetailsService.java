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
        String key = username != null ? username.trim() : "";
        // Staff: tránh NonUniqueResult khi trùng email/username trong DB
        Staff staff = staffRepository.findFirstByEmailOrderByStaffIdAsc(key).orElse(null);
        if (staff == null) {
            staff = staffRepository.findFirstByUsernameOrderByStaffIdAsc(key).orElse(null);
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
