package com.myhive.backend.repository;

import com.myhive.backend.entity.Destination;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DestinationRepository extends SluggedRepository<Destination> {

    /** Bulk-removes join rows; the join table has no entity, so a native query is required. */
    @Modifying
    @Query(value = "DELETE FROM destination_categories WHERE category_id = :categoryId", nativeQuery = true)
    void deleteCategoryLinks(@Param("categoryId") UUID categoryId);
}
