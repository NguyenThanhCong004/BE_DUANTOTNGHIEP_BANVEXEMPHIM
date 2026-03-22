package com.fpoly.duan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Showtime;

import jakarta.persistence.LockModeType;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, Integer> {
    List<Showtime> findByRoom_Cinema_CinemaId(Integer cinemaId);

    List<Showtime> findByRoom_RoomId(Integer roomId);

    List<Showtime> findByMovie_MovieId(Integer movieId);

    List<Showtime> findByMovie_MovieIdAndRoom_Cinema_CinemaId(Integer movieId, Integer cinemaId);

    @Query("SELECT s.movie.movieId, COUNT(s) FROM Showtime s GROUP BY s.movie.movieId")
    List<Object[]> countShowtimesGroupedByMovieId();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Showtime s WHERE s.showtimeId = :id")
    Optional<Showtime> findByIdForUpdate(@Param("id") Integer id);
}

