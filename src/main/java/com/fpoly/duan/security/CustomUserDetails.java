package com.fpoly.duan.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.entity.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomUserDetails implements UserDetails {

    private User user;
    private Staff staff;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (staff != null) {
            // Staff roles (ADMIN, STAFF, etc.) from database
            String role = staff.getRole();
            if (role == null) role = "STAFF";
            if (!role.startsWith("ROLE_")) {
                role = "ROLE_" + role.toUpperCase();
            }
            return List.of(new SimpleGrantedAuthority(role));
        }
        // Customers are always ROLE_USER
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return (staff != null) ? staff.getPassword() : user.getPassword();
    }

    @Override
    public String getUsername() {
        // Với staff: ưu tiên username, fallback email
        if (staff == null) return user.getUsername();
        return (staff.getUsername() != null && !staff.getUsername().trim().isEmpty())
                ? staff.getUsername()
                : staff.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        Integer status = (staff != null) ? staff.getStatus() : user.getStatus();
        return status != null && status != 0;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        Integer status = (staff != null) ? staff.getStatus() : user.getStatus();
        return status != null && status != 0;
    }

    public Integer getUserId() {
        return (staff != null) ? staff.getStaffId() : user.getUserId();
    }
}
