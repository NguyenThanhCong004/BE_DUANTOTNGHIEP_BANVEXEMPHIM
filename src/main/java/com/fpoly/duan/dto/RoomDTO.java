package com.fpoly.duan.dto;

import java.time.LocalDateTime;

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

    // 0 = Ngừng hoạt động, 1 = Hoạt động, 2 = Đóng tạm thời (bảo trì/hỏng ghế)
    private Integer status;

    @NotNull(message = "Rạp chiếu phim không được để trống")
    private Integer cinemaId;

    /** Chỉ có giá trị khi status = 2 (đóng tạm thời). */
    private String closeReason;
    private LocalDateTime closedAt;

    private Integer roomTypeId;
    private String roomTypeName;
    private Integer standardSeatCount;
    private Integer vipSeatCount;
    private Integer coupleSeatCount;
}

