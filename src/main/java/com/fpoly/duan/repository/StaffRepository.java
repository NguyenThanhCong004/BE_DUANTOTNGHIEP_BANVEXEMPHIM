package com.fpoly.duan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Staff;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Integer> {
    Optional<Staff> findByEmail(String email);
    Optional<Staff> findByUsername(String username);

    /** Tránh lỗi NonUniqueResult khi DB trùng email (luôn lấy 1 bản ghi cố định). */
    Optional<Staff> findFirstByEmailOrderByStaffIdAsc(String email);

    Optional<Staff> findFirstByUsernameOrderByStaffIdAsc(String username);
    Boolean existsByEmail(String email);
    Boolean existsByUsername(String username);
    Boolean existsByPhone(String phone);

    boolean existsByEmailAndStaffIdNot(String email, Integer staffId);

    boolean existsByUsernameAndStaffIdNot(String username, Integer staffId);

    boolean existsByPhoneAndStaffIdNot(String phone, Integer staffId);

    @Query(value = "SELECT * FROM staff WHERE UPPER(role) NOT LIKE '%SUPER_ADMIN%' OR role IS NULL", nativeQuery = true)
    List<Staff> findAllExceptSuperAdmin();

    List<Staff> findByCinema_CinemaIdAndRoleAndStatus(Integer cinemaId, String role, Integer status);
}
