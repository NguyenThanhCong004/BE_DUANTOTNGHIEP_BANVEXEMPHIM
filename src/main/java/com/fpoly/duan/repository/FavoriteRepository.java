package com.fpoly.duan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Favorite;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Integer> {

    List<Favorite> findByUser_UserIdOrderByFavoriteIdDesc(Integer userId);

    Page<Favorite> findByUser_UserIdOrderByFavoriteIdDesc(Integer userId, Pageable pageable);

    Optional<Favorite> findByUser_UserIdAndMovie_MovieId(Integer userId, Integer movieId);

    boolean existsByUser_UserIdAndMovie_MovieId(Integer userId, Integer movieId);

    void deleteByUser_UserIdAndMovie_MovieId(Integer userId, Integer movieId);
}
