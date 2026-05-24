package com.fpoly.duan.dto;

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
public class RoomDTO {
    private Integer id;

    @NotBlank(message = "Tên phòng chiếu không được để trống")
    @Size(max = 100, message = "Tên phòng chiếu không được vượt quá 100 ký tự")
    private String name;

    private Integer status;

    @NotNull(message = "Rạp chiếu phim không được để trống")
    private Integer cinemaId;
}

