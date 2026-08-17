package com.fpoly.duan.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fpoly.duan.entity.Seat;
import com.fpoly.duan.entity.SeatType;

/**
 * Quy tắc sơ đồ ghế: không được để lại đúng 1 ghế trống kẹp giữa hai ghế đã chiếm (đã bán / đang chọn)
 * trên cùng một hàng.
 *
 * Rule chỉ chặn khi CHÍNH giao dịch hiện tại (các ghế mới chọn) làm phát sinh ghế mồ côi MỚI. Nếu ghế
 * mồ côi đã tồn tại sẵn trong phòng từ trước giao dịch này (vd. do một giao dịch khác không thuộc
 * diện kiểm tra tạo ra), giao dịch hiện tại không bị chặn vì lý do đó — tránh 1 ghế mồ côi cũ làm
 * nghẽn mọi giao dịch không liên quan khác trong cùng phòng/suất chiếu.
 */
public final class SeatLayoutRules {

    private SeatLayoutRules() {
    }

    /**
     * @param allRoomSeats tất cả ghế của phòng (cùng room với suất)
     * @param existingBlockedSeatIds ghế đã bị chiếm TRƯỚC giao dịch này (đã bán / đang giữ)
     * @param newlySelectedSeatIds ghế đang được chọn trong giao dịch hiện tại
     */
    public static void assertNoNewSingleSeatOrphanInRows(List<Seat> allRoomSeats,
            Set<Integer> existingBlockedSeatIds, Set<Integer> newlySelectedSeatIds) {
        if (allRoomSeats == null || allRoomSeats.isEmpty()) {
            return;
        }
        Set<Integer> before = existingBlockedSeatIds != null ? existingBlockedSeatIds : Set.of();
        Set<Integer> orphansBefore = findOrphanSeatIds(allRoomSeats, before);

        Set<Integer> after = new HashSet<>(before);
        if (newlySelectedSeatIds != null) {
            after.addAll(newlySelectedSeatIds);
        }
        Set<Integer> newOrphans = findOrphanSeatIds(allRoomSeats, after);
        newOrphans.removeAll(orphansBefore);

        if (!newOrphans.isEmpty()) {
            Integer orphanId = newOrphans.iterator().next();
            Seat orphanSeat = allRoomSeats.stream()
                    .filter(s -> orphanId.equals(s.getSeatId()))
                    .findFirst()
                    .orElse(null);
            String label = orphanSeat != null
                    ? (orphanSeat.getRow() != null ? orphanSeat.getRow() : "")
                            + (orphanSeat.getNumber() != null ? orphanSeat.getNumber() : "")
                    : "";
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Không được chừa 1 ghế trống lẻ giữa hàng (ghế " + label
                            + "). Chọn thêm ghế bên cạnh hoặc bỏ ghế để không tạo khoảng trống đơn.");
        }
    }

    /** Trả về id các ghế đang bị kẹp giữa 2 ghế thuộc {@code blockedSeatIds} trên cùng hàng. */
    private static Set<Integer> findOrphanSeatIds(List<Seat> allRoomSeats, Set<Integer> blockedSeatIds) {
        Set<Integer> orphans = new HashSet<>();
        Map<String, List<Seat>> byRow = new HashMap<>();
        for (Seat s : allRoomSeats) {
            if (s.getSeatId() == null) {
                continue;
            }
            String row = s.getRow() != null ? s.getRow() : "?";
            byRow.computeIfAbsent(row, k -> new ArrayList<>()).add(s);
        }
        for (List<Seat> rowSeats : byRow.values()) {
            rowSeats.sort(Comparator
                    .comparing((Seat s) -> s.getX() != null ? s.getX() : 0)
                    .thenComparing(s -> s.getNumber() != null ? s.getNumber() : ""));
            int n = rowSeats.size();
            for (int i = 0; i < n; i++) {
                Seat mid = rowSeats.get(i);
                Integer midId = mid.getSeatId();
                if (blockedSeatIds.contains(midId)) {
                    continue;
                }
                if (isCoupleSeat(mid)) {
                    // Ghế đôi không bị coi là "mồ côi" — cho phép chọn ghế đôi cách quãng
                    // (không bắt buộc liền kề như ghế thường).
                    continue;
                }
                boolean leftBlocked = i > 0 && blockedSeatIds.contains(rowSeats.get(i - 1).getSeatId());
                boolean rightBlocked = i < n - 1 && blockedSeatIds.contains(rowSeats.get(i + 1).getSeatId());
                if (leftBlocked && rightBlocked) {
                    orphans.add(midId);
                }
            }
        }
        return orphans;
    }

    private static boolean isCoupleSeat(Seat seat) {
        SeatType st = seat.getSeatType();
        return st != null && Boolean.TRUE.equals(st.getCoupleSeat());
    }
}
