package com.myhive.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class PriceQuoteRequest {
    @NotEmpty @Size(max = 50) private List<@NotNull UUID> activityIds;
    @NotNull @Min(1) @Max(50) private Integer travelers;
}
