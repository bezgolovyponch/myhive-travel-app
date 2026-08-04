package com.myhive.backend.repository;

import com.myhive.backend.entity.EmailSuppression;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmailSuppressionRepository extends JpaRepository<EmailSuppression, UUID> {

    boolean existsByEmail(String email);
}
