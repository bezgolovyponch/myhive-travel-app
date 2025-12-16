package com.myhive.backend.repository;

import com.myhive.backend.entity.Destination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DestinationRepository extends JpaRepository<Destination, UUID> {
}
