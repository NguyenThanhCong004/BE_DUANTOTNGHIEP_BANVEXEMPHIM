package com.fpoly.duan.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "oder_details_food")
public class OrderDetailFood {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oder_details_food_id")
    private Integer orderDetailFoodId;

    private Integer status;
    private Integer quantity;
    private Double price;

    @ManyToOne
    @JoinColumn(name = "order_online_id")
    private OrderOnline orderOnline;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;
}
