package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VotePoolRequest {

    @NotNull(message = "destinationId is required")
    private UUID destinationId;

    @Valid
    @NotNull(message = "responses is required (use an empty list when there is no quiz)")
    private List<QuizResponseDTO> responses;
}
