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

/**
 * Quy tắc sơ đồ ghế: không được để lại đúng 1 ghế trống kẹp giữa hai ghế đã chiếm (đã bán / đang chọn)
 * trên cùng một hàng.
 */
public final class SeatLayoutRules {

    private SeatLayoutRules() {
    }

    /**
     * @param allRoomSeats tất cả ghế của phòng (cùng room với suất)
     * @param blockedSeatIds ghế không được bán (đã giữ/đã trả) ∪ lựa chọn hiện tại
     */
    public static void assertNoSingleSeatOrphanInRows(List<Seat> allRoomSeats, Set<Integer> blockedSeatIds) {
        if (allRoomSeats == null || allRoomSeats.isEmpty()) {
            return;
        }
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
                boolean leftBlocked = i > 0 && blockedSeatIds.contains(rowSeats.get(i - 1).getSeatId());
                boolean rightBlocked = i < n - 1 && blockedSeatIds.contains(rowSeats.get(i + 1).getSeatId());
                if (leftBlocked && rightBlocked) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Không được chừa 1 ghế trống lẻ giữa hàng (ghế "
                                    + (mid.getRow() != null ? mid.getRow() : "") + (mid.getNumber() != null ? mid.getNumber() : "")
                                    + "). Chọn thêm ghế bên cạnh hoặc bỏ ghế để không tạo khoảng trống đơn.");
                }
            }
        }
    }

    /** blocked = đã bán/đặt (DB) ∪ người dùng đang chọn */
    public static Set<Integer> mergeBlocked(List<Integer> dbHeldSeatIds, Set<Integer> selectedSeatIds) {
        Set<Integer> s = new HashSet<>();
        if (dbHeldSeatIds != null) {
            for (Integer id : dbHeldSeatIds) {
                if (id != null) {
                    s.add(id);
                }
            }
        }
        if (selectedSeatIds != null) {
            s.addAll(selectedSeatIds);
        }
        return s;
    }
}
