package com.myhive.backend.repository;

import com.myhive.backend.entity.Package;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PackageRepository extends JpaRepository<Package, UUID> {

    Optional<Package> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Package> findByDestinationId(UUID destinationId);

    List<Package> findByCategoriesSlug(String categorySlug);

    List<Package> findByDestinationIdAndCategoriesSlug(UUID destinationId, String categorySlug);

    @Query("SELECT p.name FROM Package p JOIN p.packageActivities pa WHERE pa.activity.id = :activityId")
    List<String> findPackageNamesByActivityId(@Param("activityId") UUID activityId);
}
