package com.fpoly.duan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {
    @Override
    @EntityGraph(attributePaths = "category")
    Optional<Product> findById(Integer id);

    @EntityGraph(attributePaths = "category")
    List<Product> findByCategory_CategoryProductId(Integer categoryId);

    @EntityGraph(attributePaths = "category")
    List<Product> findAllByOrderByProductIdDesc();

    @EntityGraph(attributePaths = "category")
    List<Product> findByCategory_CategoryProductIdOrderByProductIdDesc(Integer categoryId);
}
