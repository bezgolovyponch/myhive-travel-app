package com.myhive.backend.dto;

public record AuthValidationResponse(boolean valid, String email, String role) {
}
