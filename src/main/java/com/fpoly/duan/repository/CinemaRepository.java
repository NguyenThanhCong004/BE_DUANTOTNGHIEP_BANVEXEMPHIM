package com.fpoly.duan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Cinema;

@Repository
public interface CinemaRepository extends JpaRepository<Cinema, Integer> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByAddressIgnoreCase(String address);
    
    boolean existsByNameIgnoreCaseAndCinemaIdNot(String name, Integer cinemaId);
    boolean existsByAddressIgnoreCaseAndCinemaIdNot(String address, Integer cinemaId);
}

