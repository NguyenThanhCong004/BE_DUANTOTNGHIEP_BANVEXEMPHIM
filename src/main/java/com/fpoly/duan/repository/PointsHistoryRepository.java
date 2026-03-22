package com.fpoly.duan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.PointsHistory;

@Repository
public interface PointsHistoryRepository extends JpaRepository<PointsHistory, Integer> {

    List<PointsHistory> findByUser_UserIdOrderByDateDescPointHistoryIdDesc(Integer userId);
}
