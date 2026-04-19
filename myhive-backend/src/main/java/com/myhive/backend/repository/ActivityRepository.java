package com.myhive.backend.repository;

import com.myhive.backend.entity.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Optional<Activity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Activity> findByDestinationId(UUID destinationId);

    List<Activity> findByCategoriesSlug(String categorySlug);

    List<Activity> findByDestinationIdAndCategoriesSlug(UUID destinationId, String categorySlug);

    Page<Activity> findByDestinationId(UUID destinationId, Pageable pageable);

    Page<Activity> findByDestinationIdAndCategoriesSlug(UUID destinationId, String categorySlug, Pageable pageable);
}
