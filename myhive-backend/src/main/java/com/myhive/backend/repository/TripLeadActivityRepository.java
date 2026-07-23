package com.myhive.backend.repository;

import com.myhive.backend.entity.TripLeadActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TripLeadActivityRepository extends JpaRepository<TripLeadActivity, UUID> {

    List<TripLeadActivity> findByLeadIdOrderBySortOrder(UUID leadId);

    @Modifying
    @Query("DELETE FROM TripLeadActivity a WHERE a.lead.id = :leadId")
    void deleteByLeadId(@Param("leadId") UUID leadId);
}
