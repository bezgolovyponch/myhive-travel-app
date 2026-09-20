package com.myhive.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /ai/sessions}. {@code turnstileToken} is only consulted while
 * {@code app.ai.turnstile-required} is on; {@code initialMessage} lets the entry point open the chat
 * with what the group already typed, which is answered in the same response.
 */
public record CreateSessionRequest(@NotBlank String destinationSlug, String locale, String turnstileToken,
                                   @Size(max = CreateSessionRequest.MESSAGE_MAX) String initialMessage) {

    public static final int MESSAGE_MAX = 1000;
}
