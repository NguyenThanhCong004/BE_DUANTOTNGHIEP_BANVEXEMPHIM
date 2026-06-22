package com.fpoly.duan.service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpoly.duan.config.PayOSProperties;
import com.fpoly.duan.dto.payos.PayOSCheckoutData;
import com.fpoly.duan.dto.payos.PayOSCreatePaymentLinkRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PayOSService {

    @Qualifier("payOSRestClient")
    private final RestClient payOSRestClient;
    private final PayOSProperties payOSProperties;
    private final ObjectMapper objectMapper;

    /**
     * Chữ ký tạo link thanh toán (theo tài liệu PayOS):
     * amount=&cancelUrl=&description=&orderCode=&returnUrl= (sắp xếp alphabet).
     */
    public String signCreatePaymentRequest(
            int amount,
            String cancelUrl,
            String description,
            long orderCode,
            String returnUrl) {
        String data = "amount=" + amount
                + "&cancelUrl=" + cancelUrl
                + "&description=" + description
                + "&orderCode=" + orderCode
                + "&returnUrl=" + returnUrl;
        return hmacSha256Hex(data, payOSProperties.getChecksumKey());
    }

    /**
     * Gọi PayOS tạo link thanh toán.
     */
    public PayOSCheckoutData createPaymentLink(PayOSCreatePaymentLinkRequest req) {
        String signature = signCreatePaymentRequest(
                req.getAmount(),
                req.getCancelUrl(),
                req.getDescription(),
                req.getOrderCode(),
                req.getReturnUrl());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", req.getOrderCode());
        body.put("amount", req.getAmount());
        body.put("description", req.getDescription());
        body.put("returnUrl", req.getReturnUrl());
        body.put("cancelUrl", req.getCancelUrl());
        body.put("signature", signature);
        if (req.getBuyerName() != null && !req.getBuyerName().isBlank()) {
            body.put("buyerName", req.getBuyerName());
        }
        if (req.getBuyerEmail() != null && !req.getBuyerEmail().isBlank()) {
            body.put("buyerEmail", req.getBuyerEmail());
        }
        if (req.getBuyerPhone() != null && !req.getBuyerPhone().isBlank()) {
            body.put("buyerPhone", req.getBuyerPhone());
        }
        if (req.getExpiredAt() != null) {
            body.put("expiredAt", req.getExpiredAt());
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            String responseJson = payOSRestClient.post()
                    .uri("/v2/payment-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseJson);
            String code = root.path("code").asText();
            if (!"00".equals(code)) {
                String desc = root.path("desc").asText("Lỗi PayOS");
                throw new IllegalStateException(desc);
            }
            JsonNode data = root.get("data");
            if (data == null || data.isNull()) {
                throw new IllegalStateException("PayOS không trả về data");
            }
            return PayOSCheckoutData.builder()
                    .checkoutUrl(data.path("checkoutUrl").asText(null))
                    .paymentLinkId(data.path("paymentLinkId").asText(null))
                    .qrCode(data.path("qrCode").asText(null))
                    .orderCode(data.path("orderCode").asLong())
                    .amount(data.path("amount").asLong())
                    .currency(data.path("currency").asText(null))
                    .status(data.path("status").asText(null))
                    .description(data.path("description").asText(null))
                    .build();
        } catch (RestClientResponseException e) {
            String msg = e.getResponseBodyAsString();
            throw new IllegalStateException("PayOS HTTP " + e.getStatusCode().value() + ": " + msg, e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Không gọi được PayOS: " + e.getMessage(), e);
        }
    }

    /**
     * Truy vấn thông tin thanh toán từ PayOS.
     */
    public PayOSCheckoutData getPaymentInformation(long orderCode) {
        try {
            String responseJson = payOSRestClient.get()
                    .uri("/v2/payment-requests/" + orderCode)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseJson);
            String code = root.path("code").asText();
            if (!"00".equals(code)) {
                String desc = root.path("desc").asText("Lỗi PayOS");
                throw new IllegalStateException(desc);
            }
            JsonNode data = root.get("data");
            return PayOSCheckoutData.builder()
                    .orderCode(data.path("orderCode").asLong())
                    .amount(data.path("amount").asLong())
                    .currency(data.path("currency").asText(null))
                    .status(data.path("status").asText(null))
                    .description(data.path("description").asText(null))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Lỗi kiểm tra trạng thái PayOS: " + e.getMessage(), e);
        }
    }

    private static String hmacSha256Hex(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC SHA256 thất bại", e);
        }
    }
}
