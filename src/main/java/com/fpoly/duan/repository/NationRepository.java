package com.fpoly.duan.repository;

import com.fpoly.duan.entity.Nation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NationRepository extends JpaRepository<Nation, Integer> {
    Optional<Nation> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}
