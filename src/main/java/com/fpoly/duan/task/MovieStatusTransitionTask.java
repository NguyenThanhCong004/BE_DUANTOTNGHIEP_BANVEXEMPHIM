package com.fpoly.duan.task;

import com.fpoly.duan.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class MovieStatusTransitionTask {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MovieRepository movieRepository;

    /**
     * Tự động chuyển phim "Sắp chiếu" (status=2) sang "Đang chiếu" (status=1)
     * khi đến hoặc qua ngày khởi chiếu.
     * Chạy một lần khi backend khởi động để sửa ngay dữ liệu cũ sau khi reset Docker.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void activateReleasedMoviesOnStartup() {
        activateReleasedMovies("khi backend khởi động");
    }

    /**
     * Tự động chuyển phim "Sắp chiếu" (status=2) sang "Đang chiếu" (status=1)
     * khi đến hoặc qua ngày khởi chiếu.
     * Chạy lúc 00:05 mỗi ngày theo giờ Việt Nam.
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void activateReleasedMoviesBySchedule() {
        activateReleasedMovies("theo lịch hằng ngày");
    }

    private void activateReleasedMovies(String reason) {
        LocalDate today = LocalDate.now(APP_ZONE);
        log.info("Bắt đầu kiểm tra phim Sắp chiếu cần chuyển sang Đang chiếu {}. Ngày hệ thống: {}", reason, today);
        try {
            int count = movieRepository.activateReleasedMovies(today);
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
