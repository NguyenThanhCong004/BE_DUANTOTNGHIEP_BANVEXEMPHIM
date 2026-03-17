package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "points_histories")
public class PointsHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_history_id")
    private Integer pointHistoryId;

    private LocalDate date;
    private String description;
    private Integer points;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}
