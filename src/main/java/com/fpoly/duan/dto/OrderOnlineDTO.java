package com.fpoly.duan.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderOnlineDTO {
    private Integer id;
    private String orderCode;
    private LocalDateTime createdAt;
    private Double originalAmount;
    private Double discountAmount;
    private Double finalAmount;
    private Integer status;
    private Integer userId;
    private String customerName;
    private String customerEmail;
    
    private String cinemaName;
    private String staffName;
    private List<TicketInfoDTO> tickets;
    private List<FoodInfoDTO> foods;

    @Data
    @Builder
    public static class TicketInfoDTO {
        private String movieTitle;
        private LocalDateTime showtime;
        private String seatNumber;
        private Double originalPrice;
        private Double promotionDiscount;
        private Double price;
    }

    @Data
    @Builder
    public static class FoodInfoDTO {
        private String productName;
        private Integer quantity;
        private Double price;
    }
}
