package com.fpoly.duan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.Voucher;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Integer> {
}
