package com.fpoly.duan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.duan.entity.CinemaProduct;

public interface CinemaProductRepository extends JpaRepository<CinemaProduct, Integer> {

    @EntityGraph(attributePaths = { "cinema", "product", "product.category" })
    List<CinemaProduct> findByCinema_CinemaId(Integer cinemaId);

    @EntityGraph(attributePaths = { "cinema", "product", "product.category" })
    Optional<CinemaProduct> findByCinema_CinemaIdAndProduct_ProductId(Integer cinemaId, Integer productId);

    boolean existsByProduct_ProductId(Integer productId);
}
