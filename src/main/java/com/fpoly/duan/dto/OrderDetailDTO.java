package com.fpoly.duan.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class OrderDetailDTO {
    private String orderCode;
    private String createdAt;
    private Double finalAmount;
    private String paymentMethod;
    private String customerName;
    private Integer status;
    private List<TicketInfo> tickets;
    private List<FoodInfo> foods;

    @Data
    @Builder
    public static class TicketInfo {
        private String movieTitle;
        private String showtime;
        private String roomName;
        private String seatNumber;
        private String seatTypeName;
        private Double originalPrice;
        private Double promotionDiscount;
        private Double price;
    }

    @Data
    @Builder
    public static class FoodInfo {
        private String productName;
        private Integer quantity;
        private Double price;
    }
}
