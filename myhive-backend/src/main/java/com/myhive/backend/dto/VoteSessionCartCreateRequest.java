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
    // The organiser leaves a WhatsApp number or an email (or both); a vote without either is rejected.
    @Email private String initiatorEmail;
    /** E.164 with the country code ("+447700900123"); spaces and dashes are tolerated. */
    @Size(max = 32) private String initiatorPhone;
    /**
     * Optional link token picked by the browser, so the organiser's WhatsApp message (which carries
     * the link) can open in the same tap, before this request returns. Null = the server picks one.
     */
    private UUID shareToken;
    /**
     * Optional manager token picked by the same browser. With both tokens the create is repeatable: a
     * retry after a lost response gets the vote already created back instead of a conflict. Null = the
     * server picks one.
     */
    private UUID managerToken;
    @NotNull @Min(1) @Max(50) private Integer numberOfTravelers;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;

    @NotEmpty(message = "activityIds must not be empty")
    @Size(max = 50, message = "activityIds may not exceed 50")
    private List<UUID> activityIds;

    /**
     * Optional: the day of the trip each activity is planned for (1 = the first day), as the planner's
     * trip draft has them. Activities left out, and votes sent without it, have no day.
     */
    private java.util.Map<UUID, @Min(1) @Max(31) Integer> activityDays;

    /** Locale the initiator is browsing in ("de"); drives the language of the vote emails. Null = English. */
    @Size(max = 8) private String locale;
}
