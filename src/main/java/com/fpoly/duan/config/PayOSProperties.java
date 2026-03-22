package com.fpoly.duan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Cấu hình PayOS (đọc từ {@code payos.*} trong application.properties).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "payos")
public class PayOSProperties {

    /**
     * API merchant — mặc định production PayOS.
     */
    private String baseUrl = "https://api-merchant.payos.vn";

    private String clientId;
    private String apiKey;
    private String checksumKey;
}
