package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteSessionVoteModeTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private DestinationRepository destinationRepository;

    @Test
    void getSession_exposesQuizVoteModeByDefault() {
        String expectedVoteMode = "QUIZ";

        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail("organiser@example.com");
        session.setNumberOfTravelers(2);
        session.setStartDate(LocalDate.of(2026, 8, 1));
        session.setEndDate(LocalDate.of(2026, 8, 3));
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setExpiresAt(LocalDateTime.now().plusHours(24));
        voteSessionRepository.save(session);

        VoteSessionResponse response = voteSessionService.getSession(session.getShareToken());

        assertThat(response.getVoteMode()).isEqualTo(expectedVoteMode);
    }
}
