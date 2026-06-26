package com.fpoly.duan.controller;

import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.entity.StaffShift;
import com.fpoly.duan.repository.StaffShiftRepository;
import com.fpoly.duan.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/counter-orders/export-pdf")
@Tag(name = "12. Đặt vé tại quầy (POS)")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class TicketExportController {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final StaffShiftRepository staffShiftRepository;

    @Value("${app.ticket-export-dir:TICKETS_EXPORT}")
    private String ticketExportDir;

    @PostMapping
    @Operation(summary = "Lưu file vé PDF vào thư mục dự án (Để preview)")
    public ResponseEntity<ApiResponse<String>> exportPdf(Authentication authentication, @RequestBody Map<String, String> body) {
        requireSalesShift(authentication);
        try {
            String base64Pdf = body.get("pdfBase64");
            String fileName = body.get("fileName");

            if (base64Pdf == null || fileName == null) {
                return ResponseEntity.badRequest().body(ApiResponse.<String>builder().message("Thiếu dữ liệu").build());
            }

            Path exportDir = Paths.get(ticketExportDir).toAbsolutePath().normalize();
            Files.createDirectories(exportDir);

            String safeFileName = Paths.get(fileName).getFileName().toString();
            Path target = exportDir.resolve(safeFileName).normalize();
            if (!target.startsWith(exportDir)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.<String>builder().message("Tên file không hợp lệ").build());
            }

            byte[] pdfBytes = Base64.getDecoder().decode(base64Pdf);
            Files.write(target, pdfBytes);

            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .message("Đã lưu vé vào thư mục " + target)
                    .data(target.toString())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.<String>builder().message("Lỗi lưu file: " + e.getMessage()).build());
        }
    }

    private void requireSalesShift(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        if (details.getStaff() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ nhân viên mới được in vé tại quầy");
        }
        Integer staffId = details.getStaff().getStaffId();
        StaffShift activeShift = staffShiftRepository.findActiveShift(staffId, LocalDateTime.now(APP_ZONE))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Bạn chưa trong ca làm việc, không thể in vé tại quầy"));
        if (!isSalesShift(activeShift.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chỉ nhân viên đang trong ca Bán vé mới được in vé tại quầy");
        }
    }

    private boolean isSalesShift(String role) {
        String normalized = normalize(role);
        return "ban ve".equals(normalized)
                || "ban hang".equals(normalized)
                || "sales".equals(normalized)
                || "cashier".equals(normalized);
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
