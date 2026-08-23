package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestionDTO {

    private UUID id;

    @NotBlank(message = "Question prompt is required")
    @Size(max = 500, message = "Prompt must be at most 500 characters")
    private String prompt;

    private int sortOrder;

    @Valid
    @NotEmpty(message = "A question must have at least one answer")
    private List<QuizAnswerDTO> answers;

    /** Per-locale overrides of prompt ({"de": {"prompt": ...}}); admin view only, null = unchanged on save. */
    private Map<String, Map<String, String>> translations;

    public QuizQuestionDTO(UUID id, String prompt, int sortOrder, List<QuizAnswerDTO> answers) {
        this(id, prompt, sortOrder, answers, null);
    }
}
