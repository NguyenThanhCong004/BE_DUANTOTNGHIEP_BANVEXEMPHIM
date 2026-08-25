package com.fpoly.duan.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoMoveToSpareRoomRequest {
    /** null = không giới hạn, dời hết suất "Sắp chiếu" của phòng. */
    private LocalDateTime windowEnd;
}
