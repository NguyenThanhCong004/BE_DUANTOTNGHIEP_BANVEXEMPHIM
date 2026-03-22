package com.fpoly.duan.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CinemaProductMenuDTO {
    private Integer cinemaId;
    /** Đang mở bán tại rạp (cinema_products.is_active = true) */
    private List<CinemaProductOfferDTO> onSale;
    /** Chưa mở bán: chưa gắn rạp hoặc đã gắn nhưng tắt bán */
    private List<CinemaProductOfferDTO> notOnSale;
}
