package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CinemaDetailDTO {
    private String cinemaName;
    private List<MovieRev> topMovies;
    private List<Recommendation> recommendations;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MovieRev {
        private String title;
        private Double revenue;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Recommendation {
        private String movieTitle;
        private String reason;
        private String action;
    }
}
