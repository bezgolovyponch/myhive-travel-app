package com.myhive.backend.repository;

import java.util.UUID;

public interface ActivityLikeCount {
    UUID getActivityId();
    Integer getDuration();
    Long getLikeCount();
}
