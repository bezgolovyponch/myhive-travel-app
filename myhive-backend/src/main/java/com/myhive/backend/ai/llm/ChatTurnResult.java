package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.edit.EditRequest;
import com.myhive.backend.ai.model.Brief;

import java.util.List;

/**
 * Result of one chat turn. There is no "action" field from the model: Java decides when to
 * generate a plan once the merged brief is ready (see Task 10 {@code ChatTurnNode}).
 * {@code suggestedReplies} are the tap-to-send answers the UI offers under the reply, written as the organizer.
 * {@code recommendations} are catalog names the model suggests for the draft, best match first, when the
 * organizer asked for a kind of activity rather than a concrete edit; the UI offers them as one tap to add.
 * {@code showPackage} is the trim the organizer asked to look at ("show me Premium": a tier name) or
 * {@code ALL} for every trim again ("what were the other options?"); null on every other turn.
 */
public record ChatTurnResult(String reply, Brief briefUpdate, List<String> missingFields, List<EditRequest> edits,
                             LlmUsage usage, List<String> suggestedReplies, List<String> recommendations,
                             String showPackage) {

    public ChatTurnResult {
        edits = edits == null ? List.of() : List.copyOf(edits);
        suggestedReplies = suggestedReplies == null ? List.of() : List.copyOf(suggestedReplies);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }

    /** The shape every caller used before the trim switch existed. */
    public ChatTurnResult(String reply, Brief briefUpdate, List<String> missingFields, List<EditRequest> edits,
                          LlmUsage usage, List<String> suggestedReplies, List<String> recommendations) {
        this(reply, briefUpdate, missingFields, edits, usage, suggestedReplies, recommendations, null);
    }

    /** The shape every caller used before recommendations existed. */
    public ChatTurnResult(String reply, Brief briefUpdate, List<String> missingFields, List<EditRequest> edits,
                          LlmUsage usage, List<String> suggestedReplies) {
        this(reply, briefUpdate, missingFields, edits, usage, suggestedReplies, List.of(), null);
    }

    /** The shape every caller used before suggested replies existed. */
    public ChatTurnResult(String reply, Brief briefUpdate, List<String> missingFields, List<EditRequest> edits,
                          LlmUsage usage) {
        this(reply, briefUpdate, missingFields, edits, usage, List.of());
    }

    /** The shape every caller used before edits existed; a turn without edits. */
    public ChatTurnResult(String reply, Brief briefUpdate, List<String> missingFields, LlmUsage usage) {
        this(reply, briefUpdate, missingFields, List.of(), usage);
    }

    public ChatTurnResult withUsage(LlmUsage newUsage) {
        return new ChatTurnResult(reply, briefUpdate, missingFields, edits, newUsage, suggestedReplies,
                recommendations, showPackage);
    }
}
