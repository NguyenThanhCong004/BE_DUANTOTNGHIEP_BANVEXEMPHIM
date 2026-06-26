package com.fpoly.duan.service;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.repository.ShowtimeRepository;
import com.fpoly.duan.security.CustomUserDetails;

@Service
public class CinemaScopeService {

    private final ShowtimeRepository showtimeRepository;

    public CinemaScopeService(ShowtimeRepository showtimeRepository) {
        this.showtimeRepository = showtimeRepository;
    }

    public Integer effectiveCinemaId(Integer requestedCinemaId) {
        CustomUserDetails details = currentStaffDetails();
        if (hasAuthority(details, "ROLE_SUPER_ADMIN")) {
            return requestedCinemaId;
        }

        Staff staff = details.getStaff();
        Integer staffCinemaId = staff != null && staff.getCinema() != null ? staff.getCinema().getCinemaId() : null;
        if (staffCinemaId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản chưa được gán rạp");
        }
        if (requestedCinemaId != null && !staffCinemaId.equals(requestedCinemaId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền thao tác rạp này");
        }
        return staffCinemaId;
    }

    public void requireCinemaAccess(Integer cinemaId) {
        if (cinemaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu cinemaId");
        }
        effectiveCinemaId(cinemaId);
    }

    public void requireCinemaAccess(Cinema cinema) {
        requireCinemaAccess(cinema != null ? cinema.getCinemaId() : null);
    }

    public boolean isCinemaOperational(Cinema cinema) {
        return cinema != null && (cinema.getStatus() == null || cinema.getStatus() == 1);
    }

    public boolean hasRemainingShowtimes(Cinema cinema) {
        if (cinema == null || cinema.getCinemaId() == null) {
            return false;
        }
        return showtimeRepository.existsByRoom_Cinema_CinemaIdAndStartTimeGreaterThanEqual(
                cinema.getCinemaId(), LocalDate.now().atStartOfDay());
    }

    public boolean isCinemaPubliclyAvailable(Cinema cinema) {
        return isCinemaOperational(cinema);
    }

    public void requireCinemaOperational(Cinema cinema) {
        if (!isCinemaOperational(cinema)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rạp đang tạm ngừng hoạt động, không thể tạo nghiệp vụ mới");
        }
    }

    public boolean isSuperAdmin() {
        return hasAuthority(currentStaffDetails(), "ROLE_SUPER_ADMIN");
    }

    private CustomUserDetails currentStaffDetails() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details) || details.getStaff() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập nhân sự");
        }
        return details;
    }

    private boolean hasAuthority(CustomUserDetails details, String authority) {
        return details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
