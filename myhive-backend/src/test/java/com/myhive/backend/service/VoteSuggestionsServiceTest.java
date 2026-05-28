package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.QuizResponseDTO;
import com.myhive.backend.dto.SuggestionDTO;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizAnswerWeight;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteSuggestionsServiceTest {

    @Autowired private VoteSuggestionsService voteSuggestionsService;
    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;

    @Test
    void suggestions_quizDerived_excludesCuratedAndOrdersByFeaturedWeight() {
        Destination destination = newDestination();
        Category nightlife = newCategory("Nightlife", "nightlife", true);
        attachCategory(destination, nightlife);

        Activity curated = newActivity(destination, "curated", 5, Set.of(nightlife));
        Activity hi = newActivity(destination, "hi", 10, Set.of(nightlife));
        Activity lo = newActivity(destination, "lo", 1, Set.of(nightlife));

        QuizQuestion question = newQuestion(destination, "Vibe?");
        QuizAnswer answer = newAnswer(question, "Wild", nightlife, 2);

        VoteSession session = createSession(destination,
                List.of(curated.getId()),
                List.of(new QuizResponseDTO(question.getId(), answer.getId())));

        List<SuggestionDTO> suggestions = voteSuggestionsService.buildSuggestions(session);

        assertThat(suggestions).extracting(SuggestionDTO::getActivityId)
                .containsExactly(hi.getId(), lo.getId());
    }

    @Test
    void suggestions_emptyGroupCats_fallsBackToVotableExcludingCurated() {
        Destination destination = newDestination();
        Category nightlife = newCategory("Nightlife", "nightlife", true);
        Category transfer = newCategory("Transfer", "transfer", false);
        attachCategory(destination, nightlife, transfer);

        Activity curated = newActivity(destination, "curated", 5, Set.of(nightlife));
        Activity nightOther = newActivity(destination, "nightOther", 7, Set.of(nightlife));
        Activity inTransferOnly = newActivity(destination, "transferOnly", 9, Set.of(transfer));

        VoteSession session = createSession(destination, List.of(curated.getId()), List.of());

        List<SuggestionDTO> suggestions = voteSuggestionsService.buildSuggestions(session);

        // No quiz responses → snapshot empty → fallback to all-votable-not-curated.
        // Transfer is non-votable, excluded.
        assertThat(suggestions).extracting(SuggestionDTO::getActivityId).containsExactly(nightOther.getId());
    }

    @Test
    void suggestions_cappedAtTen() {
        Destination destination = newDestination();
        Category nightlife = newCategory("Nightlife", "nightlife", true);
        attachCategory(destination, nightlife);

        Activity curated = newActivity(destination, "curated", 100, Set.of(nightlife));
        for (int i = 0; i < 12; i++) {
            newActivity(destination, "a" + i, 50 - i, Set.of(nightlife));
        }

        VoteSession session = createSession(destination, List.of(curated.getId()), List.of());

        List<SuggestionDTO> suggestions = voteSuggestionsService.buildSuggestions(session);

        assertThat(suggestions).hasSize(10);
    }

    // ---- helpers ----

    private VoteSession createSession(Destination destination, List<UUID> activityIds,
                                      List<QuizResponseDTO> quizResponses) {
        VoteSessionCreateRequest req = new VoteSessionCreateRequest();
        req.setDestinationId(destination.getId());
        req.setInitiatorEmail("o+" + UUID.randomUUID() + "@example.com");
        req.setNumberOfTravelers(2);
        req.setStartDate(LocalDate.of(2026, 8, 1));
        req.setEndDate(LocalDate.of(2026, 8, 10));
        req.setVoterToken(UUID.randomUUID());
        req.setQuizResponses(quizResponses);
        req.setActivityIds(activityIds);
        VoteSessionResponse response = voteSessionService.createSession(req);
        return voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
    }

    private Destination newDestination() {
        Destination d = new Destination();
        d.setName("Prague");
        return destinationRepository.save(d);
    }

    private Category newCategory(String name, String slug, boolean votable) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setVotable(votable);
        return categoryRepository.save(c);
    }

    private void attachCategory(Destination destination, Category... cats) {
        Set<Category> set = new HashSet<>(destination.getCategories());
        for (Category c : cats) {
            set.add(c);
        }
        destination.setCategories(set);
        destinationRepository.saveAndFlush(destination);
    }

    private Activity newActivity(Destination destination, String name, int featuredWeight, Set<Category> cats) {
        Activity a = new Activity();
        a.setDestination(destination);
        a.setName(name);
        a.setPrice(new BigDecimal("100"));
        a.setFeaturedWeight(featuredWeight);
        a.setCategories(new HashSet<>(cats));
        return activityRepository.saveAndFlush(a);
    }

    private QuizQuestion newQuestion(Destination destination, String prompt) {
        QuizQuestion q = new QuizQuestion();
        q.setDestination(destination);
        q.setPrompt(prompt);
        q.setSortOrder(0);
        return quizQuestionRepository.saveAndFlush(q);
    }

    private QuizAnswer newAnswer(QuizQuestion question, String label, Category category, int weight) {
        QuizAnswer a = new QuizAnswer();
        a.setQuestion(question);
        a.setLabel(label);
        a.setSortOrder(0);
        QuizAnswerWeight w = new QuizAnswerWeight();
        w.setAnswer(a);
        w.setCategory(category);
        w.setWeight(weight);
        a.getWeights().add(w);
        question.getAnswers().add(a);
        QuizQuestion saved = quizQuestionRepository.saveAndFlush(question);
        return saved.getAnswers().get(saved.getAnswers().size() - 1);
    }
}
