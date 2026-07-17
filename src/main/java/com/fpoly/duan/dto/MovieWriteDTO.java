package com.fpoly.duan.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieWriteDTO {

    private List<Integer> genreIds;

    @NotBlank(message = "Tên phim không được để trống")
    @Size(max = 255, message = "Tên phim không được vượt quá 255 ký tự")
    private String title;

    private String description;

    @NotNull(message = "Thời lượng phim không được để trống")
    @Min(value = 1, message = "Thời lượng phim phải lớn hơn 0")
    private Integer duration;

    private Integer ageLimit;

    @NotNull(message = "Ngày khởi chiếu không được để trống")
    private LocalDate releaseDate;

    private String poster;
    private Integer status;

    @NotNull(message = "Giá vé cơ bản không được để trống")
    @Min(value = 0, message = "Giá vé không được âm")
    private Double basePrice;

    private String author;
    private String nation;
    private String content;
    private String banner;
}
