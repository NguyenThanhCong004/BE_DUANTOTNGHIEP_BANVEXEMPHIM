package com.fpoly.duan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.RevokedToken;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Integer> {
    boolean existsByToken(String token);
}
