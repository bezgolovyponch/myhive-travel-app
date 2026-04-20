package com.myhive.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ActivityImportApplyRequest(@NotBlank String token) {
}
