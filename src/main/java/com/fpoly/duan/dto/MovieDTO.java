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
public class MovieDTO {
    private Integer id;
    private String title;
    private String genre;
    private String posterUrl;
    private Integer duration;
    private Integer ageLimit;
    private LocalDate releaseDate;
    private Integer status;
    private Double basePrice;
    /** Đạo diễn */
    private String author;
    private String nation;
    private String description;
    /** Nội dung chi tiết */
    private String content;
    private String banner;
}

