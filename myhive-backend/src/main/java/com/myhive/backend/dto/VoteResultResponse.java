package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoteResultResponse {

    private List<ResultActivityDTO> result;
    private List<SuggestionDTO> suggestions;
    private Integer numberOfTravelers;
    private BigDecimal totalPrice;     // group total of result, snapshot prices
    private BigDecimal budget;         // nullable
    private BigDecimal remaining;      // budget - totalPrice; null when budget is null
    private String destinationName;
    private String destinationSlug;
    private LocalDate startDate;
    private LocalDate endDate;
    private String voteMode;
    private long participantCount;
}
