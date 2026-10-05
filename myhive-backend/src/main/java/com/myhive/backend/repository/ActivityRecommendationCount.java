package com.myhive.backend.repository;

import java.util.UUID;

public interface ActivityRecommendationCount {

    UUID getActivityId();

    long getRecommendationCount();
}
