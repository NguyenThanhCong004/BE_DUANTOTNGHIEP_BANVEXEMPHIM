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
import com.fpoly.duan.dto.CategoryProductDTO;
import com.fpoly.duan.entity.CategoryProduct;
import com.fpoly.duan.repository.CategoryProductRepository;
import com.fpoly.duan.repository.ProductRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/product-categories")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "8e. Loại sản phẩm", description = "CRUD danh mục đồ ăn — Super Admin.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class CategoryProductController {

    private final CategoryProductRepository categoryProductRepository;
    private final ProductRepository productRepository;

    @GetMapping
    @Operation(summary = "Danh sách loại sản phẩm")
    public ResponseEntity<ApiResponse<List<CategoryProductDTO>>> list() {
        List<CategoryProductDTO> data = categoryProductRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.<List<CategoryProductDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(data)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết loại")
    public ResponseEntity<ApiResponse<CategoryProductDTO>> getById(@PathVariable Integer id) {
        CategoryProduct c = categoryProductRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại với id: " + id));
        return ResponseEntity.ok(ApiResponse.<CategoryProductDTO>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(toDTO(c))
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo loại")
    public ResponseEntity<ApiResponse<CategoryProductDTO>> create(@RequestBody CategoryProductDTO dto) {
        if (dto == null || dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new RuntimeException("Tên loại không được để trống");
        }
        CategoryProduct c = new CategoryProduct();
        c.setName(dto.getName().trim());
        CategoryProduct saved = categoryProductRepository.save(c);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<CategoryProductDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo loại thành công")
                .data(toDTO(saved))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật loại")
    public ResponseEntity<ApiResponse<CategoryProductDTO>> update(@PathVariable Integer id,
            @RequestBody CategoryProductDTO dto) {
        CategoryProduct c = categoryProductRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại với id: " + id));
        if (dto != null && dto.getName() != null && !dto.getName().trim().isEmpty()) {
            c.setName(dto.getName().trim());
        }
        CategoryProduct saved = categoryProductRepository.save(c);
        return ResponseEntity.ok(ApiResponse.<CategoryProductDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật thành công")
                .data(toDTO(saved))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa loại")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        if (!categoryProductRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy loại với id: " + id);
        }
        if (!productRepository.findByCategory_CategoryProductId(id).isEmpty()) {
            throw new RuntimeException("Không thể xóa: còn sản phẩm thuộc loại này");
        }
        categoryProductRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa loại thành công")
                .build());
    }

    private CategoryProductDTO toDTO(CategoryProduct c) {
        return CategoryProductDTO.builder()
                .id(c.getCategoryProductId())
                .name(c.getName())
                .build();
    }
}
