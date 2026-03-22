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
        // Tìm staff theo email (gmail) trước, nếu không có thì tìm theo username
        Staff staff = staffRepository.findByEmail(username).orElse(null);
        if (staff == null) {
            staff = staffRepository.findByUsername(username).orElse(null);
        }
        if (staff != null) return CustomUserDetails.builder().staff(staff).build();

        // Then try in users table
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username/email: " + username));
        
        return CustomUserDetails.builder().user(user).build();
    }
}
