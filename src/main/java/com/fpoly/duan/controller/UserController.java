package com.fpoly.duan.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.UserDTO;
import com.fpoly.duan.dto.UserPasswordChangeRequest;
import com.fpoly.duan.dto.UserRequest;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.service.UserService;
import com.fpoly.duan.util.SearchUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "2. Người dùng (Users)", description = """
        CRUD khách hàng. FE: `Profile.jsx`, `MembershipStatus.jsx`, `PointsHistory.jsx`, `super-admin/Users.jsx`.
        JSON dùng **camelCase** (`userId`, không phải `user_id`).
        """)
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Danh sách tất cả user")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserDTO>>> getAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q) {
        String term = SearchUtils.pick(search, keyword, q);
        List<UserDTO> users = userService.getAllUsers().stream()
                .filter(u -> SearchUtils.matches(term,
                        u.getUserId(), u.getUsername(), u.getFullname(), u.getEmail(), u.getPhone(),
                        u.getStatus(), u.getRankName(), u.getPoints(), u.getTotalSpending()))
                .toList();
        return ResponseEntity.ok(ApiResponse.<List<UserDTO>>builder()
                .status(200)
                .message("Lấy danh sách người dùng thành công")
                .data(users)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết user theo id", description = "Khớp với `GET /api/v1/users/{id}` mà FE gọi sau khi decode JWT.")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN') or (hasAuthority('ROLE_USER') and #id == principal.userId)")
    public ResponseEntity<ApiResponse<UserDTO>> getUserById(@PathVariable Integer id) {
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.<UserDTO>builder()
                .status(200)
                .message("Lấy thông tin người dùng thành công")
                .data(user)
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo user (Super Admin / nội bộ)")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserDTO>> createUser(@Valid @RequestBody UserRequest userRequest) {
        UserDTO userDTO = UserDTO.builder()
                .username(userRequest.getUsername())
                .fullname(userRequest.getFullname())
                .email(userRequest.getEmail())
                .phone(userRequest.getPhone())
                .birthday(userRequest.getBirthday())
                .avatar(userRequest.getAvatar())
                .rankId(userRequest.getRankId())
                .build();
        UserDTO createdUser = userService.createUser(userDTO, userRequest.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<UserDTO>builder()
                .status(201)
                .message("Tạo người dùng thành công")
                .data(createdUser)
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật user")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN') or (hasAuthority('ROLE_USER') and #id == principal.userId)")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(@PathVariable Integer id, @RequestBody UserDTO userDTO) {
        if (isCustomerSelfAccess(id) && !hasAdminAuthority()) {
            userDTO = selfEditableUserFields(userDTO);
        }

        // Kiểm tra xem có dữ liệu cập nhật không
        boolean hasChanges = hasEditableChanges(userDTO);
        
        // Nếu không có thay đổi nào, trả về thông báo phù hợp
        if (!hasChanges) {
            UserDTO current = userService.getUserById(id);
            return ResponseEntity.ok(ApiResponse.<UserDTO>builder()
                    .status(200)
                    .message("Không có thay đổi để cập nhật")
                    .data(current)
                    .build());
        }
        
        UserDTO updatedUser = userService.updateUser(id, userDTO);
        return ResponseEntity.ok(ApiResponse.<UserDTO>builder()
                .status(200)
                .message("Cập nhật người dùng thành công")
                .data(updatedUser)
                .build());
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "Đổi mật khẩu (khách)", description = "Body: currentPassword, newPassword — dùng cho trang Hồ sơ.")
    @PreAuthorize("hasAuthority('ROLE_USER') and #id == principal.userId")
    public ResponseEntity<ApiResponse<Void>> changePassword(@PathVariable Integer id,
            @Valid @RequestBody UserPasswordChangeRequest body) {
        userService.changePassword(id, body.getCurrentPassword(), body.getNewPassword());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(200)
                .message("Đổi mật khẩu thành công")
                .build());
    }

    private boolean hasEditableChanges(UserDTO userDTO) {
        if (userDTO == null) {
            return false;
        }
        return userDTO.getFullname() != null
                || userDTO.getEmail() != null
                || userDTO.getPhone() != null
                || userDTO.getBirthday() != null
                || userDTO.getStatus() != null
                || userDTO.getAvatar() != null
                || userDTO.getRankId() != null
                || userDTO.getPoints() != null
                || userDTO.getTotalSpending() != null;
    }

    private UserDTO selfEditableUserFields(UserDTO userDTO) {
        if (userDTO == null) {
            return UserDTO.builder().build();
        }
        return UserDTO.builder()
                .fullname(userDTO.getFullname())
                .email(userDTO.getEmail())
                .phone(userDTO.getPhone())
                .birthday(userDTO.getBirthday())
                .avatar(userDTO.getAvatar())
                .build();
    }

    private boolean isCustomerSelfAccess(Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            return false;
        }
        return details.getUser() != null && id != null && id.equals(details.getUser().getUserId());
    }

    private boolean hasAdminAuthority() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_ADMIN".equals(a) || "ROLE_SUPER_ADMIN".equals(a));
    }

}
