package com.myhive.backend.dto;

import java.util.UUID;

public record TripLeadCreateResponse(UUID id, UUID restoreToken) {
}
