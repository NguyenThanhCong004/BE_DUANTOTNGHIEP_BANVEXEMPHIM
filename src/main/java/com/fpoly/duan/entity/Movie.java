package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "movie")
public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id")
    private Integer movieId;

    private String title;

    /** NVARCHAR(MAX): mô tả tiếng Việt (SQL Server) — tránh kiểu TEXT (không Unicode). */
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String content;

    private Integer duration;
    private String author;
    private String nation;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "age_limit")
    private Integer ageLimit;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String poster;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String banner;
    private Integer status;

    @Column(name = "base_price")
    private Double basePrice;

    @ManyToOne
    @JoinColumn(name = "genre_id")
    private Genre genre;
}
