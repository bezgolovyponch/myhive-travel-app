package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizAnswerDTO {

    private UUID id;

    @NotBlank(message = "Answer label is required")
    @Size(max = 200, message = "Answer label must be at most 200 characters")
    private String label;

    private int sortOrder;

    @Valid
    private List<QuizAnswerWeightDTO> weights;
}
