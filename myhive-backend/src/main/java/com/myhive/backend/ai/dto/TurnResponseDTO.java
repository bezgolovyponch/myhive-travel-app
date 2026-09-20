package com.myhive.backend.ai.dto;

import com.myhive.backend.ai.model.Brief;

import java.util.List;

/**
 * Answer to one user turn. {@code generation} is non-null exactly on the turn that completed the
 * brief — there is no confirmation step — and is the poller's cue to start.
 */
public record TurnResponseDTO(MessageDTO message, Brief brief, List<String> missingFields, boolean readyToGenerate,
                              GenerationDTO generation) {
}
