package com.myhive.backend.repository;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteActivityLike;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.model.VoteSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteActivityLikeRepositoryTest {

    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteActivityLikeRepository voteActivityLikeRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void findVoteCountsBySessionId_returnsPerActivityLikeAndSkipCounts() {
        Destination destination = saveDestination();
        Activity a1 = saveActivity(destination, "A1");
        Activity a2 = saveActivity(destination, "A2");
        VoteSession session = saveActiveSession(destination);

        // a1: 3 likes, 1 skip; a2: 0 likes, 2 skips
        saveLike(session, a1, true);
        saveLike(session, a1, true);
        saveLike(session, a1, true);
        saveLike(session, a1, false);
        saveLike(session, a2, false);
        saveLike(session, a2, false);

        List<ActivityVoteCount> counts = voteActivityLikeRepository.findVoteCountsBySessionId(session.getId());
        Map<UUID, ActivityVoteCount> byActivity = counts.stream()
                .collect(Collectors.toMap(ActivityVoteCount::getActivityId, c -> c));

        assertThat(byActivity).hasSize(2);
        assertThat(byActivity.get(a1.getId()).getLikeCount()).isEqualTo(3);
        assertThat(byActivity.get(a1.getId()).getSkipCount()).isEqualTo(1);
        assertThat(byActivity.get(a2.getId()).getLikeCount()).isEqualTo(0);
        assertThat(byActivity.get(a2.getId()).getSkipCount()).isEqualTo(2);
    }

    private Destination saveDestination() {
        Destination d = new Destination();
        d.setName("Prague");
        return destinationRepository.save(d);
    }

    private Activity saveActivity(Destination destination, String name) {
        Activity a = new Activity();
        a.setDestination(destination);
        a.setName(name);
        a.setPrice(new BigDecimal("100"));
        return activityRepository.saveAndFlush(a);
    }

    private VoteSession saveActiveSession(Destination destination) {
        VoteSession s = new VoteSession();
        s.setShareToken(UUID.randomUUID());
        s.setManagerToken(UUID.randomUUID());
        s.setDestination(destination);
        s.setInitiatorEmail("o@example.com");
        s.setNumberOfTravelers(2);
        s.setStartDate(LocalDate.of(2026, 8, 1));
        s.setEndDate(LocalDate.of(2026, 8, 10));
        s.setStatus(VoteSessionStatus.ACTIVE);
        s.setExpiresAt(LocalDateTime.of(2026, 8, 10, 23, 59));
        return voteSessionRepository.save(s);
    }

    private void saveLike(VoteSession session, Activity activity, boolean liked) {
        VoteActivityLike like = new VoteActivityLike();
        like.setSession(session);
        like.setVoterToken(UUID.randomUUID());
        like.setActivity(activity);
        like.setLiked(liked);
        voteActivityLikeRepository.save(like);
    }
}
