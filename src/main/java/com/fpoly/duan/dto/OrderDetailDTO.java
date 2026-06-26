package com.fpoly.duan.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderDetailDTO {
    private Integer id;
    private String orderCode;
    private LocalDateTime createdAt;
    private Double originalAmount;
    private Double discountAmount;
    private Double finalAmount;
    private String paymentMethod;
    private String voucherCode;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String staffName;
    private Integer cinemaId;
    private String cinemaName;
    private String cinemaAddress;
    private Integer status;
    private List<TicketInfo> tickets;
    private List<FoodInfo> foods;

    @Data
    @Builder
    public static class TicketInfo {
        private Integer ticketId;
        private String ticketCode;
        private String qrToken;
        private String qrImagePath;
        private String movieTitle;
        private LocalDateTime showtime;
        private LocalDateTime showtimeStart;
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
