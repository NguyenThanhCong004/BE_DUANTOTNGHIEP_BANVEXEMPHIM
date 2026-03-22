package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "membership_ranks")
public class MembershipRank {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rank_id")
    private Integer rankId;

    @Column(name = "rank_name")
    private String rankName;

    @Column(name = "min_spending")
    private Double minSpending;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "discount_percent")
    private Double discountPercent;

    @Column(name = "bonus_point")
    private Integer bonusPoint;
}
