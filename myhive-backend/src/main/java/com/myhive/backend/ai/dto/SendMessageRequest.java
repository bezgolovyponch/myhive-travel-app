package com.myhive.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /ai/sessions/&#123;token&#125;/messages}: one user turn, 1-1000 characters. */
public record SendMessageRequest(@NotBlank @Size(max = CreateSessionRequest.MESSAGE_MAX) String content) {
}
