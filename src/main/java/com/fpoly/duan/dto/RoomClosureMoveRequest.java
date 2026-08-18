package com.fpoly.duan.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomClosureMoveRequest {

    @NotNull(message = "Vui lòng chọn suất chiếu thay thế")
    private Integer targetShowtimeId;
}
