package com.myhive.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizAnswerWeightDTO {

    @NotNull(message = "categoryId is required")
    private UUID categoryId;

    private int weight;
}
