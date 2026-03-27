package com.fpoly.duan.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
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
import com.fpoly.duan.dto.GenreDTO;
import com.fpoly.duan.entity.Genre;
import com.fpoly.duan.entity.Movie;
import com.fpoly.duan.repository.GenreRepository;
import com.fpoly.duan.repository.MovieRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/genres")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "1c. Thể loại phim (Genres)", description = "Danh sách thể loại — FE Super Admin.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class GenreController {

    private final GenreRepository genreRepository;
    private final MovieRepository movieRepository;

    @GetMapping
    @Operation(summary = "Danh sách thể loại")
    public ResponseEntity<ApiResponse<List<GenreDTO>>> list() {
        List<GenreDTO> data = genreRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.<List<GenreDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách thể loại thành công")
                .data(data)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết thể loại")
    public ResponseEntity<ApiResponse<GenreDTO>> getById(@PathVariable @NonNull Integer id) {
        Genre g = genreRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thể loại với id: " + id));
        return ResponseEntity.ok(ApiResponse.<GenreDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy thông tin thể loại thành công")
                .data(toDTO(g))
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo thể loại")
    public ResponseEntity<ApiResponse<GenreDTO>> create(@RequestBody GenreDTO dto) {
        if (dto == null || dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new RuntimeException("Tên thể loại không được để trống");
        }
        Genre g = new Genre();
        g.setName(dto.getName().trim());
        Genre saved = genreRepository.save(g);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<GenreDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo thể loại thành công")
                .data(toDTO(saved))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thể loại")
    public ResponseEntity<ApiResponse<GenreDTO>> update(@PathVariable @NonNull Integer id, @RequestBody GenreDTO dto) {
        Genre g = genreRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thể loại với id: " + id));
        if (dto != null && dto.getName() != null && !dto.getName().trim().isEmpty()) {
            g.setName(dto.getName().trim());
        }
        Genre saved = genreRepository.save(g);
        return ResponseEntity.ok(ApiResponse.<GenreDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật thể loại thành công")
                .data(toDTO(saved))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa thể loại")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable @NonNull Integer id) {
        if (!genreRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy thể loại với id: " + id);
        }
        
        // Kiểm tra xem có phim nào đang sử dụng thể loại này không
        List<Movie> moviesWithGenre = movieRepository.findByGenre_GenreId(id);
        if (!moviesWithGenre.isEmpty()) {
            throw new RuntimeException("Không thể xóa thể loại này vì đang có " + moviesWithGenre.size() + 
                " phim sử dụng. Vui lòng xóa hoặc thay đổi thể loại của các phim trước.");
        }
        
        genreRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa thể loại thành công")
                .build());
    }

    private GenreDTO toDTO(Genre g) {
        return GenreDTO.builder()
                .genreId(g.getGenreId())
                .name(g.getName())
                .build();
    }
}
