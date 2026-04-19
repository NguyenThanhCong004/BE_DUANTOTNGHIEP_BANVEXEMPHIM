package com.fpoly.duan.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.StaffShift;

@Repository
public interface StaffShiftRepository extends JpaRepository<StaffShift, Integer> {
    List<StaffShift> findByDate(LocalDate date);

    List<StaffShift> findByDateAndStartTimeAndEndTime(LocalDate date, LocalDateTime startTime, LocalDateTime endTime);

    /** Ca làm của một nhân viên — dùng cho GET /shifts/me */
    List<StaffShift> findByStaffStaffIdOrderByDateDescStartTimeAsc(Integer staffId);

    @Query("SELECT s FROM StaffShift s WHERE s.staff.staffId = :staffId AND :now BETWEEN s.startTime AND s.endTime")
    java.util.Optional<StaffShift> findActiveShift(@Param("staffId") Integer staffId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM StaffShift s WHERE s.staff.staffId = :staffId AND s.date >= :date")
    void deleteByStaffIdAndDateAfterOrEqual(@Param("staffId") Integer staffId, @Param("date") LocalDate date);
}

