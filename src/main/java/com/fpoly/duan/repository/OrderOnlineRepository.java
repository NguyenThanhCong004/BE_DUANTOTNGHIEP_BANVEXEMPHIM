package com.fpoly.duan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.OrderOnline;
import java.time.LocalDateTime;

@Repository
public interface OrderOnlineRepository extends JpaRepository<OrderOnline, Integer> {

    List<OrderOnline> findByUser_UserIdOrderByCreatedAtDesc(Integer userId);

    Optional<OrderOnline> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    @Query(value = "SELECT CAST(COALESCE(SUM(final_amount), 0) AS FLOAT) FROM orders_online WHERE status = 1", nativeQuery = true)
    Object sumTotalRevenue();

    @Query(value = "SELECT CAST(COALESCE(SUM(final_amount), 0) AS FLOAT) FROM orders_online WHERE status = 1 AND created_at BETWEEN :start AND :end", nativeQuery = true)
    Object sumRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT CAST(MONTH(created_at) AS INT) as month, CAST(SUM(final_amount) AS FLOAT) as total " +
           "FROM orders_online " +
           "WHERE status = 1 AND YEAR(created_at) = :year " +
           "GROUP BY MONTH(created_at) " +
           "ORDER BY MONTH(created_at)", nativeQuery = true)
    List<Object[]> getMonthlyRevenueByYear(@Param("year") int year);

    @Query(value = "SELECT TOP 5 CAST(c.name AS NVARCHAR(MAX)), CAST(SUM(t.price) AS FLOAT) as revenue, CAST(COUNT(t.ticket_id) AS BIGINT) as count " +
           "FROM tickets t " +
           "JOIN showtimes s ON t.showtime_id = s.showtime_id " +
           "JOIN rooms r ON s.room_id = r.room_id " +
           "JOIN cinemas c ON r.cinema_id = c.cinema_id " +
           "WHERE t.order_online_id IN (SELECT order_online_id FROM orders_online WHERE status = 1) " +
           "GROUP BY c.name " +
           "ORDER BY revenue DESC", nativeQuery = true)
    List<Object[]> getCinemaRankings();
}
