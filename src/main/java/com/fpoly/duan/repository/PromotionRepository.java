package com.fpoly.duan.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Promotion;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Integer> {
    List<Promotion> findByCinema_CinemaId(Integer cinemaId);

    /**
     * Tìm promotion đang active cho movie và cinema trong ngày hiện tại
     * Ưu tiên: (movie + cinema) > (movie only) > (cinema only) > general
     */
    @Query("SELECT p FROM Promotion p WHERE " +
           "(p.startDate IS NULL OR p.startDate <= :today) AND " +
           "(p.endDate IS NULL OR p.endDate >= :today) AND " +
           "(p.movie.movieId = :movieId OR p.movie IS NULL) AND " +
           "(p.cinema.cinemaId = :cinemaId OR p.cinema IS NULL) " +
           "ORDER BY p.discountPercent DESC, " +
           "CASE WHEN p.movie IS NOT NULL THEN 1 ELSE 0 END DESC, " +
           "CASE WHEN p.cinema IS NOT NULL THEN 1 ELSE 0 END DESC")
    List<Promotion> findActivePromotions(@Param("movieId") Integer movieId,
                                         @Param("cinemaId") Integer cinemaId,
                                         @Param("today") LocalDate today);
}

