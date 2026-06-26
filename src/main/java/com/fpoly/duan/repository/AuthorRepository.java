package com.fpoly.duan.repository;

import com.fpoly.duan.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AuthorRepository extends JpaRepository<Author, Integer> {
    Optional<Author> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}
