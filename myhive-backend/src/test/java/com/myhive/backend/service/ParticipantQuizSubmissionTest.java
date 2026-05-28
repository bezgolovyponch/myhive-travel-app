package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.ParticipantQuizSubmissionRequest;
import com.myhive.backend.dto.PublicQuizDTO;
import com.myhive.backend.dto.QuizResponseDTO;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizAnswerWeight;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionQuizResponse;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import com.myhive.backend.repository.VoteSessionQuizResponseRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class ParticipantQuizSubmissionTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteSessionQuizResponseRepository voteSessionQuizResponseRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;

    @Test
    void getParticipantQuiz_returnsSameShapeAsPublicQuiz() {
        Fixture f = setupActiveSession(true);

        PublicQuizDTO quiz = voteSessionService.getParticipantQuiz(f.session.getShareToken());

        assertThat(quiz.getQuestions()).hasSize(1);
        assertThat(quiz.getQuestions().get(0).getAnswers()).hasSize(1);
    }

    @Test
    void getParticipantQuiz_unknownShareToken_throwsNotFound() {
        assertThatThrownBy(() -> voteSessionService.getParticipantQuiz(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitParticipantQuiz_persistsResponses() {
        Fixture f = setupActiveSession(true);
        UUID expectedVoter = UUID.randomUUID();

        voteSessionService.submitParticipantQuiz(
                f.session.getShareToken(),
                new ParticipantQuizSubmissionRequest(expectedVoter,
                        List.of(new QuizResponseDTO(f.question.getId(), f.answer.getId()))));

        List<VoteSessionQuizResponse> rows =
                voteSessionQuizResponseRepository.findBySessionId(f.session.getId());
        // setup persists 1 organizer response; participant adds a second.
        assertThat(rows).extracting(VoteSessionQuizResponse::getVoterToken)
                .contains(expectedVoter);
    }

    @Test
    void submitParticipantQuiz_secondTime_throwsConflict() {
        Fixture f = setupActiveSession(true);
        UUID voter = UUID.randomUUID();
        QuizResponseDTO good = new QuizResponseDTO(f.question.getId(), f.answer.getId());

        voteSessionService.submitParticipantQuiz(f.session.getShareToken(),
                new ParticipantQuizSubmissionRequest(voter, List.of(good)));

        assertThatThrownBy(() -> voteSessionService.submitParticipantQuiz(
                f.session.getShareToken(),
                new ParticipantQuizSubmissionRequest(voter, List.of(good))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void submitParticipantQuiz_sessionCompleted_throwsConflict() {
        Fixture f = setupActiveSession(true);
        f.session.setStatus(VoteSessionStatus.COMPLETED);
        voteSessionRepository.saveAndFlush(f.session);

        assertThatThrownBy(() -> voteSessionService.submitParticipantQuiz(
                f.session.getShareToken(),
                new ParticipantQuizSubmissionRequest(UUID.randomUUID(),
                        List.of(new QuizResponseDTO(f.question.getId(), f.answer.getId())))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void submitParticipantQuiz_unknownQuestion_throwsBadRequest() {
        Fixture f = setupActiveSession(true);

        assertThatThrownBy(() -> voteSessionService.submitParticipantQuiz(
                f.session.getShareToken(),
                new ParticipantQuizSubmissionRequest(UUID.randomUUID(),
                        List.of(new QuizResponseDTO(UUID.randomUUID(), f.answer.getId())))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void submitParticipantQuiz_noQuiz_emptyResponsesAccepted() {
        Fixture f = setupActiveSession(false);

        voteSessionService.submitParticipantQuiz(f.session.getShareToken(),
                new ParticipantQuizSubmissionRequest(UUID.randomUUID(), List.of()));

        // No new responses persisted (we passed an empty list); no exception thrown.
        assertThat(voteSessionQuizResponseRepository.findBySessionId(f.session.getId())).isEmpty();
    }

    // ---------------- fixture ----------------

    private record Fixture(VoteSession session, QuizQuestion question, QuizAnswer answer) {}

    private Fixture setupActiveSession(boolean withQuiz) {
        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        Category nightlife = new Category();
        nightlife.setName("Nightlife");
        nightlife.setSlug("nightlife");
        nightlife = categoryRepository.save(nightlife);

        Set<Category> destCats = new HashSet<>();
        destCats.add(nightlife);
        destination.setCategories(destCats);
        destinationRepository.saveAndFlush(destination);

        Activity activity = new Activity();
        activity.setDestination(destination);
        activity.setName("Club");
        activity.setPrice(new BigDecimal("100"));
        activity.setCategories(new HashSet<>(List.of(nightlife)));
        activity = activityRepository.saveAndFlush(activity);

        QuizQuestion question = null;
        QuizAnswer answer = null;
        QuizResponseDTO organizerResponse = null;
        if (withQuiz) {
            QuizQuestion q = new QuizQuestion();
            q.setDestination(destination);
            q.setPrompt("Vibe?");
            q.setSortOrder(0);
            QuizAnswer a = new QuizAnswer();
            a.setQuestion(q);
            a.setLabel("Wild");
            a.setSortOrder(0);
            QuizAnswerWeight w = new QuizAnswerWeight();
            w.setAnswer(a);
            w.setCategory(nightlife);
            w.setWeight(2);
            a.getWeights().add(w);
            q.getAnswers().add(a);
            QuizQuestion savedQ = quizQuestionRepository.saveAndFlush(q);
            question = savedQ;
            answer = savedQ.getAnswers().get(savedQ.getAnswers().size() - 1);
            organizerResponse = new QuizResponseDTO(question.getId(), answer.getId());
        }

        VoteSessionCreateRequest req = new VoteSessionCreateRequest();
        req.setDestinationId(destination.getId());
        req.setInitiatorEmail("organizer+" + UUID.randomUUID() + "@example.com");
        req.setNumberOfTravelers(2);
        req.setStartDate(LocalDate.of(2026, 8, 1));
        req.setEndDate(LocalDate.of(2026, 8, 10));
        req.setBudget(new BigDecimal("3000"));
        req.setVoterToken(UUID.randomUUID());
        req.setQuizResponses(organizerResponse == null ? List.of() : List.of(organizerResponse));
        req.setActivityIds(List.of(activity.getId()));

        VoteSessionResponse response = voteSessionService.createSession(req);
        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        return new Fixture(session, question, answer);
    }
}
