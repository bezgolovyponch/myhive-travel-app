package com.myhive.backend.repository;

import com.myhive.backend.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, UUID> {
    List<Activity> findByDestinationId(UUID destinationId);
    List<Activity> findByCategory(String category);
    List<Activity> findByDestinationIdAndCategory(UUID destinationId, String category);
}
