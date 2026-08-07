package com.fpoly.duan.dto;

import java.time.LocalDate;
import java.util.List;

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
    private List<String> genres;
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
    /** Điểm đánh giá trung bình từ người dùng (0–5) */
    private Double averageRating;
}

