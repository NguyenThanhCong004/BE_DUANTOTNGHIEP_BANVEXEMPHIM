package com.fpoly.duan.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType;

    @Column(name = "actor_id")
    private Integer actorId;

    @Column(name = "actor_name", columnDefinition = "NVARCHAR(200)")
    private String actorName;

    @Column(name = "actor_role", length = 50)
    private String actorRole;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Integer targetId;

    @Column(name = "description", columnDefinition = "NVARCHAR(500)")
    private String description;

    @Column(name = "cinema_id")
    private Integer cinemaId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
