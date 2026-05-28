package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.QuizResponseDTO;
import com.myhive.backend.dto.VoteActivityResponse;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizAnswerWeight;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.entity.VoteSessionQuizResponse;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import com.myhive.backend.repository.VoteSessionActivityRepository;
import com.myhive.backend.repository.VoteSessionQuizResponseRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteSessionCreateSessionTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteSessionActivityRepository voteSessionActivityRepository;
    @Autowired private VoteSessionQuizResponseRepository voteSessionQuizResponseRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;

    @Test
    void createSession_persistsCuratedListWithNameAndPriceSnapshots() {
        String expectedName = "Tank Driving";
        BigDecimal expectedPrice = new BigDecimal("150.00");

        Destination destination = newDestination("Prague");
        Category nightlife = newCategory("Nightlife", "nightlife");
        attachCategory(destination, nightlife);
        Activity activity = newActivity(destination, expectedName, expectedPrice, Set.of(nightlife));

        VoteSessionCreateRequest request = baseRequest(destination.getId());
        request.setActivityIds(List.of(activity.getId()));

        VoteSessionResponse response = voteSessionService.createSession(request);

        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        List<VoteSessionActivity> curated = voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId());

        assertThat(curated).hasSize(1);
        assertThat(curated.get(0).getActivityName()).isEqualTo(expectedName);
        assertThat(curated.get(0).getPrice()).isEqualByComparingTo(expectedPrice);
        assertThat(session.getStatus()).isEqualTo(VoteSessionStatus.ACTIVE);
    }

    @Test
    void createSession_snapshotPriceFrozenAgainstLaterRePrice() {
        Destination destination = newDestination("Prague");
        Category nightlife = newCategory("Nightlife", "nightlife");
        attachCategory(destination, nightlife);
        Activity activity = newActivity(destination, "Tank Driving", new BigDecimal("150.00"), Set.of(nightlife));

        VoteSessionCreateRequest request = baseRequest(destination.getId());
        request.setActivityIds(List.of(activity.getId()));

        VoteSessionResponse response = voteSessionService.createSession(request);

        activity.setPrice(new BigDecimal("999.00"));
        activityRepository.saveAndFlush(activity);

        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        List<VoteSessionActivity> curated = voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId());

        assertThat(curated.get(0).getPrice()).isEqualByComparingTo("150.00");
    }

    @Test
    void createSession_persistsOrganizerQuizResponses() {
        Destination destination = newDestination("Prague");
        Category nightlife = newCategory("Nightlife", "nightlife");
        attachCategory(destination, nightlife);
        Activity activity = newActivity(destination, "Tank", new BigDecimal("100"), Set.of(nightlife));

        QuizQuestion question = newQuestion(destination, "Vibe?");
        QuizAnswer answer = newAnswer(question, "Wild", nightlife, 2);

        VoteSessionCreateRequest request = baseRequest(destination.getId());
        request.setActivityIds(List.of(activity.getId()));
        request.setQuizResponses(List.of(new QuizResponseDTO(question.getId(), answer.getId())));

        VoteSessionResponse response = voteSessionService.createSession(request);

        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        List<VoteSessionQuizResponse> rows = voteSessionQuizResponseRepository.findBySessionId(session.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getVoterToken()).isEqualTo(request.getVoterToken());
        assertThat(rows.get(0).getAnswer().getId()).isEqualTo(answer.getId());
    }

    @Test
    void createSession_rejectsActivityFromOtherDestination_400() {
        Destination prague = newDestination("Prague");
        Destination berlin = newDestination("Berlin");
        Category nightlife = newCategory("Nightlife", "nightlife");
        attachCategory(prague, nightlife);
        attachCategory(berlin, nightlife);
        Activity inBerlin = newActivity(berlin, "Berlin Club", new BigDecimal("80"), Set.of(nightlife));

        VoteSessionCreateRequest request = baseRequest(prague.getId());
        request.setActivityIds(List.of(inBerlin.getId()));

        assertThatThrownBy(() -> voteSessionService.createSession(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(inBerlin.getId().toString());
    }

    @Test
    void createSession_rejectsActivityNotInOrganizerCategories_400() {
        Destination destination = newDestination("Prague");
        Category nightlife = newCategory("Nightlife", "nightlife");
        Category chillout = newCategory("Chillout", "chillout");
        attachCategory(destination, nightlife, chillout);
        Activity inChillout = newActivity(destination, "Spa", new BigDecimal("60"), Set.of(chillout));

        QuizQuestion question = newQuestion(destination, "Vibe?");
        QuizAnswer answer = newAnswer(question, "Wild", nightlife, 2);

        VoteSessionCreateRequest request = baseRequest(destination.getId());
        request.setActivityIds(List.of(inChillout.getId()));
        request.setQuizResponses(List.of(new QuizResponseDTO(question.getId(), answer.getId())));

        assertThatThrownBy(() -> voteSessionService.createSession(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createSession_rejectsCrossDestinationQuestion_400() {
        Destination prague = newDestination("Prague");
        Destination berlin = newDestination("Berlin");
        Category nightlife = newCategory("Nightlife", "nightlife");
        attachCategory(prague, nightlife);
        Activity activity = newActivity(prague, "Club", new BigDecimal("100"), Set.of(nightlife));

        QuizQuestion berlinQuestion = newQuestion(berlin, "Vibe?");
        QuizAnswer berlinAnswer = newAnswer(berlinQuestion, "Wild", nightlife, 2);

        VoteSessionCreateRequest request = baseRequest(prague.getId());
        request.setActivityIds(List.of(activity.getId()));
        request.setQuizResponses(List.of(new QuizResponseDTO(berlinQuestion.getId(), berlinAnswer.getId())));

        assertThatThrownBy(() -> voteSessionService.createSession(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createSession_ignoresLikedCategoryIds() {
        Destination destination = newDestination("Prague");
        Category nightlife = newCategory("Nightlife", "nightlife");
        attachCategory(destination, nightlife);
        Activity activity = newActivity(destination, "Club", new BigDecimal("100"), Set.of(nightlife));

        VoteSessionCreateRequest request = baseRequest(destination.getId());
        request.setActivityIds(List.of(activity.getId()));
        request.setLikedCategoryIds(List.of(UUID.randomUUID()));

        VoteSessionResponse response = voteSessionService.createSession(request);
        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        assertThat(session.getLikedCategories()).isEmpty();
    }

    @Test
    void getActivities_newSession_returnsCuratedListInSortOrder() {
        Destination destination = newDestination("Prague");
        Category nightlife = newCategory("Nightlife", "nightlife");
        attachCategory(destination, nightlife);
        Activity a1 = newActivity(destination, "First", new BigDecimal("100"), Set.of(nightlife));
        Activity a2 = newActivity(destination, "Second", new BigDecimal("200"), Set.of(nightlife));

        VoteSessionCreateRequest request = baseRequest(destination.getId());
        // intentionally put a2 before a1 to verify the order is preserved
        request.setActivityIds(List.of(a2.getId(), a1.getId()));

        VoteSessionResponse response = voteSessionService.createSession(request);

        List<VoteActivityResponse> activities = voteSessionService.getActivities(response.getShareToken());

        assertThat(activities).extracting(VoteActivityResponse::getId)
                .containsExactly(a2.getId(), a1.getId());
    }

    private VoteSessionCreateRequest baseRequest(UUID destinationId) {
        VoteSessionCreateRequest req = new VoteSessionCreateRequest();
        req.setDestinationId(destinationId);
        req.setInitiatorEmail("test+" + UUID.randomUUID() + "@example.com");
        req.setNumberOfTravelers(2);
        req.setStartDate(LocalDate.of(2026, 8, 1));
        req.setEndDate(LocalDate.of(2026, 8, 10));
        req.setBudget(new BigDecimal("3000.00"));
        req.setVoterToken(UUID.randomUUID());
        req.setQuizResponses(List.of());
        return req;
    }

    private Destination newDestination(String name) {
        Destination d = new Destination();
        d.setName(name);
        return destinationRepository.save(d);
    }

    private Category newCategory(String name, String slug) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        return categoryRepository.save(c);
    }

    private void attachCategory(Destination destination, Category... categories) {
        Set<Category> set = new HashSet<>(destination.getCategories());
        for (Category c : categories) {
            set.add(c);
        }
        destination.setCategories(set);
        destinationRepository.saveAndFlush(destination);
    }

    private Activity newActivity(Destination destination, String name, BigDecimal price, Set<Category> categories) {
        Activity a = new Activity();
        a.setDestination(destination);
        a.setName(name);
        a.setPrice(price);
        a.setCategories(new HashSet<>(categories));
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
