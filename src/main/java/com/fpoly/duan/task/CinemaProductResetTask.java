package com.fpoly.duan.task;

import com.fpoly.duan.entity.CinemaProduct;
import com.fpoly.duan.repository.CinemaProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CinemaProductResetTask {

    private final CinemaProductRepository cinemaProductRepository;

    /**
     * Tự động reset tất cả sản phẩm tại rạp về trạng thái "Còn hàng" (isActive = true)
     * Chạy vào lúc 07:00:00 sáng mỗi ngày.
     * Cron expression: "0 0 7 * * *" (giây phút giờ ngày tháng thứ)
     */
    @Scheduled(cron = "0 0 7 * * *")
    @Transactional
    public void resetCinemaProductsStock() {
        log.info("Bắt đầu tự động reset trạng thái còn hàng cho tất cả sản phẩm tại rạp...");
        try {
            List<CinemaProduct> allCinemaProducts = cinemaProductRepository.findAll();
            for (CinemaProduct cp : allCinemaProducts) {
                if (cp.getIsActive() == null || !cp.getIsActive()) {
                    cp.setIsActive(true);
                }
            }
            cinemaProductRepository.saveAll(allCinemaProducts);
            log.info("Đã reset thành công trạng thái còn hàng cho {} sản phẩm.", allCinemaProducts.size());
        } catch (Exception e) {
            log.error("Lỗi khi reset trạng thái sản phẩm: {}", e.getMessage(), e);
        }
    }
}
