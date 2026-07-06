package com.myhive.backend.repository;

import com.myhive.backend.entity.Package;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PackageRepository extends SluggedRepository<Package> {

    List<Package> findByDestinationId(UUID destinationId);

    List<Package> findByCategoriesSlug(String categorySlug);

    List<Package> findByCategoriesId(UUID categoryId);

    List<Package> findByDestinationIdAndCategoriesSlug(UUID destinationId, String categorySlug);

    @Query("SELECT p.name FROM Package p JOIN p.packageActivities pa WHERE pa.activity.id = :activityId")
    List<String> findPackageNamesByActivityId(@Param("activityId") UUID activityId);
}
