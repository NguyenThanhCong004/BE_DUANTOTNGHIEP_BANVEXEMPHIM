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
<<<<<<< HEAD
        // Staff: tránh NonUniqueResult khi trùng email/username trong DB
        Staff staff = staffRepository.findFirstByEmailOrderByStaffIdAsc(key).orElse(null);
        if (staff == null) {
            staff = staffRepository.findFirstByUsernameOrderByStaffIdAsc(key).orElse(null);
        }
        if (staff != null) {
            return CustomUserDetails.builder().staff(staff).build();
=======
        // Tìm staff theo email (gmail) trước, nếu không có thì tìm theo username
        Staff staff = staffRepository.findByEmail(key).orElse(null);
        if (staff == null) {
            staff = staffRepository.findByUsername(key).orElse(null);
>>>>>>> 861315ab6ef1f999ba3aa2770b86b944e504adf3
        }

<<<<<<< HEAD
        User user = userRepository.findFirstByUsernameOrderByUserIdAsc(key).orElse(null);
        if (user == null && key.contains("@")) {
            user = userRepository.findFirstByEmailIgnoreCaseOrderByUserIdAsc(key).orElse(null);
        }
        if (user == null) {
            throw new UsernameNotFoundException("User not found with username/email: " + username);
        }
=======
        // Then try in users table (username or email)
        User user = userRepository.findByUsernameIgnoreCase(key)
                .or(() -> userRepository.findByEmailIgnoreCase(key))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username/email: " + key));
        
>>>>>>> 861315ab6ef1f999ba3aa2770b86b944e504adf3
        return CustomUserDetails.builder().user(user).build();
    }
}
