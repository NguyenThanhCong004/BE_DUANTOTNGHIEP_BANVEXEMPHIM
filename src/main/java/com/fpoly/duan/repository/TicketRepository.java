package com.fpoly.duan.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Ticket;
import java.time.LocalDateTime;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Integer> {

    // SỬA LỖI: Sử dụng Join tường minh (Explicit Join) cho OrderOnline 
    // Tránh Join ngầm định (Implicit Join) qua t.orderOnline.status có thể gây chậm hoặc lỗi Cross Join
    @Query("SELECT m.movieId, COALESCE(SUM(t.price), 0.0) " +
           "FROM Ticket t " +
           "JOIN t.showtime s " +
           "JOIN s.movie m " +
           "JOIN t.orderOnline o " +
           "WHERE o.status = 1 " +
           "GROUP BY m.movieId")
    List<Object[]> sumTicketRevenueByMovieId();

    @Query("SELECT t.seat.seatId FROM Ticket t " +
           "JOIN t.orderOnline o " +
           "WHERE t.showtime.showtimeId = :sid AND t.seat IS NOT NULL " +
           "AND o.status = 1")
    List<Integer> findBookedSeatIdsByPaidOrder(@Param("sid") Integer showtimeId);

    /** Ghế đang chờ thanh toán hoặc đã trả — dùng cho sơ đồ đặt vé. */
    @Query("SELECT t.seat.seatId FROM Ticket t " +
           "JOIN t.orderOnline o " +
           "WHERE t.showtime.showtimeId = :sid AND t.seat IS NOT NULL " +
           "AND o.status IN (0, 1)")
    List<Integer> findHeldSeatIdsByShowtime(@Param("sid") Integer showtimeId);

    @Query("SELECT COUNT(t) FROM Ticket t " +
           "JOIN t.orderOnline o " +
           "WHERE t.showtime.showtimeId = :sid AND t.seat.seatId IN :seatIds " +
           "AND o.status = 1")
    long countPaidTicketsForSeats(@Param("sid") Integer showtimeId, @Param("seatIds") Collection<Integer> seatIds);

    /** Đơn chờ thanh toán (0) hoặc đã trả (1) — không cho đặt trùng ghế. */
    @Query("SELECT COUNT(t) FROM Ticket t " +
           "JOIN t.orderOnline o " +
           "WHERE t.showtime.showtimeId = :sid AND t.seat.seatId IN :seatIds " +
           "AND o.status IN (0, 1)")
    long countHeldOrPaidTicketsForSeats(@Param("sid") Integer showtimeId, @Param("seatIds") Collection<Integer> seatIds);

    // SỬA LỖI: Join tường minh (Explicit Join)
    @Query("SELECT COUNT(t) FROM Ticket t JOIN t.orderOnline o WHERE o.status = 1")
    Long countAllPaidTickets();

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.orderOnline.status = 1 AND t.orderOnline.staff.staffId = :staffId AND t.orderOnline.createdAt BETWEEN :start AND :end")
    Long countTicketsByStaffBetweenJPQL(@Param("staffId") Integer staffId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Ticket> findByOrderOnline_OrderOnlineId(Integer orderOnlineId);

    long countBySeat_SeatId(Integer seatId);

    /** Trùng ghế theo vé (trạng thái vé trên Ticket), không dùng derived name ...AndStatus vì Seat cũng có `status`. */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Ticket t " +
           "WHERE t.showtime.showtimeId = :showtimeId AND t.seat.seatId = :seatId AND t.status = :status")
    boolean existsByShowtime_ShowtimeIdAndSeat_SeatIdAndStatus(
            @Param("showtimeId") Integer showtimeId,
            @Param("seatId") Integer seatId,
            @Param("status") int status);
}
