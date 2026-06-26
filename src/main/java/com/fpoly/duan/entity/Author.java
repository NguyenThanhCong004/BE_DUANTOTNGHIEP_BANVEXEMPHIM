package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "authors", uniqueConstraints = @UniqueConstraint(name = "uk_authors_name", columnNames = "name"))
public class Author {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "author_id")
    private Integer authorId;

    @Column(nullable = false, length = 200, columnDefinition = "NVARCHAR(200)")
    private String name;

    /** 1: đang dùng, 0: ngừng dùng nhưng giữ lịch sử phim. */
    @Column(nullable = false)
    private Integer status = 1;
}
