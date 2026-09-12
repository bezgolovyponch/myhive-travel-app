package com.myhive.backend.repository;

import com.myhive.backend.entity.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    Optional<Contact> findByEmail(String email);

    /** Both parameters receive the same search term; an empty term matches every row via the email clause. */
    Page<Contact> findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(
            String email, String name, Pageable pageable);

    /** Contacts the daily digest has not reported yet, oldest first. */
    List<Contact> findByDigestSentAtIsNullOrderByFirstSeenAtAsc();

    @Modifying
    @Query("update Contact c set c.digestSentAt = :sentAt where c.id in :ids")
    int markDigested(@Param("ids") Collection<UUID> ids, @Param("sentAt") LocalDateTime sentAt);
}
