package com.fpoly.duan.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findFirstByEmailIgnoreCaseOrderByUserIdAsc(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);
    Boolean existsByEmail(String email);
    Boolean existsByPhone(String phone);
    Optional<User> findByPhone(String phone);

    boolean existsByEmailAndUserIdNot(String email, Integer userId);

    boolean existsByPhoneAndUserIdNot(String phone, Integer userId);
}
