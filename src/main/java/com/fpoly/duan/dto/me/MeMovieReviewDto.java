package com.fpoly.duan.dto.me;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeMovieReviewDto {
    private Integer reviewId;
    private Integer movieId;
    private Integer ticketId;
    private Integer rating;
    private String comment;
    private String userName;
    private String userAvatar;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
