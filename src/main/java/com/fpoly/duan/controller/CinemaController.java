package com.fpoly.duan.controller;

import java.util.List;
import java.util.stream.Collectors;

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
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.CinemaDTO;
import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.repository.CinemaRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/cinemas")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "1b. Rạp (Cinemas)", description = "Danh sách rạp — FE Super Admin chọn rạp.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class CinemaController {

    private final CinemaRepository cinemaRepository;

    @GetMapping
    @Operation(summary = "Danh sách rạp")
    public ResponseEntity<ApiResponse<List<CinemaDTO>>> list() {
        List<CinemaDTO> data = cinemaRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.<List<CinemaDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách rạp thành công")
                .data(data)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết rạp")
    public ResponseEntity<ApiResponse<CinemaDTO>> getById(@PathVariable Integer id) {
        Cinema c = cinemaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy rạp với id: " + id));
        return ResponseEntity.ok(ApiResponse.<CinemaDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy thông tin rạp thành công")
                .data(toDTO(c))
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo rạp")
    public ResponseEntity<ApiResponse<CinemaDTO>> create(@RequestBody CinemaDTO dto) {
        if (dto == null || dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new RuntimeException("Tên rạp không được để trống");
        }
        Cinema c = new Cinema();
        c.setName(dto.getName().trim());
        c.setAddress(dto.getAddress());
        c.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        Cinema saved = cinemaRepository.save(c);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<CinemaDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo rạp thành công")
                .data(toDTO(saved))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật rạp")
    public ResponseEntity<ApiResponse<CinemaDTO>> update(@PathVariable Integer id, @RequestBody CinemaDTO dto) {
        Cinema c = cinemaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy rạp với id: " + id));
        if (dto.getName() != null) c.setName(dto.getName().trim());
        if (dto.getAddress() != null) c.setAddress(dto.getAddress());
        if (dto.getStatus() != null) c.setStatus(dto.getStatus());
        Cinema saved = cinemaRepository.save(c);
        return ResponseEntity.ok(ApiResponse.<CinemaDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật rạp thành công")
                .data(toDTO(saved))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa rạp")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        if (!cinemaRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy rạp với id: " + id);
        }
        cinemaRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa rạp thành công")
                .build());
    }

    private CinemaDTO toDTO(Cinema c) {
        return CinemaDTO.builder()
                .cinemaId(c.getCinemaId())
                .name(c.getName())
                .address(c.getAddress())
                .status(c.getStatus())
                .build();
    }
}
