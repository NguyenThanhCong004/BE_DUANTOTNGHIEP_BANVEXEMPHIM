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

       /**
        * Khách đã từng có đơn hàng "thuộc" một rạp — suy ra rạp theo đúng thứ tự ưu tiên dùng khi
        * hiển thị (orders_online.cinema_id trước, rồi tới rạp của nhân viên xử lý đơn, cuối cùng
        * là rạp suy từ vé/suất chiếu), vì một số đơn cũ không lưu trực tiếp cinema_id.
        */
       @Query(value = "SELECT DISTINCT o.user_id FROM orders_online o " +
                     "LEFT JOIN staff s ON o.staff_id = s.staff_id " +
                     "LEFT JOIN tickets t ON t.order_online_id = o.order_online_id " +
                     "LEFT JOIN showtimes st ON t.showtime_id = st.showtime_id " +
                     "LEFT JOIN rooms r ON st.room_id = r.room_id " +
                     "WHERE o.user_id IS NOT NULL " +
                     "AND COALESCE(o.cinema_id, s.cinema_id, r.cinema_id) = :cinemaId", nativeQuery = true)
       List<Integer> findDistinctUserIdsByCinema(@Param("cinemaId") Integer cinemaId);

       Optional<OrderOnline> findByOrderCode(String orderCode);

       Optional<OrderOnline> findByReceiptToken(String receiptToken);

       boolean existsByOrderCode(String orderCode);

       @Query("SELECT COALESCE(SUM(o.finalAmount), 0.0) FROM OrderOnline o WHERE o.status = 1")
       Double sumTotalRevenue();

       @Query("SELECT COALESCE(SUM(o.finalAmount), 0.0) FROM OrderOnline o WHERE o.status = 1 AND o.createdAt BETWEEN :start AND :end")
       Double sumRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

       @Query("SELECT COALESCE(SUM(o.finalAmount), 0.0) FROM OrderOnline o WHERE o.status = 1 AND o.staff.staffId = :staffId AND o.createdAt BETWEEN :start AND :end")
       Double sumRevenueByStaffBetween(@Param("staffId") Integer staffId, @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);

       @Query("SELECT COALESCE(SUM(o.finalAmount), 0.0) FROM OrderOnline o WHERE o.status = 1 AND o.cinema.cinemaId = :cinemaId AND o.createdAt BETWEEN :start AND :end")
       Double sumRevenueByCinemaBetween(@Param("cinemaId") Integer cinemaId, @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end);

       @Query("SELECT COUNT(DISTINCT o.user.userId) FROM OrderOnline o " +
                     "WHERE o.status = 1 AND o.cinema.cinemaId = :cinemaId AND o.user IS NOT NULL " +
                     "AND o.createdAt BETWEEN :start AND :end")
       Long countDistinctCustomersByCinemaBetween(@Param("cinemaId") Integer cinemaId,
                     @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

       @Query("SELECT o.paymentMethod, SUM(o.finalAmount) FROM OrderOnline o WHERE o.status = 1 GROUP BY o.paymentMethod")
       List<Object[]> getRevenueBreakdownAll();

       /** Doanh thu theo ngày (yyyy-MM-dd), toàn hệ thống, kể từ mốc thời gian cho trước. */
       @Query(value = "SELECT CONVERT(varchar(10), o.created_at, 23) AS d, SUM(o.final_amount) " +
                     "FROM orders_online o WHERE o.status = 1 AND o.created_at >= :start " +
                     "GROUP BY CONVERT(varchar(10), o.created_at, 23) " +
                     "ORDER BY d", nativeQuery = true)
       List<Object[]> getDailyRevenueSince(@Param("start") LocalDateTime start);

       /** Doanh thu theo ngày, toàn hệ thống, trong khoảng [start, end) cho trước (bộ lọc từ ngày - đến ngày). */
       @Query(value = "SELECT CONVERT(varchar(10), o.created_at, 23) AS d, SUM(o.final_amount) " +
                     "FROM orders_online o WHERE o.status = 1 AND o.created_at >= :start AND o.created_at < :end " +
                     "GROUP BY CONVERT(varchar(10), o.created_at, 23) " +
                     "ORDER BY d", nativeQuery = true)
       List<Object[]> getDailyRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

       /** Doanh thu theo ngày cho một rạp, kể từ mốc thời gian cho trước. */
       @Query(value = "SELECT CONVERT(varchar(10), o.created_at, 23) AS d, SUM(o.final_amount) " +
                     "FROM orders_online o WHERE o.status = 1 AND o.cinema_id = :cinemaId AND o.created_at >= :start " +
                     "GROUP BY CONVERT(varchar(10), o.created_at, 23) " +
                     "ORDER BY d", nativeQuery = true)
       List<Object[]> getDailyRevenueByCinemaSince(@Param("cinemaId") Integer cinemaId,
                     @Param("start") LocalDateTime start);

       /** Doanh thu theo năm, toàn hệ thống, kể từ năm cho trước. */
       @Query("SELECT YEAR(o.createdAt), SUM(o.finalAmount) FROM OrderOnline o " +
                     "WHERE o.status = 1 AND YEAR(o.createdAt) >= :sinceYear " +
                     "GROUP BY YEAR(o.createdAt) ORDER BY YEAR(o.createdAt)")
       List<Object[]> getYearlyRevenueSince(@Param("sinceYear") int sinceYear);

       @Query("SELECT o.paymentMethod, SUM(o.finalAmount) FROM OrderOnline o " +
                     "WHERE o.status = 1 AND o.staff.staffId = :staffId AND o.createdAt BETWEEN :start AND :end " +
                     "GROUP BY o.paymentMethod")
       List<Object[]> getRevenueBreakdownByStaffBetween(@Param("staffId") Integer staffId,
                     @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

       List<OrderOnline> findTop10ByStaffStaffIdOrderByCreatedAtDesc(Integer staffId);

       List<OrderOnline> findTop10ByStaffStaffIdAndCreatedAtBetweenOrderByCreatedAtDesc(
               Integer staffId, LocalDateTime start, LocalDateTime end);

       @Query("SELECT MONTH(o.createdAt), SUM(o.finalAmount) " +
                     "FROM OrderOnline o " +
                     "WHERE o.status = 1 AND YEAR(o.createdAt) = :year " +
                     "GROUP BY MONTH(o.createdAt) " +
                     "ORDER BY MONTH(o.createdAt)")
       List<Object[]> getMonthlyRevenueByYear(@Param("year") int year);

       @Query("SELECT COALESCE(SUM(o.finalAmount), 0.0) FROM OrderOnline o WHERE o.user.userId = :userId AND o.status = 1 AND YEAR(o.createdAt) = :year")
       Double sumCompletedRevenueByUserAndYear(@Param("userId") Integer userId, @Param("year") int year);

    /**
     * Phân trang "lịch sử giao dịch" — gộp 2 nguồn (đơn hàng + sự kiện điểm) sắp theo thời gian giảm
     * dần, trả về (source_type, id) của đúng 1 trang. JPQL không hỗ trợ UNION nên phải dùng native SQL.
     * Service sẽ tách orderIds/pointIds rồi fetch chi tiết theo lô (không N+1), giữ đúng thứ tự này.
     */
    @Query(value = "SELECT source_type, id, created_at FROM (" +
           "  SELECT 'ORDER' AS source_type, order_online_id AS id, created_at FROM orders_online WHERE user_id = :userId " +
           "  UNION ALL " +
           "  SELECT 'POINTS' AS source_type, point_history_id AS id, CAST(date AS datetime2) AS created_at FROM points_histories WHERE user_id = :userId" +
           ") t ORDER BY created_at DESC " +
           "OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY", nativeQuery = true)
    List<Object[]> findTransactionPageRefs(@Param("userId") Integer userId, @Param("offset") int offset, @Param("limit") int limit);

    @Query(value = "SELECT " +
           "(SELECT COUNT(*) FROM orders_online WHERE user_id = :userId) + " +
           "(SELECT COUNT(*) FROM points_histories WHERE user_id = :userId)", nativeQuery = true)
    long countTransactionRefs(@Param("userId") Integer userId);

    @Query("SELECT o.user.userId, COUNT(o) FROM OrderOnline o WHERE o.status = 1 AND o.user IS NOT NULL GROUP BY o.user.userId")
    List<Object[]> countCompletedOrdersPerUser();

    /** Tổng chi tiêu toàn thời gian mỗi user (status=1) */
    @Query("SELECT o.user.userId, COALESCE(SUM(o.finalAmount), 0.0) FROM OrderOnline o WHERE o.status = 1 AND o.user IS NOT NULL GROUP BY o.user.userId")
    List<Object[]> sumAllTimeSpendingPerUser();

    /** Tổng chi tiêu trong năm hiện tại mỗi user (để tính hạng) */
    @Query("SELECT o.user.userId, COALESCE(SUM(o.finalAmount), 0.0) FROM OrderOnline o WHERE o.status = 1 AND o.user IS NOT NULL AND YEAR(o.createdAt) = :year GROUP BY o.user.userId")
    List<Object[]> sumCurrentYearSpendingPerUser(@Param("year") int year);

    /** Số đơn + doanh thu mỗi nhân viên xử lý (status=1) */
    @Query("SELECT o.staff.staffId, COUNT(o), COALESCE(SUM(o.finalAmount), 0.0) FROM OrderOnline o WHERE o.status = 1 AND o.staff IS NOT NULL GROUP BY o.staff.staffId")
    List<Object[]> getStaffOrderStats();

    /** Thống kê khách hàng theo rạp: cinemaId, cinemaName, unique customers, total orders, total revenue */
    @Query("SELECT o.cinema.cinemaId, o.cinema.name, COUNT(DISTINCT o.user.userId), COUNT(o), COALESCE(SUM(o.finalAmount), 0.0) " +
           "FROM OrderOnline o WHERE o.status = 1 AND o.cinema IS NOT NULL " +
           "GROUP BY o.cinema.cinemaId, o.cinema.name")
    List<Object[]> getCustomerStatsByCinema();

    /** Danh sách userId đã đặt tại rạp cụ thể (status=1) */
    @Query("SELECT DISTINCT o.user.userId FROM OrderOnline o WHERE o.status = 1 AND o.cinema IS NOT NULL AND o.cinema.cinemaId = :cinemaId AND o.user IS NOT NULL")
    List<Integer> getUserIdsByCinema(@Param("cinemaId") Integer cinemaId);

    @Query(value = "SELECT COALESCE(c_ticket.name, c_staff.name) as cinema_name, " +
           "SUM(o.final_amount) as revenue, " +
           "COALESCE(SUM(t_count.c), 0) as total_tickets " +
           "FROM orders_online o " +
           "LEFT JOIN ( " +
           "    SELECT order_online_id, MIN(showtime_id) as mid, COUNT(ticket_id) as c " +
           "    FROM tickets " +
           "    GROUP BY order_online_id " +
           ") t_count ON o.order_online_id = t_count.order_online_id " +
           "LEFT JOIN showtimes s ON t_count.mid = s.showtime_id " +
           "LEFT JOIN rooms r ON s.room_id = r.room_id " +
           "LEFT JOIN cinemas c_ticket ON r.cinema_id = c_ticket.cinema_id " +
           "LEFT JOIN staff st ON o.staff_id = st.staff_id " +
           "LEFT JOIN cinemas c_staff ON st.cinema_id = c_staff.cinema_id " +
           "WHERE o.status = 1 AND YEAR(o.created_at) = :year AND MONTH(o.created_at) = :month " +
           "AND (c_ticket.cinema_id IS NOT NULL OR c_staff.cinema_id IS NOT NULL) " +
           "GROUP BY COALESCE(c_ticket.name, c_staff.name) " +
           "ORDER BY revenue DESC", nativeQuery = true)
    List<Object[]> getCinemaRankings(@Param("year") int year, @Param("month") int month);
}
