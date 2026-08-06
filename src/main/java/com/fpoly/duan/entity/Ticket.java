package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "tickets", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tickets_ticket_code", columnNames = "ticket_code")
})
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Integer ticketId;

    private Integer status;
    private Double price;

    /** Đã soát vé (khách đã vào rạp) hay chưa — đánh dấu khi nhân viên xác nhận sau khi quét QR. */
    @Column(name = "checked_in", nullable = false)
    private Boolean checkedIn = false;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(name = "original_price")
    private Double originalPrice;

    @Column(name = "promotion_discount")
    private Double promotionDiscount;

    /** Mã vé ngắn, được ký HMAC từ thông tin rạp/phim/suất chiếu và ID vé. */
    @Column(name = "ticket_code", length = 40, unique = true)
    private String ticketCode;

    /** QR AES-GCM; chỉ BE mới giải mã và xác thực được. */
    @Column(name = "qr_token", length = 1500)
    private String qrToken;

    @ManyToOne
    @JoinColumn(name = "showtime_id")
    private Showtime showtime;

    @ManyToOne
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @ManyToOne
    @JoinColumn(name = "order_online_id")
    private OrderOnline orderOnline;
}
