package com.fpoly.duan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.CategoryProduct;

@Repository
public interface CategoryProductRepository extends JpaRepository<CategoryProduct, Integer> {
}
