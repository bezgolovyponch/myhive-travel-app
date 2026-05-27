package com.myhive.backend.controller;

import com.myhive.backend.dto.PublicQuizDTO;
import com.myhive.backend.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/vote")
@RequiredArgsConstructor
public class VoteController {

    private final QuizService quizService;

    @GetMapping("/destinations/{destinationId}/quiz")
    public PublicQuizDTO getPublicQuiz(@PathVariable UUID destinationId) {
        return quizService.getPublicQuiz(destinationId);
    }
}
