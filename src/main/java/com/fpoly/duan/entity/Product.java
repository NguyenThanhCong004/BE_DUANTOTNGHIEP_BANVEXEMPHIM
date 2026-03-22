package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Integer productId;

    @Column(length = 300)
    private String name;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    private Double price;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String image;
    private Integer status;

    @ManyToOne
    @JoinColumn(name = "categories_products_id")
    private CategoryProduct category;
}
