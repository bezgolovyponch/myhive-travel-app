package com.myhive.backend.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {
    @NotBlank(message = "User email is required")
    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email must be less than 100 characters")
    private String userEmail;

    @NotEmpty(message = "Activities list cannot be empty")
    private List<BookingActivityItem> activities;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingActivityItem {
        @NotNull(message = "Activity ID is required")
        private UUID activityId;

        @NotNull(message = "Quantity is required")
        private Integer quantity;
    }
}
