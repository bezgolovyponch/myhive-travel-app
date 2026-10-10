package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.edit.PackagesView;
import com.myhive.backend.ai.model.Brief;

import java.util.List;

/**
 * One chat turn's input. {@code packagesView} and {@code catalogNames} are what the model needs to
 * propose edits and are filled only once a generation exists; a null view means "no packages yet", and
 * the prompt then carries no edit block at all. {@code extrasCatalog} is the one line naming every
 * activity's optional extras with their prices ({@link PackagesView#NO_EXTRAS} when nothing offers any or
 * the caller gave none), read only with packages; never null or blank.
 */
public record ChatTurnRequest(String locale, String destinationName, List<String> categorySlugs, Brief brief,
                              List<ChatMessage> history, String packagesView, List<String> catalogNames,
                              String extrasCatalog) {

    public ChatTurnRequest {
        catalogNames = catalogNames == null ? List.of() : List.copyOf(catalogNames);
        extrasCatalog = extrasCatalog == null || extrasCatalog.isBlank() ? PackagesView.NO_EXTRAS : extrasCatalog;
    }

    /** A turn whose catalog lists no extras. */
    public ChatTurnRequest(String locale, String destinationName, List<String> categorySlugs, Brief brief,
                           List<ChatMessage> history, String packagesView, List<String> catalogNames) {
        this(locale, destinationName, categorySlugs, brief, history, packagesView, catalogNames,
                PackagesView.NO_EXTRAS);
    }

    /** The shape every caller used before edits existed: a turn with no packages to show. */
    public ChatTurnRequest(String locale, String destinationName, List<String> categorySlugs, Brief brief,
                           List<ChatMessage> history) {
        this(locale, destinationName, categorySlugs, brief, history, null, List.of());
    }
}
