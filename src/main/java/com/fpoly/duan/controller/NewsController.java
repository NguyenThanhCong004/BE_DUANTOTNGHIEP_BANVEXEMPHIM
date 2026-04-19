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
import com.fpoly.duan.dto.NewsDTO;
import com.fpoly.duan.entity.News;
import com.fpoly.duan.repository.NewsRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "8c. Tin tức", description = "CRUD tin tức — Super Admin.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
// [SUPER ADMIN ONLY] - This section belongs to Super Admin. Do not modify without authorization.
public class NewsController {

    private final NewsRepository newsRepository;

    @GetMapping
    @Operation(summary = "Danh sách tin tức")
    public ResponseEntity<ApiResponse<List<NewsDTO>>> list() {
        List<NewsDTO> data = newsRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.<List<NewsDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách tin tức thành công")
                .data(data)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết tin")
    public ResponseEntity<ApiResponse<NewsDTO>> getById(@PathVariable Integer id) {
        News n = newsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tin với id: " + id));
        return ResponseEntity.ok(ApiResponse.<NewsDTO>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(toDTO(n))
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo tin")
    public ResponseEntity<ApiResponse<NewsDTO>> create(@RequestBody NewsDTO dto) {
        validate(dto, true);
        News n = fromDTO(new News(), dto);
        News saved = newsRepository.save(n);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<NewsDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo tin thành công")
                .data(toDTO(saved))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật tin")
    public ResponseEntity<ApiResponse<NewsDTO>> update(@PathVariable Integer id, @RequestBody NewsDTO dto) {
        News n = newsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tin với id: " + id));
        validate(dto, false);

        boolean hasChanges = false;
        if (dto.getTitle() != null && !dto.getTitle().trim().equals(n.getTitle())) hasChanges = true;
        if (dto.getContent() != null && !dto.getContent().equals(n.getContent())) hasChanges = true;
        if (dto.getImage() != null && !dto.getImage().trim().isEmpty() && !dto.getImage().trim().equals(n.getImage())) hasChanges = true;
        if (dto.getStatus() != null && !dto.getStatus().equals(n.getStatus())) hasChanges = true;

        if (!hasChanges) {
            return ResponseEntity.ok(ApiResponse.<NewsDTO>builder()
                    .status(HttpStatus.OK.value())
                    .message("Không có thay đổi để cập nhật")
                    .data(toDTO(n))
                    .build());
        }

        News saved = newsRepository.save(fromDTO(n, dto));
        return ResponseEntity.ok(ApiResponse.<NewsDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật tin thành công")
                .data(toDTO(saved))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa tin")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        if (!newsRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy tin với id: " + id);
        }
        newsRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa tin thành công")
                .build());
    }

    private void validate(NewsDTO dto, boolean isCreate) {
        if (dto == null) {
            throw new RuntimeException("Dữ liệu không hợp lệ");
        }
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new RuntimeException("Tiêu đề không được để trống");
        }
        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            throw new RuntimeException("Nội dung không được để trống");
        }
        if (isCreate && (dto.getImage() == null || dto.getImage().trim().isEmpty())) {
            throw new RuntimeException("Ảnh minh họa không được để trống");
        }
    }

    private NewsDTO toDTO(News n) {
        return NewsDTO.builder()
                .id(n.getNewsId())
                .title(n.getTitle())
                .content(n.getContent())
                .image(n.getImage())
                .status(n.getStatus() != null ? n.getStatus() : 1)
                .createdAt(n.getCreatedAt())
                .build();
    }

    private News fromDTO(News n, NewsDTO dto) {
        n.setTitle(dto.getTitle() != null ? dto.getTitle().trim() : n.getTitle());
        n.setContent(dto.getContent() != null ? dto.getContent() : n.getContent());
        if (dto.getImage() != null && !dto.getImage().trim().isEmpty()) {
            n.setImage(dto.getImage().trim());
        }
        n.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        return n;
    }
}
