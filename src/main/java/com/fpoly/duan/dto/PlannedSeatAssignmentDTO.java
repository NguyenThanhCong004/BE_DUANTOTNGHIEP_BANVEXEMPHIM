package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Dự kiến gán vé cũ (ticketId) sang 1 ghế cụ thể ở suất chiếu thay thế, giữ đúng loại ghế đã mua. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannedSeatAssignmentDTO {
    private Integer ticketId;
    private Integer seatId;
    private String seatLabel;
    private String seatTypeName;
}
