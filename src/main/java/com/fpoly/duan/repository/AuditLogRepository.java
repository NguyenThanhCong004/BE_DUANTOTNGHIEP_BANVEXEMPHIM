package com.fpoly.duan.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fpoly.duan.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {
    List<AuditLog> findTop20ByOrderByCreatedAtDesc();

    List<AuditLog> findByActorRoleIgnoreCaseOrderByCreatedAtDesc(String actorRole, Pageable pageable);
}
