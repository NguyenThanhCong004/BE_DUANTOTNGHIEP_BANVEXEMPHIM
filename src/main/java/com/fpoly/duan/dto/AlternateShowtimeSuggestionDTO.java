package com.fpoly.duan.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 1 suất chiếu thay thế khả thi (cùng phim, cùng rạp, còn đủ ghế đúng loại) cho 1 đơn bị ảnh hưởng. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlternateShowtimeSuggestionDTO {
    private Integer showtimeId;
    private String movieTitle;
    private Integer roomId;
    private String roomName;
    private String startTime;
    /** Ghế dự kiến gán cho từng vé của đơn (ticketId -> nhãn ghế mới), theo đúng loại ghế cũ. */
    private List<PlannedSeatAssignmentDTO> plannedSeats;
}
