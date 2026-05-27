package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizDTO {

    @Valid
    @NotNull(message = "questions is required (use an empty list to clear the quiz)")
    private List<QuizQuestionDTO> questions;
}
