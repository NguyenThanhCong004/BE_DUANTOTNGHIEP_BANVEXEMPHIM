package com.fpoly.duan.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.repository.StaffRepository;
import com.fpoly.duan.repository.UserRepository;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Phát hiện & xử lý hành vi "giữ ghế/đơn hàng rồi bỏ" lặp lại nhiều lần trong thời gian ngắn
 * (giữ ghế để phá, chiếm dụng ghế không cho khách khác mua). Toàn bộ state ở đây là in-memory
 * (không lưu DB) — cùng triết lý với {@link EphemeralSeatHoldService}: đây là bộ đếm chống phá
 * tạm thời trong phiên hoạt động của server, không phải bằng chứng cần lưu vĩnh viễn.
 *
 * Ngưỡng: cảnh báo ở vi phạm thứ 3, khoá tài khoản (users.status = 0, tái dùng cơ chế khoá có sẵn)
 * ở vi phạm thứ 5, tính trong cửa sổ trượt 30 phút gần nhất. Áp dụng cùng ngưỡng cho tài khoản
 * nhân viên bán vé (staff) — khi bị khoá còn gửi email cảnh báo cho admin rạp và chính nhân viên đó,
 * đề phòng tài khoản nhân viên bị lộ và bị lợi dụng để giữ ghế phá khách hàng.
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
    private final StaffRepository staffRepository;
    private final EmailService emailService;

    private final ConcurrentHashMap<Integer, Deque<Long>> violations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Deque<Long>> staffViolations = new ConcurrentHashMap<>();

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

    /** Ghi nhận 1 lần vi phạm của tài khoản NHÂN VIÊN (bán vé tại quầy); tự khoá nếu vượt ngưỡng. */
    public void recordStaffViolation(Integer staffId, ViolationType type) {
        if (staffId == null) {
            return;
        }
        Deque<Long> deque = staffViolations.computeIfAbsent(staffId, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        int count;
        synchronized (deque) {
            deque.addLast(now);
            pruneOld(deque, now);
            count = deque.size();
        }
        log.warn("Seat-hold violation [{}] cho nhân viên {} — {} lần trong 30 phút gần nhất", type, staffId, count);
        if (count >= LOCK_THRESHOLD) {
            lockStaff(staffId, count);
        }
    }

    public int currentStaffViolationCount(Integer staffId) {
        if (staffId == null) {
            return 0;
        }
        Deque<Long> deque = staffViolations.get(staffId);
        if (deque == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        synchronized (deque) {
            pruneOld(deque, now);
            return deque.size();
        }
    }

    public boolean shouldWarnStaff(Integer staffId) {
        int count = currentStaffViolationCount(staffId);
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

    /** Khoá tài khoản nhân viên nghi bị lộ/lợi dụng để giữ ghế phá khách, rồi báo qua email cho admin rạp + chính nhân viên. */
    private void lockStaff(Integer staffId, int violationCount) {
        staffRepository.findById(staffId).ifPresent(staff -> {
            Integer status = staff.getStatus();
            if (status != null && status == 0) {
                return;
            }
            staff.setStatus(0);
            staffRepository.save(staff);
            log.warn("Tự động khoá tài khoản nhân viên {} do giữ ghế/đơn hàng bất thường ({} lần trong 30 phút) — " +
                    "nghi tài khoản bị lộ và bị lợi dụng để phá khách hàng", staffId, violationCount);
            notifyStaffLocked(staff, violationCount);
        });
    }

    private void notifyStaffLocked(Staff staff, int violationCount) {
        if (!emailService.isConfigured()) {
            return;
        }
        String staffName = staff.getFullname() != null ? staff.getFullname() : ("NV #" + staff.getStaffId());
        String cinemaName = staff.getCinema() != null ? staff.getCinema().getName() : "(chưa gán rạp)";
        String subject = "[MovieZone] Cảnh báo: tài khoản nhân viên bị tự động khoá do nghi ngờ bị lợi dụng";
        String body = "<p>Hệ thống MovieZone vừa <b>tự động khoá</b> một tài khoản nhân viên bán vé do phát hiện hành vi " +
                "giữ ghế/đơn hàng bất thường lặp lại (" + violationCount + " lần trong 30 phút gần nhất) — " +
                "có thể do tài khoản bị lộ và bị người khác lợi dụng để giữ ghế, ngăn không cho khách hàng mua vé.</p>" +
                "<ul>" +
                "<li><b>Nhân viên:</b> " + staffName + " (mã NV: " + staff.getStaffId() + ")</li>" +
                "<li><b>Email đăng nhập:</b> " + staff.getEmail() + "</li>" +
                "<li><b>Rạp:</b> " + cinemaName + "</li>" +
                "</ul>" +
                "<p>Vui lòng kiểm tra lại hoạt động gần đây của tài khoản này. Nếu xác nhận đây là hành vi bất thường/tài khoản bị lộ, " +
                "hãy đổi mật khẩu trước khi mở khoá lại cho nhân viên. Nếu là nhầm lẫn, admin có thể mở khoá lại trong trang quản lý nhân viên.</p>";

        // Báo cho chính nhân viên bị khoá (để họ biết và đổi mật khẩu nếu nghi bị lộ tài khoản).
        try {
            if (staff.getEmail() != null && !staff.getEmail().isBlank()) {
                emailService.sendHtml(staff.getEmail(), subject, body);
            }
        } catch (MessagingException | RuntimeException e) {
            log.warn("Không gửi được email cảnh báo khoá tài khoản cho nhân viên {}: {}", staff.getStaffId(), e.getMessage());
        }

        // Báo cho admin của rạp mà nhân viên này đang làm việc.
        try {
            if (staff.getCinema() != null && staff.getCinema().getCinemaId() != null) {
                List<Staff> admins = staffRepository.findByCinema_CinemaIdAndRoleAndStatus(
                        staff.getCinema().getCinemaId(), "ADMIN", 1);
                for (Staff admin : admins) {
                    if (admin.getEmail() != null && !admin.getEmail().isBlank()
                            && !admin.getEmail().equalsIgnoreCase(staff.getEmail())) {
                        emailService.sendHtml(admin.getEmail(), subject, body);
                    }
                }
            }
        } catch (MessagingException | RuntimeException e) {
            log.warn("Không gửi được email cảnh báo khoá tài khoản cho admin rạp của nhân viên {}: {}",
                    staff.getStaffId(), e.getMessage());
        }
    }
}
