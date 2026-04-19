package com.fpoly.duan.controller;

import com.fpoly.duan.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.FileOutputStream;
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

    @PostMapping
    @Operation(summary = "Lưu file vé PDF vào thư mục dự án (Để preview)")
    public ResponseEntity<ApiResponse<String>> exportPdf(@RequestBody Map<String, String> body) {
        try {
            String base64Pdf = body.get("pdfBase64");
            String fileName = body.get("fileName");

            if (base64Pdf == null || fileName == null) {
                return ResponseEntity.badRequest().body(ApiResponse.<String>builder().message("Thiếu dữ liệu").build());
            }

            // Tạo thư mục TICKETS_EXPORT ở root dự án nếu chưa có
            Path path = Paths.get("TICKETS_EXPORT");
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }

            byte[] pdfBytes = Base64.getDecoder().decode(base64Pdf);
            try (FileOutputStream fos = new FileOutputStream("TICKETS_EXPORT/" + fileName)) {
                fos.write(pdfBytes);
            }

            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .message("Đã lưu vé vào thư mục TICKETS_EXPORT/" + fileName)
                    .data("TICKETS_EXPORT/" + fileName)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.<String>builder().message("Lỗi lưu file: " + e.getMessage()).build());
        }
    }
}
