package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "reviews")
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Integer reviewId;

    private Integer rating;
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String comment;

    @ManyToOne
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;
}
