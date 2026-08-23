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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/vote")
@RequiredArgsConstructor
public class VoteController {

    private final QuizService quizService;
    private final VotePoolService votePoolService;

    // `locale` (en/de/…) localizes the copy in place; absent = English.

    @GetMapping("/destinations/{destinationId}/quiz")
    public PublicQuizDTO getPublicQuiz(@PathVariable UUID destinationId,
                                       @RequestParam(required = false) String locale) {
        return quizService.getPublicQuiz(destinationId, locale);
    }

    @PostMapping("/pool")
    public VotePoolResponse buildPool(@Valid @RequestBody VotePoolRequest request,
                                      @RequestParam(required = false) String locale) {
        return votePoolService.buildPool(request, locale);
    }
}
