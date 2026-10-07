package com.myhive.backend.ai.dto;

import com.myhive.backend.ai.model.Tier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /ai/sessions/&#123;token&#125;/messages}: one user turn, 1-1000 characters.
 * {@code packageKey} is the trim on screen once there are packages - the organizer's trip draft, which is
 * the only package the turn may change; optional for clients that predate it.
 */
public record SendMessageRequest(@NotBlank @Size(max = CreateSessionRequest.MESSAGE_MAX) String content,
                                 Tier packageKey) {

    public SendMessageRequest(String content) {
        this(content, null);
    }
}
