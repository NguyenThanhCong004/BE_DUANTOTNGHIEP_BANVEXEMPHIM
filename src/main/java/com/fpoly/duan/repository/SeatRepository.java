package com.fpoly.duan.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Seat;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Integer> {
    List<Seat> findByRoom_RoomId(Integer roomId);

    void deleteByRoom_RoomId(Integer roomId);

    long countBySeatType_SeatTypeId(Integer seatTypeId);

    @Query("SELECT s FROM Seat s JOIN FETCH s.seatType WHERE s.seatId IN :ids")
    List<Seat> findAllByIdWithType(@Param("ids") Collection<Integer> ids);
}

