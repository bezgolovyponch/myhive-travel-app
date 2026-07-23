package com.myhive.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class TripLeadCreateRequest {
    @NotNull @Email private String email;
    private UUID destinationId;
    @Min(1) @Max(99) private Integer numberOfTravelers;
    private LocalDate startDate;
    private LocalDate endDate;
    @PositiveOrZero private BigDecimal budget;
}
