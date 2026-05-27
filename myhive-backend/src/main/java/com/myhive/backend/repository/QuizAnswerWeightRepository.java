package com.myhive.backend.repository;

import com.myhive.backend.entity.QuizAnswerWeight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface QuizAnswerWeightRepository extends JpaRepository<QuizAnswerWeight, UUID> {

    void deleteAllByCategoryId(UUID categoryId);

    List<QuizAnswerWeight> findAllByAnswerIdIn(Collection<UUID> answerIds);
}
