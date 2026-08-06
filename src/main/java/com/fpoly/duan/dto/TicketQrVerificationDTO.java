package com.fpoly.duan.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TicketQrVerificationDTO {
    private String orderCode;
    private String ticketCode;
    private Integer status;
    private String customerName;
    private String cinemaName;
    private String cinemaAddress;
    private String movieTitle;
    private String showtime;
    private String roomName;
    private String seatNumber;
    /** Thời điểm vé được quét vào rạp (HH:mm dd/MM/yyyy), null nếu chưa vào. */
    private String checkedInAt;
}
