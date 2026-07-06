package com.myhive.backend.repository;

import com.myhive.backend.entity.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ActivityRepository extends SluggedRepository<Activity> {

    List<Activity> findByDestinationId(UUID destinationId);

    List<Activity> findByCategoriesSlug(String categorySlug);

    List<Activity> findByCategoriesId(UUID categoryId);

    List<Activity> findByDestinationIdAndCategoriesSlug(UUID destinationId, String categorySlug);

    List<Activity> findByFeaturedTrueOrderByNameAsc();

    List<Activity> findByFeaturedTrueAndCategoriesSlugOrderByNameAsc(String categorySlug);

    Page<Activity> findByDestinationId(UUID destinationId, Pageable pageable);

    Page<Activity> findByDestinationIdAndCategoriesSlug(UUID destinationId, String categorySlug, Pageable pageable);

    @Query("SELECT DISTINCT a FROM Activity a JOIN a.categories c WHERE a.destination.id = :destinationId AND c.id IN :categoryIds")
    List<Activity> findByDestinationIdAndCategoriesIdIn(
            @Param("destinationId") UUID destinationId,
            @Param("categoryIds") Set<UUID> categoryIds);

    @Query("""
            SELECT DISTINCT a FROM Activity a
            JOIN a.categories c
            WHERE a.destination.id = :destinationId
              AND c.id IN :categoryIds
            ORDER BY a.featuredWeight DESC, a.id ASC
            """)
    List<Activity> findPoolCandidates(@Param("destinationId") UUID destinationId,
                                      @Param("categoryIds") Collection<UUID> categoryIds,
                                      Pageable pageable);

    @Query("""
            SELECT DISTINCT a FROM Activity a
            JOIN a.categories c
            WHERE a.destination.id = :destinationId
              AND c.votable = true
              AND (:categoryIds IS NULL OR c.id IN :categoryIds)
              AND a.id NOT IN :excludedActivityIds
            ORDER BY a.featuredWeight DESC, a.id ASC
            """)
    List<Activity> findSuggestionCandidates(@Param("destinationId") UUID destinationId,
                                            @Param("categoryIds") Collection<UUID> categoryIds,
                                            @Param("excludedActivityIds") Collection<UUID> excludedActivityIds,
                                            Pageable pageable);
}
