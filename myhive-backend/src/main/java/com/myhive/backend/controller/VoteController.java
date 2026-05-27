package com.myhive.backend.controller;

import com.myhive.backend.dto.PublicQuizDTO;
import com.myhive.backend.dto.VotePoolRequest;
import com.myhive.backend.dto.VotePoolResponse;
import com.myhive.backend.service.QuizService;
import com.myhive.backend.service.VotePoolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/vote")
@RequiredArgsConstructor
public class VoteController {

    private final QuizService quizService;
    private final VotePoolService votePoolService;

    @GetMapping("/destinations/{destinationId}/quiz")
    public PublicQuizDTO getPublicQuiz(@PathVariable UUID destinationId) {
        return quizService.getPublicQuiz(destinationId);
    }

    @PostMapping("/pool")
    public VotePoolResponse buildPool(@Valid @RequestBody VotePoolRequest request) {
        return votePoolService.buildPool(request);
    }
}
