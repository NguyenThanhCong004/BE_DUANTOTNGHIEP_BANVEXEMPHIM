package com.fpoly.duan.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FoodOrderVerifyRequest {
    @NotBlank(message = "Mã QR bắp nước không được để trống")
    private String receiptToken;
}
