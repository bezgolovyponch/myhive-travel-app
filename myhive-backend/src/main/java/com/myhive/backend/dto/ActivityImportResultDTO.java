package com.myhive.backend.dto;

import java.time.Instant;

public record ActivityImportResultDTO(int rowsUpdated, Instant appliedAt) {
}
