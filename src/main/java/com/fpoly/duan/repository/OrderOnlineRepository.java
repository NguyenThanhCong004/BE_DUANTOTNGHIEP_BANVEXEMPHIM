package com.fpoly.duan.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.OrderOnline;

@Repository
public interface OrderOnlineRepository extends JpaRepository<OrderOnline, Integer> {

    List<OrderOnline> findByUser_UserIdOrderByCreatedAtDesc(Integer userId);

    Optional<OrderOnline> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    @Query("SELECT COALESCE(SUM(o.finalAmount), 0.0) FROM OrderOnline o WHERE o.status = 1")
    Double sumTotalRevenue();

    @Query("SELECT COALESCE(SUM(o.finalAmount), 0.0) FROM OrderOnline o WHERE o.status = 1 AND o.createdAt BETWEEN :start AND :end")
    Double sumRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(o.finalAmount), 0.0) FROM OrderOnline o WHERE o.status = 1 AND o.staff.staffId = :staffId AND o.createdAt BETWEEN :start AND :end")
    Double sumRevenueByStaffBetween(@Param("staffId") Integer staffId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o.paymentMethod, SUM(o.finalAmount) FROM OrderOnline o " +
           "WHERE o.status = 1 AND o.staff.staffId = :staffId AND o.createdAt BETWEEN :start AND :end " +
           "GROUP BY o.paymentMethod")
    List<Object[]> getRevenueBreakdownByStaffBetween(@Param("staffId") Integer staffId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<OrderOnline> findTop10ByStaffStaffIdOrderByCreatedAtDesc(Integer staffId);

    @Query("SELECT MONTH(o.createdAt), SUM(o.finalAmount) " +
           "FROM OrderOnline o " +
           "WHERE o.status = 1 AND YEAR(o.createdAt) = :year " +
           "GROUP BY MONTH(o.createdAt) " +
           "ORDER BY MONTH(o.createdAt)")
    List<Object[]> getMonthlyRevenueByYear(@Param("year") int year);

    @Query("SELECT COALESCE(SUM(o.finalAmount), 0.0) " +
           "FROM OrderOnline o " +
           "WHERE o.status = 1 " +
           "AND o.user.userId = :userId " +
           "AND YEAR(o.createdAt) = :year")
    Double sumCompletedRevenueByUserAndYear(@Param("userId") Integer userId, @Param("year") int year);

    @Query("SELECT c.name, SUM(t.price), COUNT(t.ticketId) " +
           "FROM Ticket t " +
           "JOIN t.showtime s " +
           "JOIN s.room r " +
           "JOIN r.cinema c " +
           "WHERE t.orderOnline.status = 1 " +
           "GROUP BY c.name " +
           "ORDER BY SUM(t.price) DESC")
    List<Object[]> getCinemaRankings();
}