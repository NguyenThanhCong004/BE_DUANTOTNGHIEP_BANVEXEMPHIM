package com.fpoly.duan.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bật bằng {@code app.data.seed=true} trong {@code application.properties}.
 * Gọi seed sau khi ứng dụng sẵn sàng (sau khi Hibernate tạo/cập nhật schema nếu dùng {@code ddl-auto}).
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.data.seed", havingValue = "true")
public class DataSeeder {

    private final DatabaseSeedService databaseSeedService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            databaseSeedService.seedIfEnabled();
        } catch (Exception ex) {
            log.error("[DataSeed] Lỗi khi seed: {}", ex.getMessage(), ex);
        }
    }
}
