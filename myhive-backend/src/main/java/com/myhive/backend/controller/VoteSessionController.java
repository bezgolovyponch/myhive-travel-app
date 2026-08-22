package com.myhive.backend.controller;

import com.myhive.backend.dto.ParticipantQuizSubmissionRequest;
import com.myhive.backend.dto.PublicQuizDTO;
import com.myhive.backend.dto.VoteActivityResponse;
import com.myhive.backend.dto.VoteBatchRequest;
import com.myhive.backend.dto.VoteRequest;
import com.myhive.backend.dto.VoteResultResponse;
import com.myhive.backend.dto.VoteSessionCartCreateRequest;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.dto.VoteTallyResponse;
import com.myhive.backend.service.VoteSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/vote/sessions")
@RequiredArgsConstructor
public class VoteSessionController {

    private final VoteSessionService voteSessionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VoteSessionResponse createSession(@Valid @RequestBody VoteSessionCreateRequest request) {
        return voteSessionService.createSession(request);
    }

    @PostMapping("/cart")
    @ResponseStatus(HttpStatus.CREATED)
    public VoteSessionResponse createCartSession(@Valid @RequestBody VoteSessionCartCreateRequest request) {
        return voteSessionService.createCartSession(request);
    }

    @GetMapping("/{shareToken}")
    public VoteSessionResponse getSession(@PathVariable UUID shareToken) {
        return voteSessionService.getSession(shareToken);
    }

    @GetMapping("/{shareToken}/activities")
    public List<VoteActivityResponse> getActivities(@PathVariable UUID shareToken) {
        return voteSessionService.getActivities(shareToken);
    }

    @PostMapping("/{shareToken}/votes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void castVote(@PathVariable UUID shareToken, @Valid @RequestBody VoteRequest request) {
        voteSessionService.castVote(shareToken, request);
    }

    @PostMapping("/{shareToken}/votes/batch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void castVotes(@PathVariable UUID shareToken, @Valid @RequestBody VoteBatchRequest request) {
        voteSessionService.castVotes(shareToken, request);
    }

    @GetMapping("/{shareToken}/participant-count")
    public Map<String, Long> getParticipantCount(@PathVariable UUID shareToken) {
        return Map.of("count", voteSessionService.getParticipantCount(shareToken));
    }

    @PostMapping("/{shareToken}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeSession(@PathVariable UUID shareToken, @RequestParam UUID managerToken) {
        voteSessionService.closeSession(shareToken, managerToken);
    }

    @GetMapping("/{shareToken}/result")
    public VoteResultResponse getResult(@PathVariable UUID shareToken) {
        return voteSessionService.getResult(shareToken);
    }

    @GetMapping("/{shareToken}/tally")
    public VoteTallyResponse getTally(@PathVariable UUID shareToken,
                                      @RequestParam(required = false) UUID voterToken,
                                      @RequestParam(required = false) UUID managerToken) {
        return voteSessionService.getTally(shareToken, voterToken, managerToken);
    }

    @GetMapping("/{shareToken}/quiz")
    public PublicQuizDTO getParticipantQuiz(@PathVariable UUID shareToken) {
        return voteSessionService.getParticipantQuiz(shareToken);
    }

    @PostMapping("/{shareToken}/quiz")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submitParticipantQuiz(@PathVariable UUID shareToken,
                                      @Valid @RequestBody ParticipantQuizSubmissionRequest request) {
        voteSessionService.submitParticipantQuiz(shareToken, request);
    }

    public record RecordOpenRequest(@NotNull UUID voterToken) {}

    @PostMapping("/{shareToken}/opens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordOpen(@PathVariable UUID shareToken, @Valid @RequestBody RecordOpenRequest request) {
        voteSessionService.recordOpen(shareToken, request.voterToken());
    }
}
