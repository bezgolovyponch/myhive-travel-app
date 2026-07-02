package com.myhive.backend.repository;

import com.myhive.backend.entity.VoteSessionQuizResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VoteSessionQuizResponseRepository extends JpaRepository<VoteSessionQuizResponse, UUID> {

    List<VoteSessionQuizResponse> findBySessionId(UUID sessionId);

    /**
     * Deletes every quiz response that points at one of a destination's quiz questions, returning the
     * number of rows removed. Called before a quiz is replaced so the old {@code quiz_questions}/
     * {@code quiz_answers} rows can be deleted without tripping the {@code vote_session_quiz_responses}
     * foreign keys ({@code question_id} and {@code answer_id}, neither of which cascades). The subquery
     * keeps the DELETE target join-free, which a bulk JPQL DELETE requires.
     *
     * <p>Keying off {@code question_id} also covers the {@code answer_id} FK because a response's answer
     * always belongs to its question (enforced for every written response by
     * {@code VoteSessionService.validateQuizResponses}).
     *
     * <p>No {@code clearAutomatically} is needed: {@code replaceQuiz} loads no responses into the
     * persistence context before calling this, and an explicit {@code flush()} follows.
     */
    @Modifying
    @Query("""
            DELETE FROM VoteSessionQuizResponse r
            WHERE r.question.id IN (
                SELECT q.id FROM QuizQuestion q WHERE q.destination.id = :destinationId
            )
            """)
    int deleteByDestinationId(@Param("destinationId") UUID destinationId);
}
