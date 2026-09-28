package com.myhive.backend.repository;

import com.myhive.backend.entity.Destination;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DestinationRepository extends SluggedRepository<Destination> {

    /**
     * Same lookup as {@code findBySlug}, with the categories already fetched: the AI planner seeds
     * their slugs into the graph outside any transaction, where a lazy collection would blow up.
     */
    @Query("SELECT d FROM Destination d LEFT JOIN FETCH d.categories WHERE d.slug = :slug")
    Optional<Destination> findBySlugWithCategories(@Param("slug") String slug);

    /** Bulk-removes join rows; the join table has no entity, so a native query is required. */
    @Modifying
    @Query(value = "DELETE FROM destination_categories WHERE category_id = :categoryId", nativeQuery = true)
    void deleteCategoryLinks(@Param("categoryId") UUID categoryId);
}
