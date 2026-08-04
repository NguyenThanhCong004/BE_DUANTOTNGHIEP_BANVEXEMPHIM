package com.fpoly.duan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    @Query("SELECT n FROM Notification n " +
            "WHERE n.active = true AND (n.cinemaId IS NULL OR n.cinemaId = :cinemaId) " +
            "ORDER BY n.createdAt DESC")
    List<Notification> findActiveForCinema(@Param("cinemaId") Integer cinemaId);

    List<Notification> findTop10ByActiveTrueOrderByCreatedAtDesc();
}
