package com.fpoly.duan.util;

import com.fpoly.duan.entity.Seat;

/** Nhãn ghế hiển thị (VD "A1") = hàng + số ghế. Dùng để snapshot vào {@code Ticket.seatLabel}
 * mỗi khi gắn/đổi ghế cho vé, tách khỏi bảng seats để hóa đơn không mất tên ghế nếu ghế bị xóa. */
public final class SeatLabel {
    private SeatLabel() {
    }

    public static String of(Seat seat) {
        if (seat == null) {
            return null;
        }
        String row = seat.getRow() != null ? seat.getRow() : "";
        String number = seat.getNumber() != null ? seat.getNumber() : "";
        return row + number;
    }
}
