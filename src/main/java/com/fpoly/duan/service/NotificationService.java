package com.fpoly.duan.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fpoly.duan.dto.CreateNotificationRequest;
import com.fpoly.duan.dto.NotificationDTO;
import com.fpoly.duan.entity.Notification;
import com.fpoly.duan.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

/** Thông báo nội bộ hiển thị trên Dashboard Admin/Super Admin. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int DEFAULT_LIMIT = 10;

    private final NotificationRepository notificationRepository;

    public List<NotificationDTO> listForCinema(Integer cinemaId, Integer limit) {
        int cap = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;
        List<Notification> rows = cinemaId != null
                ? notificationRepository.findActiveForCinema(cinemaId)
                : notificationRepository.findTop10ByActiveTrueOrderByCreatedAtDesc();
        return rows.stream()
                .limit(cap)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public NotificationDTO create(CreateNotificationRequest request, Integer createdByStaffId) {
        Notification entity = Notification.builder()
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .cinemaId(request.getCinemaId())
                .createdBy(createdByStaffId)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        return toDTO(notificationRepository.save(entity));
    }

    private NotificationDTO toDTO(Notification n) {
        return NotificationDTO.builder()
                .id(n.getId())
                .title(n.getTitle())
                .content(n.getContent())
                .cinemaId(n.getCinemaId())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
