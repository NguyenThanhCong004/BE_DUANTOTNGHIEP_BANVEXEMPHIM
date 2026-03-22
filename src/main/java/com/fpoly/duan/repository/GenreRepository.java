package com.fpoly.duan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Genre;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Integer> {
}
