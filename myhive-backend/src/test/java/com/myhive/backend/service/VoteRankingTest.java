package com.myhive.backend.service;

import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.repository.ActivityVoteCount;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class VoteRankingTest {

    private static VoteSessionActivity row(UUID activityId, int sortOrder) {
        Activity activity = new Activity();
        activity.setId(activityId);
        VoteSessionActivity row = new VoteSessionActivity();
        row.setActivity(activity);
        row.setSortOrder(sortOrder);
        return row;
    }

    private static ActivityVoteCount count(UUID activityId, long likes) {
        return new ActivityVoteCount() {
            @Override
            public UUID getActivityId() {
                return activityId;
            }

            @Override
            public long getLikeCount() {
                return likes;
            }

            @Override
            public long getSkipCount() {
                return 0;
            }
        };
    }

    @Test
    void byLikes_ordersByLikesDescendingThenBallotOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        Map<UUID, ActivityVoteCount> counts = Map.of(first, count(first, 1), third, count(third, 3));

        List<VoteSessionActivity> sorted = Stream.of(row(first, 0), row(second, 1), row(third, 2))
                .sorted(VoteRanking.byLikes(counts))
                .toList();

        assertThat(sorted).extracting(r -> r.getActivity().getId()).containsExactly(third, first, second);
    }

    @Test
    void likeCountOf_isZeroForAnActivityNobodyVotedOn() {
        assertThat(VoteRanking.likeCountOf(Map.of(), row(UUID.randomUUID(), 0))).isZero();
    }
}
