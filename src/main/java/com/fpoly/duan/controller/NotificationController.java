package com.fpoly.duan.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.CreateNotificationRequest;
import com.fpoly.duan.dto.NotificationDTO;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.service.CinemaScopeService;
import com.fpoly.duan.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Thông báo nội bộ", description = "Thông báo hiển thị trên Dashboard Admin/Super Admin.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class NotificationController {

    private final NotificationService notificationService;
    private final CinemaScopeService cinemaScopeService;

    @GetMapping
    @Operation(summary = "Danh sách thông báo nội bộ (theo rạp đang thao tác)")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<NotificationDTO>>> list(
            @RequestParam(required = false) Integer cinemaId,
            @RequestParam(required = false) Integer limit) {
        Integer effectiveCinemaId = cinemaScopeService.effectiveCinemaId(cinemaId);
        return ResponseEntity.ok(ApiResponse.<List<NotificationDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách thông báo thành công")
                .data(notificationService.listForCinema(effectiveCinemaId, limit))
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo thông báo nội bộ (Super Admin)")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<NotificationDTO>> create(
            Authentication authentication,
            @Valid @RequestBody CreateNotificationRequest request) {
        Integer createdByStaffId = null;
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details
                && details.getStaff() != null) {
            createdByStaffId = details.getStaff().getStaffId();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<NotificationDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo thông báo thành công")
                .data(notificationService.create(request, createdByStaffId))
                .build());
    }
}
