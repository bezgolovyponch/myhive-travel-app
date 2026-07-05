package com.myhive.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class VoteSessionCartCreateRequest {
    @NotNull private UUID destinationId;
    @NotNull @Email private String initiatorEmail;
    @NotNull @Min(1) @Max(50) private Integer numberOfTravelers;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;

    @NotEmpty(message = "activityIds must not be empty")
    @Size(max = 50, message = "activityIds may not exceed 50")
    private List<UUID> activityIds;
}
