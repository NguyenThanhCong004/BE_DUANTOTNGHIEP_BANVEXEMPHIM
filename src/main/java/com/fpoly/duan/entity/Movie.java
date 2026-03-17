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

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String content;

    private Integer duration;
    private String author;
    private String nation;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "age_limit")
    private Integer ageLimit;

    private String poster;
    private String banner;
    private Integer status;

    @Column(name = "base_price")
    private Double basePrice;

    @ManyToOne
    @JoinColumn(name = "genre_id")
    private Genre genre;
}
