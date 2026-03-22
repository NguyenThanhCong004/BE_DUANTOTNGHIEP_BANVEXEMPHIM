package com.fpoly.duan.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.StaffShift;

@Repository
public interface StaffShiftRepository extends JpaRepository<StaffShift, Integer> {
    List<StaffShift> findByDate(LocalDate date);

    List<StaffShift> findByDateAndStartTimeAndEndTime(LocalDate date, LocalDateTime startTime, LocalDateTime endTime);

    /** Ca làm của một nhân viên — dùng cho GET /shifts/me */
    List<StaffShift> findByStaffStaffIdOrderByDateDescStartTimeAsc(Integer staffId);
}

