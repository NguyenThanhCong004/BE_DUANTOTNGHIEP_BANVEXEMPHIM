package com.fpoly.duan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Cinema;

@Repository
public interface CinemaRepository extends JpaRepository<Cinema, Integer> {
}

