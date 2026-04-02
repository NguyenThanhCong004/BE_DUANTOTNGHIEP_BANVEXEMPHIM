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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.ProductDTO;
import com.fpoly.duan.entity.CategoryProduct;
import com.fpoly.duan.entity.Product;
import com.fpoly.duan.repository.CategoryProductRepository;
import com.fpoly.duan.repository.CinemaProductRepository;
import com.fpoly.duan.repository.OrderDetailFoodRepository;
import com.fpoly.duan.repository.ProductRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "8f. Sản phẩm (đồ ăn)", description = "CRUD sản phẩm — Super Admin.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
// [SUPER ADMIN ONLY] - This section belongs to Super Admin. Do not modify without authorization.
public class ProductController {

    private final ProductRepository productRepository;
    private final CategoryProductRepository categoryProductRepository;
    private final OrderDetailFoodRepository orderDetailFoodRepository;
    private final CinemaProductRepository cinemaProductRepository;

    @GetMapping
    @Operation(summary = "Danh sách sản phẩm")
    public ResponseEntity<ApiResponse<List<ProductDTO>>> list(
            @RequestParam(required = false) Integer categoryId) {
        List<Product> list = categoryId != null
                ? productRepository.findByCategory_CategoryProductId(categoryId)
                : productRepository.findAll();
        List<ProductDTO> data = list.stream().map(this::toDTO).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.<List<ProductDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(data)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết sản phẩm")
    public ResponseEntity<ApiResponse<ProductDTO>> getById(@PathVariable Integer id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với id: " + id));
        return ResponseEntity.ok(ApiResponse.<ProductDTO>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(toDTO(p))
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo sản phẩm")
    public ResponseEntity<ApiResponse<ProductDTO>> create(@RequestBody ProductDTO dto) {
        validate(dto, true);
        Product p = fromDTO(new Product(), dto);
        Product saved = productRepository.save(p);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ProductDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo sản phẩm thành công")
                .data(toDTO(saved))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật sản phẩm")
    public ResponseEntity<ApiResponse<ProductDTO>> update(@PathVariable Integer id, @RequestBody ProductDTO dto) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với id: " + id));
        validate(dto, false);
        Product saved = productRepository.save(fromDTO(p, dto));
        return ResponseEntity.ok(ApiResponse.<ProductDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật thành công")
                .data(toDTO(saved))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa sản phẩm")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        if (!productRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy sản phẩm với id: " + id);
        }

        // Kiểm tra xem sản phẩm đã từng bán (có trong hóa đơn) chưa
        if (orderDetailFoodRepository.existsByProduct_ProductId(id)) {
            throw new RuntimeException("Không thể xóa sản phẩm này vì đã có dữ liệu bán hàng (hóa đơn). Hãy chuyển sang trạng thái Ngừng bán.");
        }

        // Kiểm tra xem sản phẩm có đang được gán cho rạp nào không
        if (cinemaProductRepository.existsByProduct_ProductId(id)) {
            throw new RuntimeException("Sản phẩm đang được gán cho một hoặc nhiều rạp. Hãy gỡ bỏ khỏi rạp trước khi xóa.");
        }

        productRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa sản phẩm thành công")
                .build());
    }

    private void validate(ProductDTO dto, boolean isCreate) {
        if (dto == null) {
            throw new RuntimeException("Dữ liệu không hợp lệ");
        }
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new RuntimeException("Tên sản phẩm không được để trống");
        }
        if (dto.getPrice() == null || dto.getPrice() <= 0) {
            throw new RuntimeException("Giá không hợp lệ");
        }
        if (isCreate && (dto.getImage() == null || dto.getImage().trim().isEmpty())) {
            throw new RuntimeException("Ảnh sản phẩm không được để trống");
        }
        if (dto.getCategoryId() != null && !categoryProductRepository.existsById(dto.getCategoryId())) {
            throw new RuntimeException("Không tìm thấy loại sản phẩm với id: " + dto.getCategoryId());
        }
    }

    private ProductDTO toDTO(Product p) {
        CategoryProduct c = p.getCategory();
        return ProductDTO.builder()
                .id(p.getProductId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .image(p.getImage())
                .status(p.getStatus() != null ? p.getStatus() : 1)
                .categoryId(c != null ? c.getCategoryProductId() : null)
                .categoryName(c != null ? c.getName() : null)
                .build();
    }

    private Product fromDTO(Product p, ProductDTO dto) {
        p.setName(dto.getName() != null ? dto.getName().trim() : p.getName());
        p.setDescription(dto.getDescription());
        p.setPrice(dto.getPrice());
        if (dto.getImage() != null && !dto.getImage().trim().isEmpty()) {
            p.setImage(dto.getImage().trim());
        }
        p.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        if (dto.getCategoryId() != null) {
            CategoryProduct c = categoryProductRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy loại sản phẩm"));
            p.setCategory(c);
        }
        return p;
    }
}
