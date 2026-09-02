package com.fpoly.duan.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Movie;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Integer> {
    List<Movie> findByGenres_GenreId(Integer genreId);

    // status 2 = Sắp chiếu → 1 = Đang chiếu khi đến ngày khởi chiếu
    @Modifying
    @Query("UPDATE Movie m SET m.status = 1 WHERE m.status = 2 AND m.releaseDate <= :today")
    int activateReleasedMovies(@Param("today") LocalDate today);
}

