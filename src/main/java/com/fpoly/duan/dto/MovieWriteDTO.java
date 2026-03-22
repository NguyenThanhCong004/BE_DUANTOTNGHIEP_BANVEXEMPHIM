package com.fpoly.duan.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieWriteDTO {
    private Integer genreId;
    private String title;
    private String description;
    private Integer duration;
    private Integer ageLimit;
    private LocalDate releaseDate;
    private String poster;
    private Integer status;
    private Double basePrice;
    private String author;
    private String nation;
    private String content;
    private String banner;
}
