package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.model.VoteMode;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteSessionActivityRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

// Not @Transactional: processSession runs REQUIRES_NEW.
@SpringBootTest
@Import({TestSecurityConfig.class, VoteSessionLeadCaptureFailureTest.MockConfig.class})
class VoteSessionLeadCaptureFailureTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        public TripLeadService tripLeadService() {
            TripLeadService broken = mock(TripLeadService.class);
            doThrow(new RuntimeException("lead capture down")).when(broken).createFromVoteSession(any(), any());
            return broken;
        }
    }

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteSessionActivityRepository voteSessionActivityRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void processSession_completesEvenWhenLeadCaptureThrows() {
        Destination destination = destinationRepository.saveAndFlush(TestDataFactory.destination("Prague"));
        Activity activity = activityRepository.saveAndFlush(
                TestDataFactory.activity(destination, "Karting", new BigDecimal("50.00")));

        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail("organizer@example.com");
        session.setNumberOfTravelers(4);
        session.setStartDate(LocalDate.now().plusDays(10));
        session.setEndDate(LocalDate.now().plusDays(12));
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setVoteMode(VoteMode.CART);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC));
        session = voteSessionRepository.saveAndFlush(session);

        VoteSessionActivity ballotRow = new VoteSessionActivity();
        ballotRow.setSession(session);
        ballotRow.setActivity(activity);
        ballotRow.setActivityName("Karting");
        ballotRow.setPrice(new BigDecimal("50.00"));
        ballotRow.setSortOrder(0);
        voteSessionActivityRepository.saveAndFlush(ballotRow);

        voteSessionService.processSession(session);

        assertThat(voteSessionRepository.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(VoteSessionStatus.COMPLETED);
    }
}
