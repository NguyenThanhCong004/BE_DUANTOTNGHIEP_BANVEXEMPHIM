package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Chụp lại thông tin vé TRƯỚC khi bị dời/hủy do đóng phòng — dùng để soạn email xin lỗi (suất/ghế cũ). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketRescheduleSnapshotDTO {
    private Integer ticketId;
    private String movieTitle;
    private String seatLabel;
    private String showtimeStart;
}
