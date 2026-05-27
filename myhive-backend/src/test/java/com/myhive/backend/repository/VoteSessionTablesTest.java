package com.myhive.backend.repository;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.entity.VoteSessionQuizResponse;
import com.myhive.backend.model.VoteSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteSessionTablesTest {

    @Autowired
    private VoteSessionRepository voteSessionRepository;
    @Autowired
    private VoteSessionActivityRepository voteSessionActivityRepository;
    @Autowired
    private VoteSessionQuizResponseRepository voteSessionQuizResponseRepository;
    @Autowired
    private DestinationRepository destinationRepository;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

    @Test
    void voteSessionActivity_persistsNameAndPriceSnapshots() {
        BigDecimal expectedPrice = new BigDecimal("149.99");
        String expectedName = "Tank Driving (snapshot)";

        VoteSession session = newActiveSession();
        Activity activity = newActivity(session.getDestination(), "Tank Driving", new BigDecimal("150.00"));

        VoteSessionActivity row = new VoteSessionActivity();
        row.setSession(session);
        row.setActivity(activity);
        row.setActivityName(expectedName);
        row.setPrice(expectedPrice);
        row.setSortOrder(0);
        voteSessionActivityRepository.saveAndFlush(row);

        List<VoteSessionActivity> rows = voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getActivityName()).isEqualTo(expectedName);
        assertThat(rows.get(0).getPrice()).isEqualByComparingTo(expectedPrice);
    }

    @Test
    void voteSessionQuizResponse_uniqueOnSessionVoterQuestion() {
        VoteSession session = newActiveSession();
        QuizQuestion question = newQuestion(session.getDestination());
        QuizAnswer answer = question.getAnswers().get(0);
        UUID voterToken = UUID.randomUUID();

        VoteSessionQuizResponse first = new VoteSessionQuizResponse();
        first.setSession(session);
        first.setVoterToken(voterToken);
        first.setQuestion(question);
        first.setAnswer(answer);
        voteSessionQuizResponseRepository.saveAndFlush(first);

        VoteSessionQuizResponse duplicate = new VoteSessionQuizResponse();
        duplicate.setSession(session);
        duplicate.setVoterToken(voterToken);
        duplicate.setQuestion(question);
        duplicate.setAnswer(answer);

        assertThatThrownBy(() -> voteSessionQuizResponseRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void voteSessionQuizResponse_persistsAndReadsBySession() {
        VoteSession session = newActiveSession();
        QuizQuestion question = newQuestion(session.getDestination());
        QuizAnswer answer = question.getAnswers().get(0);
        UUID expectedVoterToken = UUID.randomUUID();

        VoteSessionQuizResponse response = new VoteSessionQuizResponse();
        response.setSession(session);
        response.setVoterToken(expectedVoterToken);
        response.setQuestion(question);
        response.setAnswer(answer);
        voteSessionQuizResponseRepository.saveAndFlush(response);

        List<VoteSessionQuizResponse> reloaded =
                voteSessionQuizResponseRepository.findBySessionId(session.getId());
        assertThat(reloaded).hasSize(1);
        assertThat(reloaded.get(0).getVoterToken()).isEqualTo(expectedVoterToken);
        assertThat(reloaded.get(0).getAnswer().getId()).isEqualTo(answer.getId());
    }

    private VoteSession newActiveSession() {
        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail("test@example.com");
        session.setNumberOfTravelers(2);
        session.setStartDate(LocalDate.of(2026, 8, 1));
        session.setEndDate(LocalDate.of(2026, 8, 10));
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setExpiresAt(LocalDateTime.of(2026, 8, 10, 23, 59));
        return voteSessionRepository.save(session);
    }

    private Activity newActivity(Destination destination, String name, BigDecimal price) {
        Activity activity = new Activity();
        activity.setDestination(destination);
        activity.setName(name);
        activity.setPrice(price);
        return activityRepository.saveAndFlush(activity);
    }

    private QuizQuestion newQuestion(Destination destination) {
        QuizQuestion q = new QuizQuestion();
        q.setDestination(destination);
        q.setPrompt("Q?");
        q.setSortOrder(0);
        QuizAnswer a = new QuizAnswer();
        a.setQuestion(q);
        a.setLabel("A");
        a.setSortOrder(0);
        q.getAnswers().add(a);
        return quizQuestionRepository.saveAndFlush(q);
    }
}
