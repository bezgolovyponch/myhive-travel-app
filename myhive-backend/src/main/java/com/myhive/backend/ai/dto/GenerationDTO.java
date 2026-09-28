package com.myhive.backend.ai.dto;

import com.myhive.backend.ai.model.Brief;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * What {@code GET /ai/generations/&#123;id&#125;} answers while the frontend polls. {@code packages} is
 * filled only once the status is {@code READY} and {@code error} only once it is {@code FAILED}, so
 * the poller can switch on {@code status} alone.
 */
public record GenerationDTO(UUID id, UUID sessionToken, String status, boolean degraded, String selectedPackageKey,
                            Brief brief, List<PackageDTO> packages, ErrorDTO error, LocalDateTime createdAt,
                            LocalDateTime finishedAt) {

    /** {@code retryable} tells the UI whether to offer "try again" or to apologise. */
    public record ErrorDTO(String code, boolean retryable) {
    }
}
