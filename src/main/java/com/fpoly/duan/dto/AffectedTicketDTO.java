package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 1 vé đang thuộc suất chiếu "Sắp chiếu" của 1 phòng vừa bị đóng — dùng cho màn hình xử lý dời vé. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AffectedTicketDTO {
    private Integer ticketId;
    private String movieTitle;
    private Integer showtimeId;
    private String showtimeStart;
    private String seatLabel;
    private Integer seatTypeId;
    private String seatTypeName;
}
