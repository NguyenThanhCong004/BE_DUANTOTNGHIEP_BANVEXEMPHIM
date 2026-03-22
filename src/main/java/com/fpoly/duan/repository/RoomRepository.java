package com.fpoly.duan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Room;

@Repository
public interface RoomRepository extends JpaRepository<Room, Integer> {
    List<Room> findByCinema_CinemaId(Integer cinemaId);
}

