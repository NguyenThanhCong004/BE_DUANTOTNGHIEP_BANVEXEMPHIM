package com.fpoly.duan.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Ghế đang được người khác &quot;chọn&quot; (giữ tạm ~45s, gia hạn khi FE gửi lại).
 * Không thay thế khóa DB; giảm xung đột hiển thị giữa nhiều tab.
 */
@Service
public class EphemeralSeatHoldService {

    private static final long TTL_MS = 45_000;

    private record Hold(String holderId, long expiresAtEpochMs) {
    }

    private final ConcurrentHashMap<String, Hold> map = new ConcurrentHashMap<>();

    private static String key(int showtimeId, int seatId) {
        return showtimeId + ":" + seatId;
    }

    /** Gia hạn ghế cho holder; bỏ giữ các ghế cũ của holder không còn trong danh sách. */
    public void refresh(int showtimeId, String holderId, Collection<Integer> seatIds) {
        if (holderId == null || holderId.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        long exp = now + TTL_MS;
        String prefix = showtimeId + ":";

        map.entrySet().removeIf(e -> {
            if (!e.getKey().startsWith(prefix)) {
                return false;
            }
            Hold h = e.getValue();
            if (h.expiresAtEpochMs <= now) {
                return true;
            }
            if (!holderId.equals(h.holderId)) {
                return false;
            }
            int sid = parseSeatId(e.getKey(), prefix);
            return !seatIds.contains(sid);
        });

        for (Integer sid : seatIds) {
            if (sid == null) {
                continue;
            }
            String k = key(showtimeId, sid);
            map.compute(k, (kk, old) -> {
                if (old != null && old.expiresAtEpochMs > now && !old.holderId.equals(holderId)) {
                    return old;
                }
                return new Hold(holderId, exp);
            });
        }
    }

    public List<Integer> peerHeldSeatIds(int showtimeId, String excludeHolderId) {
        long now = System.currentTimeMillis();
        String prefix = showtimeId + ":";
        List<Integer> out = new ArrayList<>();
        for (Map.Entry<String, Hold> e : map.entrySet()) {
            if (!e.getKey().startsWith(prefix)) {
                continue;
            }
            Hold h = e.getValue();
            if (h.expiresAtEpochMs <= now) {
                continue;
            }
            if (excludeHolderId != null && excludeHolderId.equals(h.holderId)) {
                continue;
            }
            out.add(parseSeatId(e.getKey(), prefix));
        }
        return out;
    }

    public boolean isHeldByOther(int showtimeId, String myHolderId, int seatId) {
        if (myHolderId == null || myHolderId.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Hold h = map.get(key(showtimeId, seatId));
        if (h == null || h.expiresAtEpochMs <= now) {
            return false;
        }
        return !myHolderId.equals(h.holderId);
    }

    public void releaseSeats(int showtimeId, Collection<Integer> seatIds) {
        for (Integer sid : seatIds) {
            if (sid != null) {
                map.remove(key(showtimeId, sid));
            }
        }
    }

    @Scheduled(fixedRate = 60_000)
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(e -> e.getValue().expiresAtEpochMs <= now);
    }

    private static int parseSeatId(String fullKey, String prefix) {
        return Integer.parseInt(fullKey.substring(prefix.length()));
    }
}
