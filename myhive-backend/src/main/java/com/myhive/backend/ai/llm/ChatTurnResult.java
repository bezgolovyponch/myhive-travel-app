package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.edit.EditRequest;
import com.myhive.backend.ai.model.Brief;

import java.util.List;

/**
 * Result of one chat turn. There is no "action" field from the model: Java decides when to
 * generate a plan once the merged brief is ready (see Task 10 {@code ChatTurnNode}).
 */
public record ChatTurnResult(String reply, Brief briefUpdate, List<String> missingFields, List<EditRequest> edits,
                             LlmUsage usage) {

    public ChatTurnResult {
        edits = edits == null ? List.of() : List.copyOf(edits);
    }

    /** The shape every caller used before edits existed; a turn without edits. */
    public ChatTurnResult(String reply, Brief briefUpdate, List<String> missingFields, LlmUsage usage) {
        this(reply, briefUpdate, missingFields, List.of(), usage);
    }

    public ChatTurnResult withUsage(LlmUsage newUsage) {
        return new ChatTurnResult(reply, briefUpdate, missingFields, edits, newUsage);
    }
}
