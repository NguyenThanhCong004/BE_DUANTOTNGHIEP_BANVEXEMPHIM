package com.fpoly.duan.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.payos.PayOSCheckoutData;
import com.fpoly.duan.dto.payos.PayOSCreatePaymentLinkRequest;
import com.fpoly.duan.service.PayOSService;
import com.fpoly.duan.service.PayOSWebhookService;
import com.fpoly.duan.service.TicketCheckoutService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Thanh toán PayOS: tạo link + webhook.
 */
@RestController
@RequestMapping("/api/v1/payments/payos")
@RequiredArgsConstructor
@Tag(name = "10. Thanh toán PayOS", description = """
        Tạo link thanh toán (JWT khuyến nghị). Webhook **không JWT** — PayOS server gọi trực tiếp.
        Cấu hình webhook URL trên my.payos.vn trỏ tới `POST .../webhook`.
        """)
public class PayOSController {

    private final PayOSService payOSService;
    private final PayOSWebhookService payOSWebhookService;
    private final TicketCheckoutService ticketCheckoutService;

    @PostMapping("/create-link")
    @Operation(summary = "Tạo link thanh toán PayOS", description = "Gọi API PayOS merchant; trả `checkoutUrl` cho FE redirect.")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    public ResponseEntity<ApiResponse<PayOSCheckoutData>> createPaymentLink(
            @Valid @RequestBody PayOSCreatePaymentLinkRequest request) {
        PayOSCheckoutData data = payOSService.createPaymentLink(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<PayOSCheckoutData>builder()
                        .status(HttpStatus.CREATED.value())
                        .message("Tạo link thanh toán PayOS thành công")
                        .data(data)
                        .build());
    }

    /**
     * Webhook PayOS gọi khi có thanh toán — cấu hình URL này trên my.payos.vn.
     * Phải trả HTTP 2xx; kiểm tra signature trước khi cập nhật đơn hàng.
     */
    @PostMapping("/webhook")
    @Operation(summary = "Webhook thanh toán (PayOS → BE)", description = "Không dùng JWT. Kiểm tra HMAC `signature` với payload `data`.")
    public ResponseEntity<ApiResponse<PayOSWebhookAck>> paymentWebhook(@RequestBody JsonNode body) {
        String signature = body.path("signature").asText(null);
        JsonNode dataNode = body.get("data");
        if (dataNode == null || !dataNode.isObject()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ApiResponse.<PayOSWebhookAck>builder()
                            .status(HttpStatus.BAD_REQUEST.value())
                            .message("Thiếu data webhook")
                            .build());
        }
        org.json.JSONObject dataJson = new org.json.JSONObject(dataNode.toString());
        if (!payOSWebhookService.verifyPaymentWebhookSignature(dataJson, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.<PayOSWebhookAck>builder()
                            .status(HttpStatus.UNAUTHORIZED.value())
                            .message("Signature webhook không hợp lệ")
                            .build());
        }
        try {
            ticketCheckoutService.confirmPaymentFromPayosWebhook(dataJson);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ApiResponse.<PayOSWebhookAck>builder()
                            .status(HttpStatus.BAD_REQUEST.value())
                            .message(ex.getMessage())
                            .build());
        }
        PayOSWebhookAck ack = PayOSWebhookAck.builder()
                .received(true)
                .orderCode(dataJson.optLong("orderCode"))
                .build();
        return ResponseEntity.ok(ApiResponse.<PayOSWebhookAck>builder()
                .status(HttpStatus.OK.value())
                .message("Webhook đã nhận")
                .data(ack)
                .build());
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PayOSWebhookAck {
        private boolean received;
        private long orderCode;
    }
}
