package com.fpoly.duan.controller;



import java.util.List;



import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;



import com.fpoly.duan.config.OpenApiConfig;

import com.fpoly.duan.dto.ApiResponse;

import com.fpoly.duan.dto.StaffDTO;

import com.fpoly.duan.dto.UserPasswordChangeRequest;

import com.fpoly.duan.service.StaffService;



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



    @GetMapping

    @Operation(summary = "Danh sách nhân viên (Admin dùng)")

    public ResponseEntity<ApiResponse<List<StaffDTO>>> getAllStaff(

            @RequestParam(required = false) Integer cinemaId) {

        List<StaffDTO> staff = cinemaId == null ? staffService.getAllStaff() : staffService.listStaffByCinema(cinemaId);

        return ResponseEntity.ok(ApiResponse.<List<StaffDTO>>builder()

                .status(HttpStatus.OK.value())

                .message("Thành công")

                .data(staff)

                .build());

    }



    @GetMapping("/super-admin-view")

    @Operation(summary = "Danh sách nhân viên (Super Admin dùng)")

    public ResponseEntity<ApiResponse<List<StaffDTO>>> getStaffForSuperAdmin() {

        return ResponseEntity.ok(ApiResponse.<List<StaffDTO>>builder()

                .status(HttpStatus.OK.value())

                .message("Thành công")

                .data(staffService.getAllStaffForSuperAdmin())

                .build());

    }



    @GetMapping("/{id}")

    @Operation(summary = "Chi tiết nhân viên")

    public ResponseEntity<ApiResponse<StaffDTO>> getStaffById(@PathVariable Integer id) {

        StaffDTO staff = staffService.getStaffById(id);

        return ResponseEntity.ok(ApiResponse.<StaffDTO>builder()

                .status(HttpStatus.OK.value())

                .message("Thành công")

                .data(staff)

                .build());

    }



    @PostMapping

    @Operation(summary = "Tạo nhân viên", description = "Dữ liệu avatar gửi kèm dạng Base64 string trong JSON.")

    public ResponseEntity<ApiResponse<StaffDTO>> createStaff(@RequestBody StaffDTO staffDTO) {

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

        // Kiểm tra xem có dữ liệu cập nhật không

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

        

        // Nếu không có thay đổi nào, trả về thông báo phù hợp

        if (!hasChanges) {

            StaffDTO current = staffService.getStaffById(id);

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

            @RequestBody UserPasswordChangeRequest body) {

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

}

