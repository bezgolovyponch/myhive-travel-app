package com.myhive.backend.repository;

import com.myhive.backend.entity.TripLead;
import com.myhive.backend.model.TripLeadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripLeadRepository extends JpaRepository<TripLead, UUID> {

    Optional<TripLead> findFirstByEmailAndStatus(String email, TripLeadStatus status);

    List<TripLead> findAllByEmailAndStatus(String email, TripLeadStatus status);

    Optional<TripLead> findByRestoreToken(UUID restoreToken);

    Optional<TripLead> findByUnsubscribeToken(UUID unsubscribeToken);

    List<TripLead> findByStatus(TripLeadStatus status);

    @Modifying
    @Transactional
    @Query("DELETE FROM TripLead l WHERE l.updatedAt < :cutoff")
    int deleteByUpdatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
