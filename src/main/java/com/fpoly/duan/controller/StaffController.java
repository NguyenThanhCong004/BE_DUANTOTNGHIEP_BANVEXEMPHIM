package com.fpoly.duan.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.StaffDTO;
import com.fpoly.duan.dto.UserPasswordChangeRequest;
import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.repository.CinemaRepository;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.service.CinemaScopeService;
import com.fpoly.duan.service.StaffService;
import com.fpoly.duan.util.SearchUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "3. Nhân viên (Staff)", description = "CRUD nhân viên")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class StaffController {

    private final StaffService staffService;
    private final CinemaScopeService cinemaScopeService;
    private final CinemaRepository cinemaRepository;

    @GetMapping("/me")
    @Operation(summary = "Hồ sơ nhân viên đang đăng nhập")
    public ResponseEntity<ApiResponse<StaffDTO>> getMyStaffProfile() {
        StaffDTO staff = staffService.getStaffById(currentStaffId());
        return ResponseEntity.ok(ApiResponse.<StaffDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .data(staff)
                .build());
    }

    @PutMapping("/me")
    @Operation(summary = "Cập nhật hồ sơ nhân viên đang đăng nhập")
    public ResponseEntity<ApiResponse<StaffDTO>> updateMyStaffProfile(@RequestBody StaffDTO staffDTO) {
        Integer staffId = currentStaffId();
        StaffDTO current = staffService.getStaffById(staffId);

        StaffDTO safePayload = StaffDTO.builder()
                .fullname(staffDTO != null ? staffDTO.getFullname() : null)
                .email(staffDTO != null ? staffDTO.getEmail() : null)
                .username(staffDTO != null ? staffDTO.getUsername() : null)
                .phone(staffDTO != null ? staffDTO.getPhone() : null)
                .birthday(staffDTO != null ? staffDTO.getBirthday() : null)
                .avatar(staffDTO != null ? staffDTO.getAvatar() : null)
                // Không cho tự đổi vai trò, trạng thái hoặc rạp qua hồ sơ cá nhân.
                .role(current.getRole())
                .status(current.getStatus())
                .cinemaId(current.getCinemaId())
                .build();

        StaffDTO updated = staffService.updateStaff(staffId, safePayload);
        return ResponseEntity.ok(ApiResponse.<StaffDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật thành công")
                .data(updated)
                .build());
    }

    @PutMapping("/me/password")
    @Operation(summary = "Đổi mật khẩu nhân viên đang đăng nhập")
    public ResponseEntity<ApiResponse<Void>> changeMyPassword(@Valid @RequestBody UserPasswordChangeRequest body) {
        staffService.changePassword(currentStaffId(), body.getCurrentPassword(), body.getNewPassword());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Đổi mật khẩu thành công")
                .build());
    }

    @GetMapping
    @Operation(summary = "Danh sách nhân viên (Admin dùng)")
    public ResponseEntity<ApiResponse<List<StaffDTO>>> getAllStaff(
            @RequestParam(required = false) Integer cinemaId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q) {
        Integer effectiveCinemaId = cinemaScopeService.effectiveCinemaId(cinemaId);
        String term = SearchUtils.pick(search, keyword, q);
        List<StaffDTO> staff = (effectiveCinemaId == null ? staffService.getAllStaff() : staffService.listStaffByCinema(effectiveCinemaId))
                .stream()
                .filter(s -> matchesSearch(term, s))
                .toList();
        return ResponseEntity.ok(ApiResponse.<List<StaffDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .data(staff)
                .build());
    }

    @GetMapping("/super-admin-view")
    @Operation(summary = "Danh sách nhân viên (Super Admin dùng)")
    public ResponseEntity<ApiResponse<List<StaffDTO>>> getStaffForSuperAdmin(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q) {
        String term = SearchUtils.pick(search, keyword, q);
        return ResponseEntity.ok(ApiResponse.<List<StaffDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .data(staffService.getAllStaffForSuperAdmin().stream()
                        .filter(s -> matchesSearch(term, s))
                        .toList())
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết nhân viên")
    public ResponseEntity<ApiResponse<StaffDTO>> getStaffById(@PathVariable Integer id) {
        StaffDTO staff = staffService.getStaffById(id);
        requireStaffScope(staff);
        return ResponseEntity.ok(ApiResponse.<StaffDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .data(staff)
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo nhân viên", description = "Dữ liệu avatar gửi kèm dạng Base64 string trong JSON.")
    public ResponseEntity<ApiResponse<StaffDTO>> createStaff(@Valid @RequestBody StaffDTO staffDTO) {
        requireWritableRole(staffDTO.getRole());
        cinemaScopeService.requireCinemaAccess(staffDTO.getCinemaId());
        requireWritableCinemaOperational(staffDTO.getCinemaId());
        StaffDTO created = staffService.createStaff(staffDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<StaffDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo nhân viên thành công")
                .data(created)
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật nhân viên")
    public ResponseEntity<ApiResponse<StaffDTO>> updateStaff(@PathVariable Integer id, @RequestBody StaffDTO staffDTO) {
        StaffDTO current = staffService.getStaffById(id);
        requireStaffScope(current);
        boolean selfProfileUpdate = isSelfProfileUpdate(id, current, staffDTO);
        if (!selfProfileUpdate) {
            requireWritableRole(staffDTO.getRole());
        }
        if (staffDTO.getCinemaId() != null) {
            cinemaScopeService.requireCinemaAccess(staffDTO.getCinemaId());
            requireWritableCinemaOperational(staffDTO.getCinemaId());
        } else if (!selfProfileUpdate) {
            requireWritableCinemaOperational(current.getCinemaId());
        }

        boolean hasChanges = false;

        if (staffDTO.getFullname() != null) hasChanges = true;
        if (staffDTO.getUsername() != null) hasChanges = true;
        if (staffDTO.getEmail() != null) hasChanges = true;
        if (staffDTO.getPhone() != null) hasChanges = true;
        if (staffDTO.getBirthday() != null) hasChanges = true;
        if (staffDTO.getStatus() != null) hasChanges = true;
        if (staffDTO.getAvatar() != null) hasChanges = true;
        if (staffDTO.getPassword() != null && !staffDTO.getPassword().trim().isEmpty()) hasChanges = true;
        if (staffDTO.getCinemaId() != null) hasChanges = true;

        if (!hasChanges) {
            return ResponseEntity.ok(ApiResponse.<StaffDTO>builder()
                    .status(HttpStatus.OK.value())
                    .message("Không có thay đổi để cập nhật")
                    .data(current)
                    .build());
        }

        StaffDTO updated = staffService.updateStaff(id, staffDTO);
        return ResponseEntity.ok(ApiResponse.<StaffDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật thành công")
                .data(updated)
                .build());
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@PathVariable Integer id,
            @Valid @RequestBody UserPasswordChangeRequest body) {
        staffService.changePassword(id, body.getCurrentPassword(), body.getNewPassword());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Đổi mật khẩu thành công")
                .build());
    }

    @DeleteMapping("/{id}")
    // [SUPER ADMIN ONLY] - This section belongs to Super Admin. Do not modify without authorization.
    public ResponseEntity<ApiResponse<Void>> deleteStaff(@PathVariable Integer id) {
        staffService.deleteStaff(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa thành công")
                .build());
    }

    private void requireStaffScope(StaffDTO staff) {
        if (staff == null || staff.getCinemaId() == null) {
            if (!cinemaScopeService.isSuperAdmin()) {
                throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Không có quyền thao tác nhân sự chưa gán rạp");
            }
            return;
        }
        cinemaScopeService.requireCinemaAccess(staff.getCinemaId());
    }

    private void requireWritableRole(String role) {
        if (cinemaScopeService.isSuperAdmin()) {
            return;
        }
        String normalized = role == null ? "" : role.trim().toUpperCase().replaceFirst("^ROLE_", "");
        if ("ADMIN".equals(normalized) || "SUPER_ADMIN".equals(normalized)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Quản trị rạp chỉ được quản lý nhân viên STAFF");
        }
    }

    private void requireWritableCinemaOperational(Integer cinemaId) {
        if (cinemaScopeService.isSuperAdmin() || cinemaId == null) {
            return;
        }
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy rạp với mã: " + cinemaId));
        cinemaScopeService.requireCinemaOperational(cinema);
    }

    private boolean isSelfProfileUpdate(Integer id, StaffDTO current, StaffDTO requested) {
        if (id == null || current == null || requested == null || cinemaScopeService.isSuperAdmin()) {
            return false;
        }
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)
                || details.getStaff() == null
                || !id.equals(details.getStaff().getStaffId())) {
            return false;
        }

        String currentRole = normalizeRole(current.getRole());
        String requestedRole = requested.getRole() == null ? currentRole : normalizeRole(requested.getRole());
        Integer currentCinemaId = current.getCinemaId();
        Integer requestedCinemaId = requested.getCinemaId() == null ? currentCinemaId : requested.getCinemaId();
        Integer currentStatus = current.getStatus();
        Integer requestedStatus = requested.getStatus() == null ? currentStatus : requested.getStatus();

        return currentRole.equals(requestedRole)
                && java.util.Objects.equals(currentCinemaId, requestedCinemaId)
                && java.util.Objects.equals(currentStatus, requestedStatus);
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase().replaceFirst("^ROLE_", "");
    }

    private boolean matchesSearch(String term, StaffDTO staff) {
        return SearchUtils.matches(term,
                staff.getStaffId(), staff.getFullname(), staff.getUsername(), staff.getEmail(),
                staff.getPhone(), staff.getRole(), staff.getStatus(), staff.getCinemaId(), staff.getCinemaName());
    }

    private Integer currentStaffId() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)
                || details.getStaff() == null
                || details.getStaff().getStaffId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Chưa đăng nhập nhân sự");
        }
        return details.getStaff().getStaffId();
    }
}
