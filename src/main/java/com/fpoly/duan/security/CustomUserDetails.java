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

import java.text.Normalizer;
import java.util.Locale;

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
            return List.of(new SimpleGrantedAuthority(normalizeStaffRole(staff.getRole())));
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

    public String getAccountType() {
        return staff != null ? "STAFF" : "USER";
    }

    /** Tương thích dữ liệu role cũ trước khi hệ thống thống nhất STAFF/ADMIN/SUPER_ADMIN. */
    private static String normalizeStaffRole(String role) {
        if (role == null || role.isBlank()) return "ROLE_STAFF";
        String normalized = Normalizer.normalize(role, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if (normalized.startsWith("ROLE_")) normalized = normalized.substring(5);
        return switch (normalized) {
            case "EMPLOYEE", "NHANVIEN", "NHAN_VIEN", "STAFF" -> "ROLE_STAFF";
            case "MANAGER", "QUANLY", "QUAN_LY", "ADMIN" -> "ROLE_ADMIN";
            case "SUPERADMIN", "SUPER_ADMIN" -> "ROLE_SUPER_ADMIN";
            default -> "ROLE_" + normalized;
        };
    }
}
