package com.fpoly.duan.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.fpoly.duan.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Phát hiện & xử lý hành vi "giữ ghế/đơn hàng rồi bỏ" lặp lại nhiều lần trong thời gian ngắn
 * (giữ ghế để phá, chiếm dụng ghế không cho khách khác mua). Toàn bộ state ở đây là in-memory
 * (không lưu DB) — cùng triết lý với {@link EphemeralSeatHoldService}: đây là bộ đếm chống phá
 * tạm thời trong phiên hoạt động của server, không phải bằng chứng cần lưu vĩnh viễn.
 *
 * Ngưỡng: cảnh báo ở vi phạm thứ 3, khoá tài khoản (users.status = 0, tái dùng cơ chế khoá có sẵn)
 * ở vi phạm thứ 5, tính trong cửa sổ trượt 30 phút gần nhất.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeatHoldAbuseService {

    private static final long WINDOW_MS = 30 * 60_000L;
    private static final int WARNING_THRESHOLD = 3;
    private static final int LOCK_THRESHOLD = 5;

    public enum ViolationType {
        /** Giữ ghế liên tục >= ngưỡng thời gian rồi bỏ/hết hạn mà không tạo đơn. */
        HOLD_ABANDONED,
        /** Đơn PENDING bị hệ thống tự huỷ vì quá hạn không thanh toán. */
        ORDER_ABANDONED
    }

    private final UserRepository userRepository;

    private final ConcurrentHashMap<Integer, Deque<Long>> violations = new ConcurrentHashMap<>();

    /** Ghi nhận 1 lần vi phạm; tự khoá tài khoản nếu vượt ngưỡng. */
    public void recordViolation(Integer userId, ViolationType type) {
        if (userId == null) {
            return;
        }
        Deque<Long> deque = violations.computeIfAbsent(userId, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        int count;
        synchronized (deque) {
            deque.addLast(now);
            pruneOld(deque, now);
            count = deque.size();
        }
        log.warn("Seat-hold violation [{}] cho user {} — {} lần trong 30 phút gần nhất", type, userId, count);
        if (count >= LOCK_THRESHOLD) {
            lockUser(userId, count);
        }
    }

    /** Số vi phạm còn hiệu lực trong 30 phút gần nhất. */
    public int currentViolationCount(Integer userId) {
        if (userId == null) {
            return 0;
        }
        Deque<Long> deque = violations.get(userId);
        if (deque == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        synchronized (deque) {
            pruneOld(deque, now);
            return deque.size();
        }
    }

    /** True khi user đã tới ngưỡng cảnh báo nhưng chưa bị khoá — FE hiển thị banner nhắc nhở. */
    public boolean shouldWarn(Integer userId) {
        int count = currentViolationCount(userId);
        return count >= WARNING_THRESHOLD && count < LOCK_THRESHOLD;
    }

    private void pruneOld(Deque<Long> deque, long now) {
        while (!deque.isEmpty() && now - deque.peekFirst() > WINDOW_MS) {
            deque.pollFirst();
        }
    }

    private void lockUser(Integer userId, int violationCount) {
        userRepository.findById(userId).ifPresent(user -> {
            Integer status = user.getStatus();
            if (status == null || status != 0) {
                user.setStatus(0);
                userRepository.save(user);
                log.warn("Tự động khoá tài khoản user {} do giữ ghế/đơn hàng bất thường ({} lần trong 30 phút)",
                        userId, violationCount);
            }
        });
    }
}
