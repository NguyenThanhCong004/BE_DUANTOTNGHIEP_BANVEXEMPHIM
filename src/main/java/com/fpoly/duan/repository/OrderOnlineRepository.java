package com.fpoly.duan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.OrderOnline;

@Repository
public interface OrderOnlineRepository extends JpaRepository<OrderOnline, Integer> {

    List<OrderOnline> findByUser_UserIdOrderByCreatedAtDesc(Integer userId);

    Optional<OrderOnline> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);
}
