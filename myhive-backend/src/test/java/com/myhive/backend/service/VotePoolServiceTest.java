package com.myhive.backend.service;

import com.myhive.backend.dto.QuizResponseDTO;
import com.myhive.backend.dto.VotePoolRequest;
import com.myhive.backend.dto.VotePoolResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizAnswerWeight;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.QuizAnswerWeightRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import com.myhive.backend.repository.VoteSessionQuizResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class VotePoolServiceTest {

    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private QuizAnswerWeightRepository quizAnswerWeightRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private VoteSessionQuizResponseRepository voteSessionQuizResponseRepository;

    private QuizService quizService;
    private VotePoolService votePoolService;

    @BeforeEach
    void setUp() {
        quizService = new QuizService(quizQuestionRepository, destinationRepository,
                categoryRepository, quizAnswerWeightRepository, voteSessionQuizResponseRepository);
        votePoolService = new VotePoolService(quizService, destinationRepository, activityRepository);
    }

    @Test
    void buildPool_unknownDestination_throwsNotFound() {
        VotePoolRequest request = new VotePoolRequest(UUID.randomUUID(), List.of());
        assertThatThrownBy(() -> votePoolService.buildPool(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void buildPool_withQuizResponses_filtersByTopKCategoriesAndOrdersByFeaturedWeight() {
        Destination destination = saveDestination("Prague");
        Category nightlife = saveCategory("Nightlife", "nightlife", true);
        Category chillout = saveCategory("Chillout", "chillout", true);
        Category transfer = saveCategory("Transfer", "transfer", false);   // non-votable

        attachCategoryToDestination(destination, nightlife, chillout, transfer);

        Activity a1 = saveActivity(destination, "Club Crawl", new BigDecimal("100"), 5, Set.of(nightlife));
        Activity a2 = saveActivity(destination, "Spa Day", new BigDecimal("80"), 3, Set.of(chillout));
        Activity a3 = saveActivity(destination, "Bus Tour", new BigDecimal("40"), 10, Set.of(transfer));
        Activity a4 = saveActivity(destination, "Pub Trail", new BigDecimal("60"), 4, Set.of(nightlife));

        QuizQuestion question = saveQuestion(destination, "Vibe?");
        QuizAnswer answer = saveAnswer(question, "Wild", nightlife, 2);

        VotePoolRequest request = new VotePoolRequest(destination.getId(),
                List.of(new QuizResponseDTO(question.getId(), answer.getId())));

        VotePoolResponse response = votePoolService.buildPool(request);

        // Only Nightlife survives top-K (one positive cat). featured_weight DESC.
        assertThat(response.getPool()).extracting("activityId")
                .containsExactly(a1.getId(), a4.getId());
        assertThat(response.getPool()).extracting("activityId")
                .doesNotContain(a3.getId());   // Transfer excluded
    }

    @Test
    void buildPool_emptyResponses_fallsBackToAllVotableDestinationCategories() {
        Destination destination = saveDestination("Prague");
        Category nightlife = saveCategory("Nightlife", "nightlife", true);
        Category transfer = saveCategory("Transfer", "transfer", false);
        attachCategoryToDestination(destination, nightlife, transfer);

        Activity a1 = saveActivity(destination, "Club", new BigDecimal("100"), 5, Set.of(nightlife));
        Activity a2 = saveActivity(destination, "Bus", new BigDecimal("40"), 10, Set.of(transfer));

        VotePoolResponse response = votePoolService.buildPool(
                new VotePoolRequest(destination.getId(), List.of()));

        assertThat(response.getPool()).extracting("activityId")
                .containsExactly(a1.getId());
    }

    @Test
    void buildPool_cappedAtTwenty() {
        Destination destination = saveDestination("Prague");
        Category nightlife = saveCategory("Nightlife", "nightlife", true);
        attachCategoryToDestination(destination, nightlife);

        for (int i = 0; i < 25; i++) {
            saveActivity(destination, "A" + i, new BigDecimal("10"), 100 - i, Set.of(nightlife));
        }

        VotePoolResponse response = votePoolService.buildPool(
                new VotePoolRequest(destination.getId(), List.of()));

        assertThat(response.getPool()).hasSize(20);
    }

    @Test
    void buildPool_mapsDescriptionAndDuration() {
        Destination destination = saveDestination("Prague");
        Category nightlife = saveCategory("Nightlife", "nightlife", true);
        attachCategoryToDestination(destination, nightlife);

        String expectedDescription = "All-night guided club crawl.";
        Integer expectedDuration = 240;
        Activity activity = saveActivity(destination, "Club Crawl", new BigDecimal("100"), 5, Set.of(nightlife));
        activity.setDescription(expectedDescription);
        activity.setDuration(expectedDuration);
        activityRepository.saveAndFlush(activity);

        VotePoolResponse response = votePoolService.buildPool(
                new VotePoolRequest(destination.getId(), List.of()));

        assertThat(response.getPool()).singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getDescription()).isEqualTo(expectedDescription);
                    assertThat(dto.getDuration()).isEqualTo(expectedDuration);
                });
    }

    // ---- helpers ----
    private Destination saveDestination(String name) {
        Destination d = new Destination();
        d.setName(name);
        return destinationRepository.save(d);
    }

    private Category saveCategory(String name, String slug, boolean votable) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setVotable(votable);
        return categoryRepository.save(c);
    }

    private void attachCategoryToDestination(Destination destination, Category... categories) {
        Set<Category> set = new HashSet<>(destination.getCategories());
        for (Category c : categories) {
            set.add(c);
        }
        destination.setCategories(set);
        destinationRepository.saveAndFlush(destination);
    }

    private Activity saveActivity(Destination destination, String name, BigDecimal price,
                                  int featuredWeight, Set<Category> categories) {
        Activity a = new Activity();
        a.setDestination(destination);
        a.setName(name);
        a.setPrice(price);
        a.setFeaturedWeight(featuredWeight);
        a.setCategories(new HashSet<>(categories));
        return activityRepository.saveAndFlush(a);
    }

    private QuizQuestion saveQuestion(Destination destination, String prompt) {
        QuizQuestion q = new QuizQuestion();
        q.setDestination(destination);
        q.setPrompt(prompt);
        q.setSortOrder(0);
        return quizQuestionRepository.saveAndFlush(q);
    }

    private QuizAnswer saveAnswer(QuizQuestion question, String label, Category category, int weight) {
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
        // Return the just-persisted child (last in the list).
        return saved.getAnswers().get(saved.getAnswers().size() - 1);
    }
}
