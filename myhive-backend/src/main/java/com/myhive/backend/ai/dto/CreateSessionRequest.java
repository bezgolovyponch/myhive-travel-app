package com.myhive.backend.ai.dto;

import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of {@code POST /ai/sessions}. {@code turnstileToken} is only consulted while
 * {@code app.ai.turnstile-required} is on; {@code initialMessage} lets the entry point open the chat
 * with what the group already typed, which is answered in the same response.
 *
 * <p>{@code days}, {@code groupSize}, {@code arrival} and {@code departure} are what the entry screen's
 * pickers already chose; each is optional, and the chat never asks again for one that is set.
 */
public record CreateSessionRequest(@NotBlank String destinationSlug, String locale, String turnstileToken,
                                   @Size(max = CreateSessionRequest.MESSAGE_MAX) String initialMessage,
                                   @Min(Brief.MIN_DAYS) @Max(Brief.MAX_DAYS) Integer days,
                                   @Min(Brief.MIN_GROUP) @Max(Brief.MAX_GROUP) Integer groupSize,
                                   DayEdge arrival, DayEdge departure) {

    public static final int MESSAGE_MAX = 1000;

    /** The shape callers used before the pickers existed: nothing preset. */
    public CreateSessionRequest(String destinationSlug, String locale, String turnstileToken, String initialMessage) {
        this(destinationSlug, locale, turnstileToken, initialMessage, null, null, null, null);
    }

    /** What the pickers set, as a brief with everything else unknown. */
    public Brief preset() {
        return new Brief(days, groupSize, List.of(), null, null, null, arrival, departure, null);
    }
}
