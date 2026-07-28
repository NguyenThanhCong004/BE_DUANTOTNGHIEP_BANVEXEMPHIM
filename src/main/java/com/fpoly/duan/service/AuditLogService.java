package com.fpoly.duan.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.fpoly.duan.entity.AuditLog;
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Ghi nhật ký cho các thao tác quản trị quan trọng (Nhật ký hệ thống trên Dashboard Super Admin). */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /** Không bao giờ làm hỏng nghiệp vụ gọi nó — lỗi ghi log chỉ được log lại, không ném ra ngoài. */
    public void log(Staff actor, String action, String targetType, Integer targetId, String description) {
        try {
            AuditLog entry = AuditLog.builder()
                    .actorType("STAFF")
                    .actorId(actor != null ? actor.getStaffId() : null)
                    .actorName(actor != null ? actor.getFullname() : null)
                    .actorRole(normalizeRole(actor))
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .description(description)
                    .cinemaId(actor != null && actor.getCinema() != null ? actor.getCinema().getCinemaId() : null)
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Không ghi được audit log (action={}, actorId={}): {}",
                    action, actor != null ? actor.getStaffId() : null, e.getMessage(), e);
        }
    }

    private String normalizeRole(Staff actor) {
        if (actor == null || actor.getRole() == null) {
            return null;
        }
        return actor.getRole().trim().toUpperCase().replaceFirst("^ROLE_", "");
    }
}
