package com.myhive.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminPaymentLinkRequest(
        @NotNull(message = "amountCents is required")
        @Positive(message = "amountCents must be positive")
        Long amountCents) {
}
