package com.fpoly.duan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CinemaDTO {
    private Integer cinemaId;

    @NotBlank(message = "Tên rạp không được để trống")
    @Size(max = 255, message = "Tên rạp không được vượt quá 255 ký tự")
    private String name;

    @NotBlank(message = "Địa chỉ rạp không được để trống")
    private String address;

    private Integer status;
}
