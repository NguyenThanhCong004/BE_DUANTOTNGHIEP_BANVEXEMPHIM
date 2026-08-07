package com.fpoly.duan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Integer> {

    @Query("SELECT r FROM Review r " +
            "JOIN r.ticket t " +
            "JOIN t.orderOnline o " +
            "JOIN t.showtime s " +
            "WHERE o.user.userId = :userId AND s.movie.movieId = :movieId " +
            "ORDER BY r.reviewId DESC")
    List<Review> findByUserIdAndMovieId(@Param("userId") Integer userId, @Param("movieId") Integer movieId);

    @Query("SELECT r FROM Review r " +
            "JOIN r.ticket t " +
            "JOIN t.orderOnline o " +
            "JOIN t.showtime s " +
            "WHERE s.movie.movieId = :movieId AND o.status = 1 AND (t.status IS NULL OR t.status = 1) " +
            "ORDER BY r.reviewId DESC")
    List<Review> findPublicByMovieId(@Param("movieId") Integer movieId);

    @Query("SELECT AVG(r.rating) FROM Review r " +
            "JOIN r.ticket t " +
            "JOIN t.orderOnline o " +
            "JOIN t.showtime s " +
            "WHERE s.movie.movieId = :movieId AND o.status = 1 AND (t.status IS NULL OR t.status = 1)")
    Double findAverageRatingByMovieId(@Param("movieId") Integer movieId);
}
