package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một dòng sản phẩm trong menu rạp (đang bán hoặc chưa mở bán).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CinemaProductOfferDTO {
    private Integer productId;
    /** id bảng cinema_products — null nếu chưa từng gắn rạp */
    private Integer cinemaProductId;
    private String name;
    private String description;
    private Double price;
    private String image;
    /** Trạng thái catalog toàn hệ thống (products.status), mặc định 1 = hoạt động */
    private Integer globalStatus;
    private Integer categoryId;
    private String categoryName;
}
