package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicQuizQuestionDTO {

    private UUID id;
    private String prompt;
    private List<PublicQuizAnswerDTO> answers;
}
