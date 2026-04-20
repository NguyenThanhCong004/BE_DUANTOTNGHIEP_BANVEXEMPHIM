package com.fpoly.duan.config;

import java.util.Optional;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.duan.entity.SeatType;
import com.fpoly.duan.repository.SeatTypeRepository;
import com.fpoly.duan.util.SeatTypeNaming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Luôn đảm bảo 3 loại ghế mặc định khi chạy app (không phụ thuộc {@code app.data.seed}),
 * để API lưu sơ đồ ghế khớp tên loại trong DB.
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class SeatTypeBootstrap {

    private final SeatTypeRepository seatTypeRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void ensureThreeDefaultSeatTypes() {
        ensure("Thường", 0.0, false, "#0D6EFD");
        ensure("VIP", 30_000.0, false, "#FFC107");
        ensure("Đôi", 20_000.0, true, "#DC3545");
        syncCoupleFlagsFromNames();
    }

    private void ensure(String name, double surcharge, boolean coupleSeat, String colorHex) {
        Optional<SeatType> existing = seatTypeRepository.findByName(name);
        String normalized = SeatTypeNaming.normalizeColorHex(colorHex);
        if (existing.isPresent()) {
            SeatType t = existing.get();
            boolean needSave = false;
            if (!Boolean.valueOf(coupleSeat).equals(Boolean.TRUE.equals(t.getCoupleSeat()))) {
                t.setCoupleSeat(coupleSeat);
                needSave = true;
                log.info("[SeatTypes] Đã cập nhật couple_seat cho {} -> {}", name, coupleSeat);
            }
            if (normalized != null && (t.getColor() == null || t.getColor().isBlank())) {
                t.setColor(normalized);
                needSave = true;
                log.info("[SeatTypes] Đã gán màu mặc định cho {} -> {}", name, normalized);
            }
            if (needSave) {
                seatTypeRepository.save(t);
            }
            return;
        }
        SeatType t = new SeatType();
        t.setName(name);
        t.setSurcharge(surcharge);
        t.setCoupleSeat(coupleSeat);
        t.setColor(normalized);
        seatTypeRepository.save(t);
        log.info("[SeatTypes] Đã tạo loại ghế mặc định: {} (+{}) couple={} color={}", name, surcharge, coupleSeat, normalized);
    }

    /** Đồng bộ cờ ghế đôi chỉ cho 3 loại mặc định (không động vào loại custom từ admin). */
    private void syncCoupleFlagsFromNames() {
        // Chỉ sync cho 3 loại mặc định
        java.util.List<String> defaultNames = java.util.Arrays.asList("Thường", "VIP", "Đôi");
        for (SeatType t : seatTypeRepository.findAll()) {
            if (!defaultNames.contains(t.getName())) {
                continue; // Bỏ qua loại custom
            }
            boolean inferred = SeatTypeNaming.isCoupleSeatType(t.getName());
            if (!Boolean.valueOf(inferred).equals(Boolean.TRUE.equals(t.getCoupleSeat()))) {
                t.setCoupleSeat(inferred);
                seatTypeRepository.save(t);
                log.info("[SeatTypes] sync couple_seat: {} -> {}", t.getName(), inferred);
            }
        }
    }
}
