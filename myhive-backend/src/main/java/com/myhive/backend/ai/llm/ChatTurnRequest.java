package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.model.Brief;

import java.util.List;

/**
 * One chat turn's input. {@code packagesView} and {@code catalogNames} are what the model needs to
 * propose edits and are filled only once a generation exists; a null view means "no packages yet", and
 * the prompt then carries no edit block at all.
 */
public record ChatTurnRequest(String locale, String destinationName, List<String> categorySlugs, Brief brief,
                              List<ChatMessage> history, String packagesView, List<String> catalogNames) {

    public ChatTurnRequest {
        catalogNames = catalogNames == null ? List.of() : List.copyOf(catalogNames);
    }

    /** The shape every caller used before edits existed: a turn with no packages to show. */
    public ChatTurnRequest(String locale, String destinationName, List<String> categorySlugs, Brief brief,
                           List<ChatMessage> history) {
        this(locale, destinationName, categorySlugs, brief, history, null, List.of());
    }
}
