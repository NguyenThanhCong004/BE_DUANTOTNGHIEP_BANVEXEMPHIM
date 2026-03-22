package com.fpoly.duan.dto.me;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeFavoriteMovieDto {
    private Integer favoriteId;
    private Integer movieId;
    private String title;
    private String poster;
    private Integer duration;
    private Integer status;
}
