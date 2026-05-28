package com.myhive.backend.repository;

import java.util.UUID;

public interface ActivityVoteCount {

    UUID getActivityId();

    long getLikeCount();

    long getSkipCount();
}
