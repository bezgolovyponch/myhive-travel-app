package com.myhive.backend.entity;

import java.util.UUID;

/** Entities with a unique URL slug; enables the shared slug assignment in {@code SlugAssigner}. */
public interface Slugged {

    UUID getId();

    String getSlug();

    void setSlug(String slug);
}
