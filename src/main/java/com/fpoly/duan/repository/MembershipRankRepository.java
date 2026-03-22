package com.fpoly.duan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.MembershipRank;

@Repository
public interface MembershipRankRepository extends JpaRepository<MembershipRank, Integer> {
}
