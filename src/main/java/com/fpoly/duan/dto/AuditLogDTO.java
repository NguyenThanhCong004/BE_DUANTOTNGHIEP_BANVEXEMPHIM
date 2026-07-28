package com.fpoly.duan.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private Integer id;
    private String actorName;
    private String actorRole;
    private String action;
    private String targetType;
    private Integer targetId;
    private String description;
    private LocalDateTime createdAt;
}
