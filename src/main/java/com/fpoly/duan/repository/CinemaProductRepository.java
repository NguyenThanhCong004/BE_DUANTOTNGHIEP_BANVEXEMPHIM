package com.fpoly.duan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.duan.entity.CinemaProduct;

public interface CinemaProductRepository extends JpaRepository<CinemaProduct, Integer> {

    List<CinemaProduct> findByCinema_CinemaId(Integer cinemaId);

    Optional<CinemaProduct> findByCinema_CinemaIdAndProduct_ProductId(Integer cinemaId, Integer productId);
}
