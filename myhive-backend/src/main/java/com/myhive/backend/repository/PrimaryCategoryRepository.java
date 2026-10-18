package com.myhive.backend.repository;

import com.myhive.backend.entity.PrimaryCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrimaryCategoryRepository extends JpaRepository<PrimaryCategory, UUID> {

    List<PrimaryCategory> findAllByOrderBySortOrderAsc();

    Optional<PrimaryCategory> findBySlug(String slug);
}
