package com.fpoly.duan.task;

import com.fpoly.duan.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class MovieStatusTransitionTask {

    private final MovieRepository movieRepository;

    /**
     * Tự động chuyển phim "Sắp chiếu" (status=2) sang "Đang chiếu" (status=1)
     * khi đến hoặc qua ngày khởi chiếu.
     * Chạy lúc 00:05 mỗi ngày theo giờ Việt Nam.
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void activateReleasedMovies() {
        log.info("Bắt đầu kiểm tra phim Sắp chiếu cần chuyển sang Đang chiếu...");
        try {
            int count = movieRepository.activateReleasedMovies(LocalDate.now());
            if (count > 0) {
                log.info("Đã tự động chuyển {} phim sang trạng thái Đang chiếu.", count);
            } else {
                log.info("Không có phim nào cần chuyển trạng thái hôm nay.");
            }
        } catch (Exception e) {
            log.error("Lỗi khi tự động chuyển trạng thái phim: {}", e.getMessage(), e);
        }
    }
}
