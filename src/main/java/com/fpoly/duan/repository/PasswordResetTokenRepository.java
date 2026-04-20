package com.fpoly.duan.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.PasswordResetToken;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Integer> {

    Optional<PasswordResetToken> findByToken(String token);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PasswordResetToken p WHERE p.user.userId = :userId AND p.usedAt IS NULL")
    void deleteUnusedByUserId(@Param("userId") Integer userId);
}
