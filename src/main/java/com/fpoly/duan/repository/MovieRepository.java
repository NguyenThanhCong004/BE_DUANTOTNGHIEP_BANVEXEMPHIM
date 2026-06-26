package com.fpoly.duan.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Movie;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Integer> {
    List<Movie> findByGenre_GenreId(Integer genreId);
    boolean existsByAuthorIgnoreCase(String author);
    boolean existsByNationIgnoreCase(String nation);

    @Query("select distinct m.author from Movie m where m.author is not null and trim(m.author) <> ''")
    List<String> findDistinctAuthors();

    @Query("select distinct m.nation from Movie m where m.nation is not null and trim(m.nation) <> ''")
    List<String> findDistinctNations();
}

