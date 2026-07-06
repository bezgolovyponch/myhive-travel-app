package com.myhive.backend.repository;

import com.myhive.backend.entity.Package;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PackageRepository extends SluggedRepository<Package> {

    /** Bulk-removes join rows; the join table has no entity, so a native query is required. */
    @Modifying
    @Query(value = "DELETE FROM package_categories WHERE category_id = :categoryId", nativeQuery = true)
    void deleteCategoryLinks(@Param("categoryId") UUID categoryId);

    List<Package> findByDestinationId(UUID destinationId);

    List<Package> findByCategoriesSlug(String categorySlug);

    List<Package> findByCategoriesId(UUID categoryId);

    List<Package> findByDestinationIdAndCategoriesSlug(UUID destinationId, String categorySlug);

    @Query("SELECT p.name FROM Package p JOIN p.packageActivities pa WHERE pa.activity.id = :activityId")
    List<String> findPackageNamesByActivityId(@Param("activityId") UUID activityId);
}
