package com.myhive.backend.service;

import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.repository.ActivityVoteCount;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/**
 * Ranking shared by the frozen CART result, the live tally and the organizer's halfway email:
 * like count descending, ties broken by the organizer's original ballot order.
 */
final class VoteRanking {

    private VoteRanking() {
    }

    static Comparator<VoteSessionActivity> byLikes(Map<UUID, ActivityVoteCount> counts) {
        return Comparator
                .comparingLong((VoteSessionActivity row) -> likeCountOf(counts, row)).reversed()
                .thenComparingInt(VoteSessionActivity::getSortOrder);
    }

    static long likeCountOf(Map<UUID, ActivityVoteCount> counts, VoteSessionActivity row) {
        ActivityVoteCount count = counts.get(row.getActivity().getId());
        return count == null ? 0 : count.getLikeCount();
    }
}
