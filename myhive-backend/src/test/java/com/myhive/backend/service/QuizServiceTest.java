package com.myhive.backend.service;

import com.myhive.backend.dto.PublicQuizDTO;
import com.myhive.backend.dto.QuizAnswerDTO;
import com.myhive.backend.dto.QuizAnswerWeightDTO;
import com.myhive.backend.dto.QuizDTO;
import com.myhive.backend.dto.QuizQuestionDTO;
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
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.QuizAnswerWeightRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import com.myhive.backend.repository.VoteSessionQuizResponseRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
// Run against the project's configured H2 datasource (PostgreSQL compat mode) rather than @DataJpaTest's
// default plain-H2 swap — the same prod-mirroring datasource VoteSessionTablesTest uses. Under the plain-H2
// swap, persisting a VoteSession spuriously trips its status enum CHECK constraint.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class QuizServiceTest {

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;
    @Autowired
    private DestinationRepository destinationRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private QuizAnswerWeightRepository quizAnswerWeightRepository;
    @Autowired
    private VoteSessionRepository voteSessionRepository;
    @Autowired
    private VoteSessionQuizResponseRepository voteSessionQuizResponseRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private QuizService quizService;

    @BeforeEach
    void setUp() {
        quizService = new QuizService(quizQuestionRepository, destinationRepository,
                categoryRepository, quizAnswerWeightRepository, voteSessionQuizResponseRepository);
    }

    @Test
    void getQuiz_unknownDestination_throwsNotFound() {
        assertThatThrownBy(() -> quizService.getQuiz(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getQuiz_returnsQuestionsOrderedWithAnswersAndWeights() {
        String expectedPrompt = "Daytime hero or 4am legend?";
        int expectedWeight = 2;

        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        Category category = new Category();
        category.setName("Nightlife");
        category.setSlug("nightlife");
        category = categoryRepository.save(category);

        QuizQuestion question = new QuizQuestion();
        question.setDestination(destination);
        question.setPrompt(expectedPrompt);
        question.setSortOrder(0);
        QuizAnswer answer = new QuizAnswer();
        answer.setQuestion(question);
        answer.setLabel("4am legend");
        answer.setSortOrder(0);
        QuizAnswerWeight weight = new QuizAnswerWeight();
        weight.setAnswer(answer);
        weight.setCategory(category);
        weight.setWeight(expectedWeight);
        answer.getWeights().add(weight);
        question.getAnswers().add(answer);
        quizQuestionRepository.saveAndFlush(question);

        QuizDTO quiz = quizService.getQuiz(destination.getId());

        assertThat(quiz.getQuestions()).hasSize(1);
        assertThat(quiz.getQuestions().get(0).getPrompt()).isEqualTo(expectedPrompt);
        assertThat(quiz.getQuestions().get(0).getAnswers()).hasSize(1);
        assertThat(quiz.getQuestions().get(0).getAnswers().get(0).getWeights()).hasSize(1);
        assertThat(quiz.getQuestions().get(0).getAnswers().get(0).getWeights().get(0).getCategoryId())
                .isEqualTo(category.getId());
        assertThat(quiz.getQuestions().get(0).getAnswers().get(0).getWeights().get(0).getWeight())
                .isEqualTo(expectedWeight);
    }

    @Test
    void getQuiz_noQuiz_returnsEmptyQuestions() {
        Destination destination = new Destination();
        destination.setName("Berlin");
        destination = destinationRepository.save(destination);

        QuizDTO quiz = quizService.getQuiz(destination.getId());

        assertThat(quiz.getQuestions()).isEmpty();
    }

    @Test
    void replaceQuiz_unknownCategoryWeight_throwsBadRequest() {
        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        QuizQuestion old = new QuizQuestion();
        old.setDestination(destination);
        old.setPrompt("OLD QUESTION");
        old.setSortOrder(0);
        QuizAnswer oldAnswer = new QuizAnswer();
        oldAnswer.setQuestion(old);
        oldAnswer.setLabel("old");
        oldAnswer.setSortOrder(0);
        old.getAnswers().add(oldAnswer);
        quizQuestionRepository.saveAndFlush(old);

        QuizAnswerWeightDTO weight = new QuizAnswerWeightDTO(UUID.randomUUID(), 2);
        QuizAnswerDTO answer = new QuizAnswerDTO(null, "4am legend", 0, List.of(weight));
        QuizQuestionDTO question = new QuizQuestionDTO(null, "Daytime or 4am?", 0, List.of(answer));
        QuizDTO dto = new QuizDTO(List.of(question));

        UUID destinationId = destination.getId();
        assertThatThrownBy(() -> quizService.replaceQuiz(destinationId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Category not found");

        assertThat(quizQuestionRepository.findByDestinationIdOrderBySortOrder(destinationId))
                .extracting(QuizQuestion::getPrompt)
                .containsExactly("OLD QUESTION");
    }

    @Test
    void replaceQuiz_replacesExistingQuiz() {
        String expectedPrompt = "NEW QUESTION";

        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        Category category = new Category();
        category.setName("Nightlife");
        category.setSlug("nightlife");
        category = categoryRepository.save(category);

        QuizQuestion old = new QuizQuestion();
        old.setDestination(destination);
        old.setPrompt("OLD QUESTION");
        old.setSortOrder(0);
        QuizAnswer oldAnswer = new QuizAnswer();
        oldAnswer.setQuestion(old);
        oldAnswer.setLabel("old");
        oldAnswer.setSortOrder(0);
        old.getAnswers().add(oldAnswer);
        quizQuestionRepository.saveAndFlush(old);

        QuizAnswerWeightDTO weight = new QuizAnswerWeightDTO(category.getId(), 2);
        QuizAnswerDTO answer = new QuizAnswerDTO(null, "4am legend", 0, List.of(weight));
        QuizQuestionDTO question = new QuizQuestionDTO(null, expectedPrompt, 0, List.of(answer));
        QuizDTO dto = new QuizDTO(List.of(question));

        QuizDTO result = quizService.replaceQuiz(destination.getId(), dto);

        assertThat(result.getQuestions()).hasSize(1);
        assertThat(result.getQuestions().get(0).getPrompt()).isEqualTo(expectedPrompt);
        assertThat(quizQuestionRepository.findByDestinationIdOrderBySortOrder(destination.getId()))
                .extracting(QuizQuestion::getPrompt)
                .containsExactly(expectedPrompt);
    }

    @Test
    void replaceQuiz_destinationHasQuizResponses_replacesAndWipesStaleResponses() {
        String expectedPrompt = "NEW QUESTION";

        Destination destination = destinationRepository.save(destination("Prague"));
        Category category = categoryRepository.save(category("Nightlife", "nightlife"));

        QuizQuestion old = saveQuestion(destination, "OLD QUESTION", 0);
        QuizAnswer oldAnswer = saveAnswer(old, "old", 0);

        // A participant already answered this quiz — vote_session_quiz_responses references the old answer.
        VoteSession session = saveActiveSession(destination);
        VoteSessionQuizResponse response = new VoteSessionQuizResponse();
        response.setSession(session);
        response.setVoterToken(UUID.randomUUID());
        response.setQuestion(old);
        response.setAnswer(oldAnswer);
        voteSessionQuizResponseRepository.saveAndFlush(response);

        // The real PUT-quiz request runs in a fresh persistence context with no responses loaded.
        // Detach everything so replaceQuiz sees only DB state, as it does in production.
        entityManager.clear();

        QuizAnswerWeightDTO weight = new QuizAnswerWeightDTO(category.getId(), 2);
        QuizAnswerDTO answer = new QuizAnswerDTO(null, "4am legend", 0, List.of(weight));
        QuizQuestionDTO question = new QuizQuestionDTO(null, expectedPrompt, 0, List.of(answer));
        QuizDTO dto = new QuizDTO(List.of(question));

        QuizDTO result = quizService.replaceQuiz(destination.getId(), dto);

        assertThat(result.getQuestions())
                .extracting(QuizQuestionDTO::getPrompt)
                .containsExactly(expectedPrompt);
        // Test-mode decision: editing the quiz discards existing participants' responses.
        assertThat(voteSessionQuizResponseRepository.findBySessionId(session.getId())).isEmpty();
    }

    @Test
    void replaceQuiz_emptyQuestions_clearsQuiz() {
        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        QuizQuestion old = new QuizQuestion();
        old.setDestination(destination);
        old.setPrompt("OLD");
        old.setSortOrder(0);
        QuizAnswer oldAnswer = new QuizAnswer();
        oldAnswer.setQuestion(old);
        oldAnswer.setLabel("old");
        oldAnswer.setSortOrder(0);
        old.getAnswers().add(oldAnswer);
        quizQuestionRepository.saveAndFlush(old);

        QuizDTO result = quizService.replaceQuiz(destination.getId(), new QuizDTO(List.of()));

        assertThat(result.getQuestions()).isEmpty();
    }

    @Test
    void snapshot_emptyResponses_returnsEmpty() {
        assertThat(quizService.snapshot(List.of())).isEmpty();
    }

    @Test
    void snapshot_sumsSignedWeightsAndDropsNonPositive() {
        Category nightlife = categoryRepository.save(category("Nightlife", "nightlife"));
        Category chillout = categoryRepository.save(category("Chillout", "chillout"));
        Destination destination = destinationRepository.save(destination("Prague"));

        QuizQuestion question = saveQuestion(destination, "Q1", 0);
        QuizAnswer answer = saveAnswer(question, "A", 0);
        saveWeight(answer, nightlife, 2);
        saveWeight(answer, chillout, -1);

        List<UUID> result = quizService.snapshot(List.of(answer.getId()));

        assertThat(result).containsExactly(nightlife.getId());
    }

    @Test
    void snapshot_topThreeOrderedByScoreThenId() {
        Destination destination = destinationRepository.save(destination("Prague"));
        Category cat1 = categoryRepository.save(category("Cat1", "cat1"));
        Category cat2 = categoryRepository.save(category("Cat2", "cat2"));
        Category cat3 = categoryRepository.save(category("Cat3", "cat3"));
        Category cat4 = categoryRepository.save(category("Cat4", "cat4"));

        QuizQuestion question = saveQuestion(destination, "Q1", 0);
        QuizAnswer answer = saveAnswer(question, "A", 0);
        saveWeight(answer, cat1, 1);
        saveWeight(answer, cat2, 3);
        saveWeight(answer, cat3, 2);
        saveWeight(answer, cat4, 2);

        List<UUID> result = quizService.snapshot(List.of(answer.getId()));

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualTo(cat2.getId());
        UUID winnerOfTie = cat3.getId().compareTo(cat4.getId()) < 0 ? cat3.getId() : cat4.getId();
        UUID loserOfTie = cat3.getId().compareTo(cat4.getId()) < 0 ? cat4.getId() : cat3.getId();
        assertThat(result.get(1)).isEqualTo(winnerOfTie);
        assertThat(result.get(2)).isEqualTo(loserOfTie);
    }

    @Test
    void snapshot_excludesNonVotableCategories() {
        Destination destination = destinationRepository.save(destination("Prague"));
        Category votable = categoryRepository.save(category("Nightlife", "nightlife"));
        Category nonVotable = category("Transfer", "transfer");
        nonVotable.setVotable(false);
        nonVotable = categoryRepository.save(nonVotable);

        QuizQuestion question = saveQuestion(destination, "Q1", 0);
        QuizAnswer answer = saveAnswer(question, "A", 0);
        saveWeight(answer, votable, 2);
        saveWeight(answer, nonVotable, 5);

        List<UUID> result = quizService.snapshot(List.of(answer.getId()));

        assertThat(result).containsExactly(votable.getId());
    }

    @Test
    void getPublicQuiz_returnsQuestionsWithoutWeights() {
        String expectedPrompt = "Prompt?";
        String expectedLabel = "Label";

        Destination destination = destinationRepository.save(destination("Prague"));
        Category category = categoryRepository.save(category("Nightlife", "nightlife"));
        QuizQuestion question = saveQuestion(destination, expectedPrompt, 0);
        QuizAnswer answer = saveAnswer(question, expectedLabel, 0);
        saveWeight(answer, category, 2);

        PublicQuizDTO publicQuiz = quizService.getPublicQuiz(destination.getId());

        assertThat(publicQuiz.getQuestions()).hasSize(1);
        assertThat(publicQuiz.getQuestions().get(0).getPrompt()).isEqualTo(expectedPrompt);
        assertThat(publicQuiz.getQuestions().get(0).getAnswers()).hasSize(1);
        assertThat(publicQuiz.getQuestions().get(0).getAnswers().get(0).getLabel()).isEqualTo(expectedLabel);
        // Type system guarantees no `weights` field on PublicQuizAnswerDTO — no runtime check needed.
    }

    @Test
    void getPublicQuiz_unknownDestination_throwsNotFound() {
        assertThatThrownBy(() -> quizService.getPublicQuiz(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Destination destination(String name) {
        Destination d = new Destination();
        d.setName(name);
        return d;
    }

    private VoteSession saveActiveSession(Destination destination) {
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
        return voteSessionRepository.saveAndFlush(session);
    }

    private Category category(String name, String slug) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        return c;
    }

    private QuizQuestion saveQuestion(Destination destination, String prompt, int sortOrder) {
        QuizQuestion q = new QuizQuestion();
        q.setDestination(destination);
        q.setPrompt(prompt);
        q.setSortOrder(sortOrder);
        return quizQuestionRepository.saveAndFlush(q);
    }

    private QuizAnswer saveAnswer(QuizQuestion question, String label, int sortOrder) {
        QuizAnswer a = new QuizAnswer();
        a.setQuestion(question);
        a.setLabel(label);
        a.setSortOrder(sortOrder);
        question.getAnswers().add(a);
        QuizQuestion saved = quizQuestionRepository.saveAndFlush(question);
        return saved.getAnswers().get(saved.getAnswers().size() - 1);
    }

    private void saveWeight(QuizAnswer answer, Category category, int weight) {
        QuizAnswerWeight w = new QuizAnswerWeight();
        w.setAnswer(answer);
        w.setCategory(category);
        w.setWeight(weight);
        answer.getWeights().add(w);
        quizQuestionRepository.saveAndFlush(answer.getQuestion());
    }
}
