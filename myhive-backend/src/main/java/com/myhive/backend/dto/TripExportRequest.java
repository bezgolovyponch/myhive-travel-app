package com.myhive.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripExportRequest {

    @NotBlank(message = "Trip name is required")
    private String tripName;

    @NotBlank(message = "User email is required")
    private String userEmail;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    private String phone;

    private Integer numberOfTravelers;

    @NotEmpty(message = "Destinations cannot be empty")
    private List<DestinationExport> destinations;

    private String notes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DestinationExport {
        private String destinationName;
        private String country;
        private List<ActivityExport> activities;
        private Integer duration;
        private String startDate;
        private String endDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityExport {
        private String activityName;
        private String category;
        private String description;
        private Double price;
        private Integer duration;
        private String timeOfDay;
    }
}
