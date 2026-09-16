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
                              GenerationDTO latestGeneration, LimitsDTO limits) {

    /** What is left of this chat's allowances, so the UI can offer "start a new chat" before a 429. */
    public record LimitsDTO(int messagesLeft, int generationsLeft) {
    }
}
