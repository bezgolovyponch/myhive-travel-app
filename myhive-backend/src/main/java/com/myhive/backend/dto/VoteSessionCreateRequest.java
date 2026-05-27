package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class VoteSessionCreateRequest {
    @NotNull private UUID destinationId;
    @NotNull @Email private String initiatorEmail;
    @NotNull @Min(1) @Max(50) private Integer numberOfTravelers;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;

    @Positive(message = "budget must be > 0 when present")
    private BigDecimal budget;

    @NotNull private UUID voterToken;

    @Valid
    private List<QuizResponseDTO> quizResponses;

    @NotEmpty(message = "activityIds must not be empty")
    @Size(max = 50, message = "activityIds may not exceed 50")
    private List<UUID> activityIds;

    // Retained for one release cycle so old clients still validate; ignored by the service.
    @Size(max = 20)
    private List<UUID> likedCategoryIds;
}
