package com.fpoly.duan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.News;

@Repository
public interface NewsRepository extends JpaRepository<News, Integer> {
}
