package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteSessionCartCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.model.VoteMode;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteSessionActivityRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteSessionCartCreateTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteSessionActivityRepository voteSessionActivityRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void createCartSession_persistsCartSessionWithBallotInCartOrder() {
        String expectedFirstName = "Bar Crawl";
        String expectedSecondName = "Karting";
        BigDecimal expectedFirstPrice = new BigDecimal("45.00");

        Destination prague = newDestination("Prague");
        Activity barCrawl = newActivity(prague, expectedFirstName, expectedFirstPrice);
        Activity karting = newActivity(prague, expectedSecondName, new BigDecimal("60.00"));

        VoteSessionCartCreateRequest request =
                cartRequest(prague.getId(), List.of(barCrawl.getId(), karting.getId()));

        VoteSessionResponse response = voteSessionService.createCartSession(request);

        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        List<VoteSessionActivity> ballot =
                voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId());

        assertThat(session.getVoteMode()).isEqualTo(VoteMode.CART);
        assertThat(session.getStatus()).isEqualTo(VoteSessionStatus.ACTIVE);
        assertThat(session.getBudget()).isNull();
        assertThat(response.getManagerToken()).isNotNull();
        assertThat(response.getVoteMode()).isEqualTo("CART");
        assertThat(ballot).hasSize(2);
        assertThat(ballot.get(0).getActivityName()).isEqualTo(expectedFirstName);
        assertThat(ballot.get(0).getPrice()).isEqualByComparingTo(expectedFirstPrice);
        assertThat(ballot.get(1).getActivityName()).isEqualTo(expectedSecondName);
    }

    @Test
    void createCartSession_allowsActivityWithoutCategories() {
        // Unlike the quiz flow there is no quiz-category eligibility check.
        Destination prague = newDestination("Prague");
        Activity uncategorised = newActivity(prague, "Mystery Tour", new BigDecimal("30.00"));

        VoteSessionResponse response = voteSessionService.createCartSession(
                cartRequest(prague.getId(), List.of(uncategorised.getId())));

        assertThat(response.getShareToken()).isNotNull();
    }

    @Test
    void createCartSession_dedupesRepeatedActivityIds() {
        Destination prague = newDestination("Prague");
        Activity barCrawl = newActivity(prague, "Bar Crawl", new BigDecimal("45.00"));

        VoteSessionResponse response = voteSessionService.createCartSession(
                cartRequest(prague.getId(), List.of(barCrawl.getId(), barCrawl.getId())));

        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        assertThat(voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId())).hasSize(1);
    }

    @Test
    void createCartSession_rejectsActivityFromOtherDestination_400() {
        Destination prague = newDestination("Prague");
        Destination berlin = newDestination("Berlin");
        Activity berlinActivity = newActivity(berlin, "Techno Tour", new BigDecimal("50.00"));

        VoteSessionCartCreateRequest request =
                cartRequest(prague.getId(), List.of(berlinActivity.getId()));

        assertThatThrownBy(() -> voteSessionService.createCartSession(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not belong to destination");
    }

    @Test
    void createCartSession_rejectsUnknownActivity_400() {
        Destination prague = newDestination("Prague");
        UUID unknownId = UUID.randomUUID();

        VoteSessionCartCreateRequest request = cartRequest(prague.getId(), List.of(unknownId));

        assertThatThrownBy(() -> voteSessionService.createCartSession(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void createCartSession_rejectsEndDateBeforeStartDate_400() {
        Destination prague = newDestination("Prague");
        Activity barCrawl = newActivity(prague, "Bar Crawl", new BigDecimal("45.00"));

        VoteSessionCartCreateRequest request = cartRequest(prague.getId(), List.of(barCrawl.getId()));
        request.setStartDate(LocalDate.of(2026, 8, 10));
        request.setEndDate(LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> voteSessionService.createCartSession(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("endDate must be on or after startDate");
    }

    private VoteSessionCartCreateRequest cartRequest(UUID destinationId, List<UUID> activityIds) {
        VoteSessionCartCreateRequest request = new VoteSessionCartCreateRequest();
        request.setDestinationId(destinationId);
        request.setInitiatorEmail("initiator+" + UUID.randomUUID() + "@example.com");
        request.setNumberOfTravelers(4);
        request.setStartDate(LocalDate.of(2026, 8, 1));
        request.setEndDate(LocalDate.of(2026, 8, 3));
        request.setActivityIds(activityIds);
        return request;
    }

    private Destination newDestination(String name) {
        Destination destination = new Destination();
        destination.setName(name);
        return destinationRepository.save(destination);
    }

    private Activity newActivity(Destination destination, String name, BigDecimal price) {
        Activity activity = new Activity();
        activity.setDestination(destination);
        activity.setName(name);
        activity.setPrice(price);
        activity.setCategories(new HashSet<>());
        return activityRepository.saveAndFlush(activity);
    }
}
