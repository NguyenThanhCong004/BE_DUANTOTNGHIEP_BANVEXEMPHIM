package com.fpoly.duan.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@Tag(name = "3. Nhân viên (Staff)", description = "CRUD nhân viên — FE: `AdminShiftForm.jsx` load danh sách staff.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class StaffController {
    private final StaffService staffService;

    @GetMapping
    @Operation(summary = "Danh sách nhân viên", description = "Query cinemaId (tùy chọn): lọc theo rạp + nhân viên chưa gán rạp.")
    public ResponseEntity<ApiResponse<List<StaffDTO>>> getAllStaff(
            @RequestParam(required = false) Integer cinemaId) {
        List<StaffDTO> staff = cinemaId == null ? staffService.getAllStaff() : staffService.listStaffByCinema(cinemaId);
        return ResponseEntity.ok(ApiResponse.<List<StaffDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách nhân viên thành công")
                .data(staff)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết nhân viên")
    public ResponseEntity<ApiResponse<StaffDTO>> getStaffById(@PathVariable Integer id) {
        StaffDTO staff = staffService.getStaffById(id);
        return ResponseEntity.ok(ApiResponse.<StaffDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy thông tin nhân viên thành công")
                .data(staff)
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo nhân viên")
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
        StaffDTO updated = staffService.updateStaff(id, staffDTO);
        return ResponseEntity.ok(ApiResponse.<StaffDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật nhân viên thành công")
                .data(updated)
                .build());
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "Đổi mật khẩu (nhân viên)", description = "Chỉ tài khoản đang đăng nhập được đổi mật khẩu của chính mình (staffId khớp JWT).")
    public ResponseEntity<ApiResponse<Void>> changePassword(@PathVariable Integer id,
            @RequestBody UserPasswordChangeRequest body) {
        if (body == null || body.getNewPassword() == null) {
            throw new RuntimeException("Thiếu dữ liệu đổi mật khẩu");
        }
        staffService.changePassword(id, body.getCurrentPassword(), body.getNewPassword());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Đổi mật khẩu thành công")
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa nhân viên")
    public ResponseEntity<ApiResponse<Void>> deleteStaff(@PathVariable Integer id) {
        staffService.deleteStaff(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa nhân viên thành công")
                .build());
    }
}

