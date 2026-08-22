package com.myhive.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripExportRequest {

    @NotBlank(message = "Trip name is required")
    @Size(max = 120, message = "Trip name must not exceed 120 characters")
    private String tripName;

    @NotBlank(message = "User email is required")
    @Email(message = "Invalid email format")
    private String userEmail;

    @NotBlank(message = "Customer name is required")
    @Size(max = 120, message = "Customer name must not exceed 120 characters")
    private String customerName;

    private String phone;

    @Positive(message = "Number of travelers must be positive")
    @Max(value = 100, message = "Number of travelers must not exceed 100")
    private Integer numberOfTravelers;

    @Valid
    @NotEmpty(message = "Destinations cannot be empty")
    @Size(max = 25, message = "Destinations list must not exceed 25 entries")
    private List<DestinationExport> destinations;

    @Size(max = 5000, message = "Notes must not exceed 5000 characters")
    private String notes;

    @Size(max = 64, message = "tripId must not exceed 64 characters")
    @Pattern(regexp = "^[\\w.-]+$", message = "tripId may contain only letters, digits, '.', '_' and '-'")
    private String tripId;

    @JsonProperty("utm_source")
    private String utmSource;

    @JsonProperty("utm_medium")
    private String utmMedium;

    @JsonProperty("utm_campaign")
    private String utmCampaign;

    @JsonProperty("utm_term")
    private String utmTerm;

    @JsonProperty("utm_content")
    private String utmContent;

    private String ref;

    private String gclid;

    private String fbclid;

    private String referrer;

    @JsonProperty("event_id")
    @Size(max = 64)
    private String eventId;

    @Size(max = 128)
    private String fbp;

    @Size(max = 256)
    private String fbc;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DestinationExport {
        private String destinationName;
        private String country;
        @Valid
        @NotEmpty(message = "Each destination must have at least one activity")
        @Size(max = 100, message = "Activities list must not exceed 100 entries per destination")
        private List<ActivityExport> activities;
        private Integer duration;
        private String startDate;
        private String endDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityExport {
        private UUID activityId;
        @Size(max = 200, message = "Activity name must not exceed 200 characters")
        private String activityName;
        private String category;
        @Size(max = 5000, message = "Activity description must not exceed 5000 characters")
        private String description;
        @PositiveOrZero(message = "Activity price must not be negative")
        private Double price;
        // Display-only in emails; pricing always uses the server-side snapshot (SEC-1).
        @PositiveOrZero(message = "Activity minimum price must not be negative")
        private BigDecimal minPrice;
        private Integer duration;
        private String timeOfDay;
        private UUID packageId;
        private String packageName;
        @DecimalMin(value = "0.00", message = "Discount must be between 0 and 100")
        @DecimalMax(value = "100.00", message = "Discount must be between 0 and 100")
        private BigDecimal packageDiscountPct;
    }
}
