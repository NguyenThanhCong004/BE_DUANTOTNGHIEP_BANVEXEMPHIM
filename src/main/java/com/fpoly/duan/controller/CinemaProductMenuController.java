package com.fpoly.duan.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.CinemaProductMenuDTO;
import com.fpoly.duan.dto.CinemaProductOfferDTO;
import com.fpoly.duan.dto.SellingToggleRequest;
import com.fpoly.duan.entity.CategoryProduct;
import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.entity.CinemaProduct;
import com.fpoly.duan.entity.Product;
import com.fpoly.duan.repository.CinemaProductRepository;
import com.fpoly.duan.repository.CinemaRepository;
import com.fpoly.duan.repository.ProductRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/cinemas/{cinemaId}/product-menu")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "8g. Menu sản phẩm theo rạp", description = "Đang bán / chưa bán — bảng cinema_products")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class CinemaProductMenuController {

    private final CinemaRepository cinemaRepository;
    private final ProductRepository productRepository;
    private final CinemaProductRepository cinemaProductRepository;

    @GetMapping
    @Operation(summary = "Danh sách sản phẩm chia 2 nhóm: đang bán và chưa bán tại rạp")
    public ResponseEntity<ApiResponse<CinemaProductMenuDTO>> getMenu(@PathVariable Integer cinemaId) {
        cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy rạp với id: " + cinemaId));

        List<Product> allProducts = productRepository.findAll();
        List<CinemaProduct> links = cinemaProductRepository.findByCinema_CinemaId(cinemaId);
        Map<Integer, CinemaProduct> byProductId = links.stream()
                .filter(cp -> cp.getProduct() != null && cp.getProduct().getProductId() != null)
                .collect(Collectors.toMap(cp -> cp.getProduct().getProductId(), cp -> cp, (a, b) -> a));

        List<CinemaProductOfferDTO> onSale = new ArrayList<>();
        List<CinemaProductOfferDTO> notOnSale = new ArrayList<>();

        for (Product p : allProducts) {
            Integer gid = p.getStatus() != null ? p.getStatus() : 1;
            if (gid != 1) {
                continue;
            }
            CinemaProduct cp = byProductId.get(p.getProductId());
            boolean selling = cp != null && Boolean.TRUE.equals(cp.getIsActive());
            CinemaProductOfferDTO row = toOffer(p, cp);
            if (selling) {
                onSale.add(row);
            } else {
                notOnSale.add(row);
            }
        }

        CinemaProductMenuDTO data = CinemaProductMenuDTO.builder()
                .cinemaId(cinemaId)
                .onSale(onSale)
                .notOnSale(notOnSale)
                .build();

        return ResponseEntity.ok(ApiResponse.<CinemaProductMenuDTO>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(data)
                .build());
    }

    @PutMapping("/products/{productId}/selling")
    @Operation(summary = "Bật/tắt bán sản phẩm tại rạp")
    public ResponseEntity<ApiResponse<Void>> setSelling(
            @PathVariable Integer cinemaId,
            @PathVariable Integer productId,
            @RequestBody SellingToggleRequest body) {
        if (body == null || body.getSelling() == null) {
            throw new RuntimeException("Thiếu trường selling (true/false)");
        }
        boolean selling = Boolean.TRUE.equals(body.getSelling());

        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy rạp với id: " + cinemaId));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với id: " + productId));

        Optional<CinemaProduct> existing = cinemaProductRepository
                .findByCinema_CinemaIdAndProduct_ProductId(cinemaId, productId);

        if (selling) {
            CinemaProduct cp = existing.orElseGet(() -> {
                CinemaProduct n = new CinemaProduct();
                n.setCinema(cinema);
                n.setProduct(product);
                return n;
            });
            cp.setIsActive(true);
            cinemaProductRepository.save(cp);
        } else {
            /* Tắt bán = xóa hẳn khỏi bảng cinema_products (không còn dòng inactive). */
            existing.ifPresent(cinemaProductRepository::delete);
        }

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message(selling ? "Đã thêm sản phẩm vào menu rạp" : "Đã xóa sản phẩm khỏi menu rạp")
                .data(null)
                .build());
    }

    private CinemaProductOfferDTO toOffer(Product p, CinemaProduct cp) {
        CategoryProduct c = p.getCategory();
        return CinemaProductOfferDTO.builder()
                .productId(p.getProductId())
                .cinemaProductId(cp != null ? cp.getCinemaProductId() : null)
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .image(p.getImage())
                .globalStatus(p.getStatus() != null ? p.getStatus() : 1)
                .categoryId(c != null ? c.getCategoryProductId() : null)
                .categoryName(c != null ? c.getName() : null)
                .build();
    }
}
