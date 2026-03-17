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

    private String name;
    private String description;
    private Double price;
    private String image;
    private Integer status;

    @ManyToOne
    @JoinColumn(name = "categories_products_id")
    private CategoryProduct category;
}
