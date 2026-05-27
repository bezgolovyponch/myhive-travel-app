package com.myhive.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizResponseDTO {

    @NotNull(message = "questionId is required")
    private UUID questionId;

    @NotNull(message = "answerId is required")
    private UUID answerId;
}
