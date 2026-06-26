package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "nations", uniqueConstraints = @UniqueConstraint(name = "uk_nations_name", columnNames = "name"))
public class Nation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "nation_id")
    private Integer nationId;

    @Column(nullable = false, length = 100, columnDefinition = "NVARCHAR(100)")
    private String name;

    @Column(nullable = false)
    private Integer status = 1;
}
