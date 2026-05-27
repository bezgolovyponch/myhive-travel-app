package com.myhive.backend.repository;

import com.myhive.backend.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    List<QuizQuestion> findByDestinationIdOrderBySortOrder(UUID destinationId);
}
