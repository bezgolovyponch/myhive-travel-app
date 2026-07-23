package com.myhive.backend.service;

import com.myhive.backend.dto.QuizResponseDTO;
import com.myhive.backend.dto.VotePoolActivityDTO;
import com.myhive.backend.dto.VotePoolRequest;
import com.myhive.backend.entity.TripLead;
import com.myhive.backend.entity.TripLeadActivity;
import com.myhive.backend.model.TripLeadSource;
import com.myhive.backend.model.TripLeadStatus;
import com.myhive.backend.repository.BookingRepository;
import com.myhive.backend.repository.EmailSuppressionRepository;
import com.myhive.backend.repository.TripLeadActivityRepository;
import com.myhive.backend.repository.TripLeadRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripLeadReminderService {

    static final Duration[] QUIZ_CADENCE = {Duration.ofHours(1), Duration.ofHours(24), Duration.ofHours(72)};
    static final Duration[] VOTE_CADENCE = {Duration.ofHours(24), Duration.ofHours(72)};

    private final TripLeadRepository tripLeadRepository;
    private final TripLeadActivityRepository tripLeadActivityRepository;
    private final EmailSuppressionRepository emailSuppressionRepository;
    private final BookingRepository bookingRepository;
    private final VoteSessionRepository voteSessionRepository;
    private final VotePoolService votePoolService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend.url:https://trivlu.com}")
    private String frontendUrl;

    @Transactional
    public void processReminder(UUID leadId) {
        TripLead lead = tripLeadRepository.findById(leadId).orElse(null);
        if (lead == null || lead.getStatus() != TripLeadStatus.ACTIVE) {
            return;
        }
        Duration[] cadence = cadenceFor(lead.getSource());
        if (lead.getReminderStage() >= cadence.length) {
            // Repurposed/legacy edge — series already exhausted; close it out.
            lead.setStatus(TripLeadStatus.COMPLETED);
            tripLeadRepository.save(lead);
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (now.isBefore(lead.getLastActivityAt().plus(cadence[lead.getReminderStage()]))) {
            return; // not due yet — user activity pushes every remaining stage out
        }
        if (emailSuppressionRepository.existsByEmail(lead.getEmail())) {
            lead.setStatus(TripLeadStatus.UNSUBSCRIBED);
            tripLeadRepository.save(lead);
            return;
        }
        if (hasConverted(lead)) {
            lead.setStatus(TripLeadStatus.CONVERTED);
            tripLeadRepository.save(lead);
            return;
        }
        int stage = lead.getReminderStage() + 1;
        lead.setReminderStage(stage);
        lead.setLastReminderAt(now);
        if (stage >= cadence.length) {
            lead.setStatus(TripLeadStatus.COMPLETED); // final touch — series over
        }
        tripLeadRepository.save(lead);
        sendReminderQuietly(lead, stage);
    }

    static Duration[] cadenceFor(TripLeadSource source) {
        return source == TripLeadSource.VOTE ? VOTE_CADENCE : QUIZ_CADENCE;
    }

    private boolean hasConverted(TripLead lead) {
        if (bookingRepository.existsByUserEmailIgnoreCaseAndCreatedAtGreaterThanEqual(
                lead.getEmail(), lead.getCreatedAt())) {
            return true;
        }
        if (lead.getSource() == TripLeadSource.VOTE && lead.getVoteSessionId() != null
                && bookingRepository.existsByVoteSessionId(lead.getVoteSessionId())) {
            return true;
        }
        return lead.getSource() == TripLeadSource.QUIZ
                && voteSessionRepository.existsByInitiatorEmailIgnoreCaseAndCreatedAtGreaterThanEqual(
                        lead.getEmail(), lead.getCreatedAt());
    }

    private void sendReminderQuietly(TripLead lead, int stage) {
        try {
            List<TripLeadActivity> items =
                    tripLeadActivityRepository.findByLeadIdOrderBySortOrder(lead.getId());
            List<VotePoolActivityDTO> recommendations =
                    items.isEmpty() ? buildRecommendations(lead) : List.of();
            emailService.sendTripReminder(lead, stage, items, recommendations, frontendUrl);
        } catch (Exception e) {
            // The stage advance must commit even if the hand-off fails, or the next tick would
            // resend the same stage forever. Delivery is best-effort, like the vote-result email.
            log.error("Failed to send trip reminder for lead {}: {}", lead.getId(), e.getMessage(), e);
        }
    }

    private List<VotePoolActivityDTO> buildRecommendations(TripLead lead) {
        if (lead.getQuizResponsesJson() == null || lead.getDestination() == null) {
            return List.of();
        }
        try {
            List<QuizResponseDTO> responses = objectMapper.readValue(
                    lead.getQuizResponsesJson(), new TypeReference<List<QuizResponseDTO>>() {});
            VotePoolRequest request = new VotePoolRequest();
            request.setDestinationId(lead.getDestination().getId());
            request.setResponses(responses);
            List<VotePoolActivityDTO> pool = votePoolService.buildPool(request).getPool();
            return pool.size() > 3 ? pool.subList(0, 3) : pool;
        } catch (Exception e) {
            // Malformed stored answers must not block the reminder — send it without recommendations.
            log.warn("Could not build recommendations for lead {}: {}", lead.getId(), e.getMessage());
            return List.of();
        }
    }
}
