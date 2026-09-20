package com.myhive.backend.ai.dto;

import com.myhive.backend.ai.model.Brief;

import java.util.List;
import java.util.UUID;

/**
 * The whole planner screen in one body: {@code GET /ai/sessions/&#123;token&#125;} rebuilds a chat a
 * group comes back to days later from nothing but the stored token.
 */
public record SessionStateDTO(UUID token, String destinationSlug, String locale, String status, Brief brief,
                              List<String> missingFields, boolean readyToGenerate, List<MessageDTO> messages,
                              GenerationDTO latestGeneration, GenerationDTO latestReadyGeneration,
                              FirstTurnErrorDTO firstTurnError, LimitsDTO limits) {

    /** What is left of this chat's allowances, so the UI can offer "start a new chat" before a 429. */
    public record LimitsDTO(int messagesLeft, int generationsLeft) {
    }

    /**
     * Set only by {@code POST /ai/sessions} when the inline first turn could not reach the model. The
     * session exists and the user message is stored, so the UI shows "try again" and re-sends the same
     * text; {@code code} is {@code LLM_UNAVAILABLE} or {@code LLM_TIMEOUT}.
     */
    public record FirstTurnErrorDTO(String code) {
    }
}
