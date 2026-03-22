package com.fpoly.duan.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.SeatType;

@Repository
public interface SeatTypeRepository extends JpaRepository<SeatType, Integer> {
    Optional<SeatType> findByName(String name);
}

