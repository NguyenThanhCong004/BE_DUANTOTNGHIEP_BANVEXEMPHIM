package com.fpoly.duan.controller;

import com.fpoly.duan.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/counter-orders/export-pdf")
@Tag(name = "12. Đặt vé tại quầy (POS)")
@CrossOrigin(origins = "*", maxAge = 3600)
public class TicketExportController {

    @Value("${app.ticket-export-dir:TICKETS_EXPORT}")
    private String ticketExportDir;

    @PostMapping
    @Operation(summary = "Lưu file vé PDF vào thư mục dự án (Để preview)")
    public ResponseEntity<ApiResponse<String>> exportPdf(@RequestBody Map<String, String> body) {
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
}
