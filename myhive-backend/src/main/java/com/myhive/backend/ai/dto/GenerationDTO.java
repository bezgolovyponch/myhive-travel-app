package com.myhive.backend.ai.dto;

import com.myhive.backend.ai.model.Brief;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * What {@code GET /ai/generations/&#123;id&#125;} answers while the frontend polls. {@code packages} is
 * filled only once the status is {@code READY} and {@code error} only once it is {@code FAILED}, so
 * the poller can switch on {@code status} alone.
 *
 * <p>{@code kind} is {@code GENERATED} for a row the planner model built and {@code EDITED} for one the
 * package editor derived from it; only an {@code EDITED} row carries {@code parentId} and
 * {@code editReport}, whose {@code generationId} is this row's own id.
 */
public record GenerationDTO(UUID id, UUID sessionToken, String status, boolean degraded, String selectedPackageKey,
                            Brief brief, List<PackageDTO> packages, ErrorDTO error, LocalDateTime createdAt,
                            LocalDateTime finishedAt, String kind, UUID parentId, EditDTO editReport) {

    /** {@code retryable} tells the UI whether to offer "try again" or to apologise. */
    public record ErrorDTO(String code, boolean retryable) {
    }
}
