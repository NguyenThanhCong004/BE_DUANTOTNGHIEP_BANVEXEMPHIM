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

    @Query("SELECT t.showtime.movie.movieId, COALESCE(SUM(t.price), 0) FROM Ticket t "
            + "WHERE t.showtime IS NOT NULL AND t.orderOnline IS NOT NULL AND t.orderOnline.status = 1 "
            + "GROUP BY t.showtime.movie.movieId")
    List<Object[]> sumTicketRevenueByMovieId();

    @Query("SELECT t.seat.seatId FROM Ticket t WHERE t.showtime.showtimeId = :sid AND t.seat IS NOT NULL "
            + "AND t.orderOnline IS NOT NULL AND t.orderOnline.status = 1")
    List<Integer> findBookedSeatIdsByPaidOrder(@Param("sid") Integer showtimeId);

    @Query("SELECT t.seat.seatId FROM Ticket t WHERE t.showtime.showtimeId = :sid AND t.seat IS NOT NULL "
            + "AND t.orderOnline IS NOT NULL AND t.orderOnline.status IN (0, 1)")
    List<Integer> findHeldSeatIdsByShowtime(@Param("sid") Integer showtimeId);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.showtime.showtimeId = :sid AND t.seat.seatId IN :seatIds "
            + "AND t.orderOnline IS NOT NULL AND t.orderOnline.status = 1")
    long countPaidTicketsForSeats(@Param("sid") Integer showtimeId, @Param("seatIds") Collection<Integer> seatIds);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.showtime.showtimeId = :sid AND t.seat.seatId IN :seatIds "
            + "AND t.orderOnline IS NOT NULL AND t.orderOnline.status IN (0, 1)")
    long countHeldOrPaidTicketsForSeats(@Param("sid") Integer showtimeId, @Param("seatIds") Collection<Integer> seatIds);

    @Query(value = "SELECT CAST(COUNT(*) AS BIGINT) FROM tickets t JOIN orders_online o ON t.order_online_id = o.order_online_id WHERE o.status = 1", nativeQuery = true)
    Object countAllPaidTickets();

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.orderOnline.status = 1 AND t.orderOnline.staff.staffId = :staffId AND t.orderOnline.createdAt BETWEEN :start AND :end")
    Long countTicketsByStaffBetweenJPQL(@Param("staffId") Integer staffId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Ticket> findByOrderOnline_OrderOnlineId(Integer orderOnlineId);

    boolean existsByShowtime_ShowtimeIdAndSeat_SeatIdAndStatus(Integer showtimeId, Integer seatId, Integer status);
}
