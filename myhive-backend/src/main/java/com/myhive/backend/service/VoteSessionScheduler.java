package com.myhive.backend.service;

import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityLikeCount;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.repository.VoteSessionResultActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoteSessionScheduler {

    private static final int MINUTES_PER_DAY = 480;

    private final VoteSessionRepository voteSessionRepository;
    private final VoteActivityLikeRepository voteActivityLikeRepository;
    private final VoteSessionResultActivityRepository resultActivityRepository;
    private final ActivityRepository activityRepository;
    private final EmailService emailService;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.site.url:https://trivlu.com}")
    private String siteUrl;

    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void processExpiredSessions() {
        List<VoteSession> expired = voteSessionRepository
                .findByStatusAndExpiresAtBefore(VoteSessionStatus.ACTIVE, LocalDateTime.now(ZoneOffset.UTC));

        for (VoteSession session : expired) {
            try {
                processSession(session);
            } catch (Exception e) {
                log.error("Failed to process vote session {}: {}", session.getId(), e.getMessage(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldSessions() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(7);
        int deleted = voteSessionRepository
                .deleteByStatusAndExpiresAtBefore(VoteSessionStatus.COMPLETED, cutoff);
        log.info("Cleaned up {} completed vote sessions", deleted);
    }

    void processSession(VoteSession session) {
        long tripDays = ChronoUnit.DAYS.between(session.getStartDate(), session.getEndDate()) + 1;
        int budgetMinutes = (int) (tripDays * MINUTES_PER_DAY);

        List<ActivityLikeCount> likedRows = voteActivityLikeRepository
                .findLikedActivitiesWithCounts(session.getId());

        int remaining = budgetMinutes;
        int sortOrder = 0;

        for (ActivityLikeCount row : likedRows) {
            Integer duration = row.getDuration();

            if (duration == null || duration > remaining) {
                continue;
            }

            Optional<Activity> activityOpt = activityRepository.findById(row.getActivityId());
            if (activityOpt.isEmpty()) {
                continue;
            }

            VoteSessionResultActivity result = new VoteSessionResultActivity();
            result.setSession(session);
            result.setActivity(activityOpt.get());
            result.setSortOrder(sortOrder++);
            resultActivityRepository.save(result);
            remaining -= duration;
        }

        session.setStatus(VoteSessionStatus.COMPLETED);
        voteSessionRepository.save(session);
        log.info("Processed vote session {} — {} activities selected", session.getId(), sortOrder);

        if (emailEnabled) {
            List<VoteSessionResultActivity> results = resultActivityRepository
                    .findBySessionIdOrderBySortOrder(session.getId());
            emailService.sendVoteResult(session, results, siteUrl);
        }
    }
}
