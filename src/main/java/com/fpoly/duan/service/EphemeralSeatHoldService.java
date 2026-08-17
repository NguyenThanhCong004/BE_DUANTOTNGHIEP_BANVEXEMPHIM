package com.fpoly.duan.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Ghế đang được người khác &quot;chọn&quot; (giữ tạm ~45s, gia hạn khi FE gửi lại).
 * Không thay thế khóa DB; giảm xung đột hiển thị giữa nhiều tab.
 */
@Service
@RequiredArgsConstructor
public class EphemeralSeatHoldService {

    private static final long TTL_MS = 300_000;

    /** Giữ liên tục >= 4 phút rồi bỏ/hết hạn mà không mua — tính là 1 lần vi phạm chống phá. */
    private static final long HOLD_ABANDON_THRESHOLD_MS = 4 * 60_000L;

    private record Hold(String holderId, long expiresAtEpochMs) {
    }

    /** Theo dõi thời điểm 1 user bắt đầu giữ ghế liên tục cho 1 suất chiếu, để phát hiện giữ-rồi-bỏ. */
    private record HoldSession(Integer userId, long firstHeldAtMs, long lastRefreshAtMs) {
    }

    private final SeatHoldAbuseService seatHoldAbuseService;

    private final ConcurrentHashMap<String, Hold> map = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HoldSession> sessions = new ConcurrentHashMap<>();

    private static String key(int showtimeId, int seatId) {
        return showtimeId + ":" + seatId;
    }

    private static String sessionKey(Integer userId, int showtimeId) {
        return userId + ":" + showtimeId;
    }

    /** Gia hạn ghế cho holder; bỏ giữ các ghế cũ của holder không còn trong danh sách.
     * @param userId id khách hàng đang đăng nhập (từ JWT) — null nếu không xác định được (không theo dõi chống phá). */
    public void refresh(int showtimeId, String holderId, Collection<Integer> seatIds, Integer userId) {
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

        trackHoldSession(showtimeId, userId, seatIds, now);
    }

    private void trackHoldSession(int showtimeId, Integer userId, Collection<Integer> seatIds, long now) {
        if (userId == null) {
            return;
        }
        String sKey = sessionKey(userId, showtimeId);
        if (seatIds == null || seatIds.isEmpty()) {
            HoldSession sess = sessions.remove(sKey);
            if (sess != null && now - sess.firstHeldAtMs() >= HOLD_ABANDON_THRESHOLD_MS) {
                seatHoldAbuseService.recordViolation(userId, SeatHoldAbuseService.ViolationType.HOLD_ABANDONED);
            }
        } else {
            sessions.compute(sKey, (k, existing) -> existing != null
                    ? new HoldSession(userId, existing.firstHeldAtMs(), now)
                    : new HoldSession(userId, now, now));
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

    /** Nhả ghế do luồng thành công (đã tạo đơn PENDING / đơn bị huỷ) — không tính là "bỏ giữ để phá". */
    public void releaseSeats(int showtimeId, Collection<Integer> seatIds) {
        releaseSeats(showtimeId, seatIds, null);
    }

    public void releaseSeats(int showtimeId, Collection<Integer> seatIds, Integer userId) {
        for (Integer sid : seatIds) {
            if (sid != null) {
                map.remove(key(showtimeId, sid));
            }
        }
        if (userId != null) {
            sessions.remove(sessionKey(userId, showtimeId));
        }
    }

    @Scheduled(fixedRate = 60_000)
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(e -> e.getValue().expiresAtEpochMs() <= now);

        sessions.entrySet().removeIf(e -> {
            HoldSession sess = e.getValue();
            if (now - sess.lastRefreshAtMs() <= TTL_MS) {
                return false;
            }
            // Không còn gia hạn trong suốt TTL — coi như bị bỏ rơi (đóng tab/mất mạng).
            if (now - sess.firstHeldAtMs() >= HOLD_ABANDON_THRESHOLD_MS) {
                seatHoldAbuseService.recordViolation(sess.userId(), SeatHoldAbuseService.ViolationType.HOLD_ABANDONED);
            }
            return true;
        });
    }

    private static int parseSeatId(String fullKey, String prefix) {
        return Integer.parseInt(fullKey.substring(prefix.length()));
    }
}
