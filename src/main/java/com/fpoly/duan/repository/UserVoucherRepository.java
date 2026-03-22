package com.fpoly.duan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.UserVoucher;

@Repository
public interface UserVoucherRepository extends JpaRepository<UserVoucher, Integer> {

    List<UserVoucher> findByUser_UserIdOrderByUserVoucherIdDesc(Integer userId);

    boolean existsByUser_UserIdAndVoucher_VouchersIdAndStatus(Integer userId, Integer vouchersId, Integer status);
}
