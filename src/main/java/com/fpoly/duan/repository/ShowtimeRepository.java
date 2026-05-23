package com.fpoly.duan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Showtime;

import jakarta.persistence.LockModeType;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, Integer> {
    @Override
    @EntityGraph(attributePaths = { "movie", "room", "room.cinema" })
    List<Showtime> findAll();

    @Override
    @EntityGraph(attributePaths = { "movie", "room", "room.cinema" })
    Optional<Showtime> findById(Integer id);

    @EntityGraph(attributePaths = { "movie", "room", "room.cinema" })
    List<Showtime> findByRoom_Cinema_CinemaId(Integer cinemaId);

    @EntityGraph(attributePaths = { "movie", "room", "room.cinema" })
    List<Showtime> findByRoom_RoomId(Integer roomId);

    @EntityGraph(attributePaths = { "movie", "room", "room.cinema" })
    List<Showtime> findByMovie_MovieId(Integer movieId);

    @EntityGraph(attributePaths = { "movie", "room", "room.cinema" })
    List<Showtime> findByMovie_MovieIdAndRoom_Cinema_CinemaId(Integer movieId, Integer cinemaId);

    boolean existsByRoom_Cinema_CinemaIdAndStartTimeGreaterThanEqual(Integer cinemaId, java.time.LocalDateTime startTime);

    @Query("SELECT s.movie.movieId, COUNT(s) FROM Showtime s GROUP BY s.movie.movieId")
    List<Object[]> countShowtimesGroupedByMovieId();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Showtime s WHERE s.showtimeId = :id")
    Optional<Showtime> findByIdForUpdate(@Param("id") Integer id);
}

