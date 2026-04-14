package com.myhive.backend.repository;

import com.myhive.backend.entity.Destination;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DestinationRepository extends JpaRepository<Destination, UUID> {

    Optional<Destination> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
