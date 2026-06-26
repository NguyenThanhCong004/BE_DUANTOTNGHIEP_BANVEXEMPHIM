package com.fpoly.duan.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TicketQrVerifyRequest {
    @NotBlank(message = "QR vé không được để trống")
    private String qrToken;
}
