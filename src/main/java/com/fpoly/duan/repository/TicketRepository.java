package com.fpoly.duan.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    /** Ghế đang chờ thanh toán trong hạn hoặc đã trả — dùng cho sơ đồ đặt vé. */
    @Query("SELECT t.seat.seatId FROM Ticket t " +
           "JOIN t.orderOnline o " +
           "WHERE t.showtime.showtimeId = :sid AND t.seat IS NOT NULL " +
           "AND t.status IN (0, 1) " +
           "AND (o.status = 1 OR (o.status = 0 AND o.createdAt >= :pendingSince))")
    List<Integer> findHeldSeatIdsByShowtime(
            @Param("sid") Integer showtimeId,
            @Param("pendingSince") LocalDateTime pendingSince);

    @Query("SELECT COUNT(t) FROM Ticket t " +
           "JOIN t.orderOnline o " +
           "WHERE t.showtime.showtimeId = :sid AND t.seat.seatId IN :seatIds " +
           "AND o.status = 1")
    long countPaidTicketsForSeats(@Param("sid") Integer showtimeId, @Param("seatIds") Collection<Integer> seatIds);

    /** Đơn chờ thanh toán trong hạn (0) hoặc đã trả (1) — không cho đặt trùng ghế. */
    @Query("SELECT COUNT(t) FROM Ticket t " +
           "JOIN t.orderOnline o " +
           "WHERE t.showtime.showtimeId = :sid AND t.seat.seatId IN :seatIds " +
           "AND t.status IN (0, 1) " +
           "AND (o.status = 1 OR (o.status = 0 AND o.createdAt >= :pendingSince))")
    long countHeldOrPaidTicketsForSeats(
            @Param("sid") Integer showtimeId,
            @Param("seatIds") Collection<Integer> seatIds,
            @Param("pendingSince") LocalDateTime pendingSince);

    // SỬA LỖI: Join tường minh (Explicit Join)
    @Query("SELECT COUNT(t) FROM Ticket t JOIN t.orderOnline o WHERE o.status = 1")
    Long countAllPaidTickets();

    /** Top phim theo doanh thu vé (toàn hệ thống), sắp xếp giảm dần — cắt limit ở tầng service. */
    @Query("SELECT m.title, COUNT(t), COALESCE(SUM(t.price), 0.0) " +
           "FROM Ticket t JOIN t.showtime s JOIN s.movie m JOIN t.orderOnline o " +
           "WHERE o.status = 1 " +
           "GROUP BY m.title " +
           "ORDER BY SUM(t.price) DESC")
    List<Object[]> getTopMoviesByRevenue();

    /** Top phim theo doanh thu vé, giới hạn theo rạp. */
    @Query("SELECT m.title, COUNT(t), COALESCE(SUM(t.price), 0.0) " +
           "FROM Ticket t JOIN t.showtime s JOIN s.movie m JOIN s.room r JOIN t.orderOnline o " +
           "WHERE o.status = 1 AND r.cinema.cinemaId = :cinemaId " +
           "GROUP BY m.title " +
           "ORDER BY SUM(t.price) DESC")
    List<Object[]> getTopMoviesByRevenueForCinema(@Param("cinemaId") Integer cinemaId);

    /** Số vé đã bán (theo suất chiếu diễn ra trong khoảng thời gian) — dùng để tính tỷ lệ ghế đã bán hôm nay. */
    @Query("SELECT COUNT(t) FROM Ticket t JOIN t.showtime s JOIN t.orderOnline o " +
           "WHERE o.status = 1 AND s.startTime >= :start AND s.startTime < :end")
    Long countSoldSeatsForShowtimesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** Số vé đã bán cho các suất chiếu của một rạp trong khoảng thời gian (ghế đã bán trong ngày, theo giờ chiếu). */
    @Query("SELECT COUNT(t) FROM Ticket t JOIN t.showtime s JOIN s.room r JOIN t.orderOnline o " +
           "WHERE o.status = 1 AND r.cinema.cinemaId = :cinemaId AND s.startTime >= :start AND s.startTime < :end")
    Long countSoldSeatsForCinemaShowtimesBetween(@Param("cinemaId") Integer cinemaId,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** Số vé bán ra (theo thời điểm đặt đơn) cho một rạp trong khoảng thời gian — dùng cho KPI "vé bán hôm nay". */
    @Query("SELECT COUNT(t) FROM Ticket t JOIN t.orderOnline o " +
           "WHERE o.status = 1 AND o.cinema.cinemaId = :cinemaId AND o.createdAt BETWEEN :start AND :end")
    Long countTicketsSoldByCinemaBetween(@Param("cinemaId") Integer cinemaId,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** Tỷ lệ ghế theo loại (VIP/thường/đôi) đã bán cho các suất chiếu hôm nay của một rạp. */
    @Query("SELECT st.name, st.coupleSeat, COUNT(t) " +
           "FROM Ticket t JOIN t.seat seat JOIN seat.seatType st " +
           "JOIN t.showtime s JOIN s.room r JOIN t.orderOnline o " +
           "WHERE o.status = 1 AND r.cinema.cinemaId = :cinemaId " +
           "AND s.startTime >= :start AND s.startTime < :end " +
           "GROUP BY st.name, st.coupleSeat")
    List<Object[]> getSeatTypeRatioForCinemaBetween(@Param("cinemaId") Integer cinemaId,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** Vé bán theo giờ trong ngày (theo thời điểm đặt đơn) cho một rạp — SQL Server DATEPART. */
    @Query(value = "SELECT DATEPART(HOUR, o.created_at) AS hr, COUNT(t.ticket_id) " +
           "FROM tickets t JOIN orders_online o ON t.order_online_id = o.order_online_id " +
           "WHERE o.status = 1 AND o.cinema_id = :cinemaId " +
           "AND o.created_at >= :start AND o.created_at < :end " +
           "GROUP BY DATEPART(HOUR, o.created_at) " +
           "ORDER BY hr", nativeQuery = true)
    List<Object[]> getTicketsByHourForCinema(@Param("cinemaId") Integer cinemaId,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(t) FROM Ticket t JOIN t.orderOnline o JOIN o.staff st WHERE o.status = 1 AND st.staffId = :staffId AND o.createdAt BETWEEN :start AND :end")
    Long countTicketsByStaffBetweenJPQL(@Param("staffId") Integer staffId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Ticket> findByOrderOnline_OrderOnlineId(Integer orderOnlineId);

    /** Vé đang hoạt động (đơn chưa hủy) của các suất "Sắp chiếu" trong 1 phòng — dùng cho luồng đóng phòng/dời vé. */
    @Query("SELECT t FROM Ticket t " +
           "JOIN t.orderOnline o " +
           "WHERE t.showtime.room.roomId = :roomId AND t.showtime.startTime > :now " +
           "AND o.status IN (0, 1) " +
           "ORDER BY o.orderOnlineId")
    List<Ticket> findActiveTicketsInRoomForFutureShowtimes(@Param("roomId") Integer roomId, @Param("now") LocalDateTime now);

    Optional<Ticket> findByQrToken(String qrToken);

    @Query("SELECT t FROM Ticket t " +
           "JOIN t.orderOnline o " +
           "JOIN t.showtime s " +
           "WHERE o.user.userId = :userId AND s.movie.movieId = :movieId " +
           "AND o.status = 1 AND (t.status IS NULL OR t.status = 1) " +
           "ORDER BY o.createdAt DESC, t.ticketId DESC")
    List<Ticket> findPaidTicketsByUserIdAndMovieId(@Param("userId") Integer userId, @Param("movieId") Integer movieId);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Ticket t " +
           "JOIN t.orderOnline o " +
           "JOIN t.showtime s " +
           "WHERE o.user.userId = :userId AND s.movie.movieId = :movieId " +
           "AND o.status = 1 AND (t.status IS NULL OR t.status = 1)")
    boolean existsPaidTicketByUserIdAndMovieId(@Param("userId") Integer userId, @Param("movieId") Integer movieId);

    long countBySeat_SeatId(Integer seatId);

    /** Trùng ghế theo vé (trạng thái vé trên Ticket), không dùng derived name ...AndStatus vì Seat cũng có `status`. */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Ticket t " +
           "WHERE t.showtime.showtimeId = :showtimeId AND t.seat.seatId = :seatId AND t.status = :status")
    boolean existsByShowtime_ShowtimeIdAndSeat_SeatIdAndStatus(
            @Param("showtimeId") Integer showtimeId,
            @Param("seatId") Integer seatId,
            @Param("status") int status);
}
