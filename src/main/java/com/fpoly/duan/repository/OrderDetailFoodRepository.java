package com.fpoly.duan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.OrderDetailFood;
import java.time.LocalDateTime;

@Repository
public interface OrderDetailFoodRepository extends JpaRepository<OrderDetailFood, Integer> {

    List<OrderDetailFood> findByOrderOnline_OrderOnlineId(Integer orderOnlineId);

    @Query("SELECT CAST(COALESCE(SUM(od.quantity), 0) AS Long) FROM OrderDetailFood od " +
           "WHERE od.orderOnline.status = 1 AND od.orderOnline.staff.staffId = :staffId AND od.orderOnline.createdAt BETWEEN :start AND :end")
    Long countProductsByStaffBetween(@Param("staffId") Integer staffId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT od.product.name, SUM(od.quantity) FROM OrderDetailFood od " +
           "WHERE od.orderOnline.status = 1 AND od.orderOnline.staff.staffId = :staffId AND od.orderOnline.createdAt BETWEEN :start AND :end " +
           "GROUP BY od.product.name")
    List<Object[]> getProductsBreakdownByStaffBetween(@Param("staffId") Integer staffId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    boolean existsByProduct_ProductId(Integer productId);
}
