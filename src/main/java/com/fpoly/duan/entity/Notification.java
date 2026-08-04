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
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "title", nullable = false, columnDefinition = "NVARCHAR(255)")
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String content;

    /** null = phát cho toàn bộ rạp. */
    @Column(name = "cinema_id")
    private Integer cinemaId;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
