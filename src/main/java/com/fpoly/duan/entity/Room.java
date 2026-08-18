package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "rooms")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Integer roomId;

    private String name;

    // 0 = Ngừng hoạt động, 1 = Hoạt động, 2 = Đóng tạm thời (bảo trì/hỏng ghế)
    private Integer status;

    @ManyToOne
    @JoinColumn(name = "cinema_id")
    private Cinema cinema;

    /** Lý do đóng phòng tạm thời — hiển thị cho admin và đưa vào email xin lỗi khách hàng. */
    @Column(name = "close_reason", columnDefinition = "NVARCHAR(500)")
    private String closeReason;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
