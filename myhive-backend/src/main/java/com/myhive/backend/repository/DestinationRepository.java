package com.myhive.backend.repository;

import com.myhive.backend.entity.Destination;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DestinationRepository extends JpaRepository<Destination, UUID> {
}
