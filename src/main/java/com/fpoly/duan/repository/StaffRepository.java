package com.fpoly.duan.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Staff;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Integer> {
    Optional<Staff> findByEmail(String email);
    Optional<Staff> findByUsername(String username);
    Boolean existsByEmail(String email);
    Boolean existsByUsername(String username);
    Boolean existsByPhone(String phone);

    boolean existsByEmailAndStaffIdNot(String email, Integer staffId);

    boolean existsByUsernameAndStaffIdNot(String username, Integer staffId);

    boolean existsByPhoneAndStaffIdNot(String phone, Integer staffId);
}
