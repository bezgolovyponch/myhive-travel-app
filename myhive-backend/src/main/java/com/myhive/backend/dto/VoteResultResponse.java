package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class VoteResultResponse {
    private String destinationName;
    private String destinationSlug;
    private List<VoteActivityResponse> activities;
    private BigDecimal totalPrice;
    private Integer numberOfTravelers;
    private LocalDate startDate;
    private LocalDate endDate;
}
