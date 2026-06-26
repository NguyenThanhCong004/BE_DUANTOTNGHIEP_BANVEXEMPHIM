package com.fpoly.duan.controller;

import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.ReferenceCatalogDTO;
import com.fpoly.duan.entity.Author;
import com.fpoly.duan.entity.Nation;
import com.fpoly.duan.repository.AuthorRepository;
import com.fpoly.duan.repository.MovieRepository;
import com.fpoly.duan.repository.NationRepository;
import com.fpoly.duan.util.SearchUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

/** Danh mục tác giả và quốc gia dùng chung bởi form quản lý phim. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
public class MovieReferenceController {
    private final AuthorRepository authorRepository;
    private final NationRepository nationRepository;
    private final MovieRepository movieRepository;

    @GetMapping("/authors")
    public ResponseEntity<ApiResponse<List<ReferenceCatalogDTO>>> authors(@RequestParam(required = false) String search) {
        return listAuthors(search, "Lấy danh sách tác giả thành công");
    }

    @PostMapping("/authors")
    public ResponseEntity<ApiResponse<ReferenceCatalogDTO>> createAuthor(@Valid @RequestBody ReferenceCatalogDTO dto) {
        String name = clean(dto.getName());
        if (authorRepository.existsByNameIgnoreCase(name)) throw conflict("Tác giả đã tồn tại");
        Author author = new Author(); author.setName(name); author.setStatus(status(dto.getStatus()));
        return created(toDto(authorRepository.save(author)), "Tạo tác giả thành công");
    }

    @PutMapping("/authors/{id}")
    public ResponseEntity<ApiResponse<ReferenceCatalogDTO>> updateAuthor(@PathVariable Integer id, @Valid @RequestBody ReferenceCatalogDTO dto) {
        Author author = authorRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy tác giả"));
        String name = clean(dto.getName());
        authorRepository.findByNameIgnoreCase(name).filter(found -> !found.getAuthorId().equals(id))
                .ifPresent(found -> { throw conflict("Tác giả đã tồn tại"); });
        author.setName(name); author.setStatus(status(dto.getStatus()));
        return ok(toDto(authorRepository.save(author)), "Cập nhật tác giả thành công");
    }

    @DeleteMapping("/authors/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAuthor(@PathVariable Integer id) {
        Author author = authorRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy tác giả"));
        if (movieRepository.existsByAuthorIgnoreCase(author.getName())) throw conflict("Không thể xóa tác giả đang được phim sử dụng");
        authorRepository.delete(author);
        return ResponseEntity.ok(ApiResponse.<Void>builder().status(200).message("Xóa tác giả thành công").build());
    }

    @GetMapping("/nations")
    public ResponseEntity<ApiResponse<List<ReferenceCatalogDTO>>> nations(@RequestParam(required = false) String search) {
        List<ReferenceCatalogDTO> data = nationRepository.findAll().stream()
                .filter(n -> SearchUtils.matches(search, n.getNationId(), n.getName(), n.getStatus()))
                .map(this::toDto).sorted(Comparator.comparing(ReferenceCatalogDTO::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        return ResponseEntity.ok(ApiResponse.<List<ReferenceCatalogDTO>>builder().status(200).message("Lấy danh sách quốc gia thành công").data(data).build());
    }

    @PostMapping("/nations")
    public ResponseEntity<ApiResponse<ReferenceCatalogDTO>> createNation(@Valid @RequestBody ReferenceCatalogDTO dto) {
        String name = clean(dto.getName());
        if (nationRepository.existsByNameIgnoreCase(name)) throw conflict("Quốc gia đã tồn tại");
        Nation nation = new Nation(); nation.setName(name); nation.setStatus(status(dto.getStatus()));
        return created(toDto(nationRepository.save(nation)), "Tạo quốc gia thành công");
    }

    @PutMapping("/nations/{id}")
    public ResponseEntity<ApiResponse<ReferenceCatalogDTO>> updateNation(@PathVariable Integer id, @Valid @RequestBody ReferenceCatalogDTO dto) {
        Nation nation = nationRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy quốc gia"));
        String name = clean(dto.getName());
        nationRepository.findByNameIgnoreCase(name).filter(found -> !found.getNationId().equals(id))
                .ifPresent(found -> { throw conflict("Quốc gia đã tồn tại"); });
        nation.setName(name); nation.setStatus(status(dto.getStatus()));
        return ok(toDto(nationRepository.save(nation)), "Cập nhật quốc gia thành công");
    }

    @DeleteMapping("/nations/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNation(@PathVariable Integer id) {
        Nation nation = nationRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy quốc gia"));
        if (movieRepository.existsByNationIgnoreCase(nation.getName())) throw conflict("Không thể xóa quốc gia đang được phim sử dụng");
        nationRepository.delete(nation);
        return ResponseEntity.ok(ApiResponse.<Void>builder().status(200).message("Xóa quốc gia thành công").build());
    }

    private ResponseEntity<ApiResponse<List<ReferenceCatalogDTO>>> listAuthors(String search, String message) {
        List<ReferenceCatalogDTO> data = authorRepository.findAll().stream()
                .filter(a -> SearchUtils.matches(search, a.getAuthorId(), a.getName(), a.getStatus()))
                .map(this::toDto).sorted(Comparator.comparing(ReferenceCatalogDTO::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        return ResponseEntity.ok(ApiResponse.<List<ReferenceCatalogDTO>>builder().status(200).message(message).data(data).build());
    }
    private ReferenceCatalogDTO toDto(Author item) { return ReferenceCatalogDTO.builder().id(item.getAuthorId()).name(item.getName()).status(item.getStatus()).build(); }
    private ReferenceCatalogDTO toDto(Nation item) { return ReferenceCatalogDTO.builder().id(item.getNationId()).name(item.getName()).status(item.getStatus()).build(); }
    private static String clean(String name) { return name == null ? "" : name.trim(); }
    private static int status(Integer status) { return status != null && status == 0 ? 0 : 1; }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static ResponseEntity<ApiResponse<ReferenceCatalogDTO>> ok(ReferenceCatalogDTO data, String message) { return ResponseEntity.ok(ApiResponse.<ReferenceCatalogDTO>builder().status(200).message(message).data(data).build()); }
    private static ResponseEntity<ApiResponse<ReferenceCatalogDTO>> created(ReferenceCatalogDTO data, String message) { return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ReferenceCatalogDTO>builder().status(201).message(message).data(data).build()); }
}
