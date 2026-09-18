package com.myhive.backend.ai.dto;

import com.myhive.backend.ai.model.Brief;

import java.util.List;

/**
 * Answer to one user turn. {@code generation} is non-null on the turn that completed the brief — there is
 * no confirmation step — and is then the poller's cue to start; on a turn that edited the packages it is
 * the new {@code EDITED} generation instead, already {@code READY}, so there is nothing to poll for.
 *
 * <p>{@code edit} is non-null exactly on the turns that carried edits, applied or not.
 */
public record TurnResponseDTO(MessageDTO message, Brief brief, List<String> missingFields, boolean readyToGenerate,
                              GenerationDTO generation, EditDTO edit) {
}
