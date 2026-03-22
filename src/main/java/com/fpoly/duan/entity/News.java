package com.fpoly.duan.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "news")
public class News {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_id")
    private Integer newsId;

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String content;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String image;
    private Integer status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
