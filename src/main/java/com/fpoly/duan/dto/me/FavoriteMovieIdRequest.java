package com.fpoly.duan.dto.me;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Thêm phim yêu thích")
public record FavoriteMovieIdRequest(
        @NotNull @Positive @Schema(description = "movie_id") Integer movieId) {
}
