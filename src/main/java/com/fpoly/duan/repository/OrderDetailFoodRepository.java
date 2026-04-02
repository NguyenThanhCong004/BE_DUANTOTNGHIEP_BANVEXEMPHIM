package com.fpoly.duan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.OrderDetailFood;

@Repository
public interface OrderDetailFoodRepository extends JpaRepository<OrderDetailFood, Integer> {

    List<OrderDetailFood> findByOrderOnline_OrderOnlineId(Integer orderOnlineId);

    boolean existsByProduct_ProductId(Integer productId);
}
