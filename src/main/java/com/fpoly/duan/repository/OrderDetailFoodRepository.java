package com.fpoly.duan.repository;

import java.util.Collection;
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

    /** Lấy bắp nước của nhiều đơn cùng lúc (1 query, JOIN FETCH product) — tránh N+1 khi liệt kê lịch sử giao dịch. */
    @Query("SELECT f FROM OrderDetailFood f JOIN FETCH f.orderOnline LEFT JOIN FETCH f.product " +
           "WHERE f.orderOnline.orderOnlineId IN :orderIds")
    List<OrderDetailFood> findByOrderOnline_OrderOnlineIdInWithProduct(@Param("orderIds") Collection<Integer> orderIds);

    @Query("SELECT COALESCE(SUM(od.quantity), 0) FROM OrderDetailFood od " +
           "JOIN od.orderOnline o " +
           "WHERE o.status = 1 AND o.staff.staffId = :staffId AND o.createdAt BETWEEN :start AND :end")
    Long countProductsByStaffBetween(@Param("staffId") Integer staffId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT p.name, SUM(od.quantity) FROM OrderDetailFood od " +
           "JOIN od.orderOnline o " +
           "JOIN od.product p " +
           "WHERE o.status = 1 AND o.staff.staffId = :staffId AND o.createdAt BETWEEN :start AND :end " +
           "GROUP BY p.name")
    List<Object[]> getProductsBreakdownByStaffBetween(@Param("staffId") Integer staffId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    boolean existsByProduct_ProductId(Integer productId);

    /** Số lượng + doanh thu theo từng sản phẩm (đơn hoàn tất) */
    @Query("SELECT od.product.productId, SUM(od.quantity), COALESCE(SUM(od.quantity * od.price), 0.0) " +
           "FROM OrderDetailFood od JOIN od.orderOnline o " +
           "WHERE o.status = 1 AND od.product IS NOT NULL " +
           "GROUP BY od.product.productId")
    List<Object[]> getProductSalesStats();

    /** Doanh thu theo loại sản phẩm — month=0 nghĩa là cả năm */
    @Query("SELECT cat.name, COALESCE(SUM(od.quantity * od.price), 0.0) " +
           "FROM OrderDetailFood od JOIN od.orderOnline o JOIN od.product p LEFT JOIN p.category cat " +
           "WHERE o.status = 1 AND YEAR(o.createdAt) = :year " +
           "AND (:month = 0 OR MONTH(o.createdAt) = :month) " +
           "GROUP BY cat.name")
    List<Object[]> getCategoryRevenueByMonth(@Param("year") int year, @Param("month") int month);
}
