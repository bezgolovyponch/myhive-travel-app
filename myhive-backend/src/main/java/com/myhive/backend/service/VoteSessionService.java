package com.myhive.backend.service;

import com.myhive.backend.dto.ParticipantQuizSubmissionRequest;
import com.myhive.backend.dto.PublicQuizDTO;
import com.myhive.backend.dto.QuizResponseDTO;
import com.myhive.backend.dto.ResultActivityDTO;
import com.myhive.backend.dto.SuggestionDTO;
import com.myhive.backend.dto.VoteActivityResponse;
import com.myhive.backend.dto.VoteBatchRequest;
import com.myhive.backend.dto.VoteRequest;
import com.myhive.backend.dto.VoteResultResponse;
import com.myhive.backend.dto.VoteSessionCartCreateRequest;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.entity.VoteActivityLike;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.entity.VoteSessionQuizResponse;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.EmailSendException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.exception.ResultNotReadyException;
import com.myhive.backend.exception.SessionFullException;
import com.myhive.backend.model.VoteMode;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.ActivityVoteCount;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.QuizAnswerRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionActivityRepository;
import com.myhive.backend.repository.VoteSessionQuizResponseRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.repository.VoteSessionResultActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class VoteSessionService {

    private final VoteSessionRepository voteSessionRepository;
    private final VoteActivityLikeRepository voteActivityLikeRepository;
    private final VoteSessionResultActivityRepository resultActivityRepository;
    private final DestinationRepository destinationRepository;
    private final CategoryRepository categoryRepository;
    private final ActivityRepository activityRepository;
    private final EmailService emailService;
    private final QuizService quizService;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final VoteSessionActivityRepository voteSessionActivityRepository;
    private final VoteSessionQuizResponseRepository voteSessionQuizResponseRepository;
    private final VoteSuggestionsService voteSuggestionsService;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.frontend.url:https://trivlu.com}")
    private String frontendUrl;

    @Transactional
    public VoteSessionResponse createSession(VoteSessionCreateRequest request) {
        Destination destination = destinationRepository.findById(request.getDestinationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found"));

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }

        List<QuizResponseDTO> quizResponses = request.getQuizResponses() == null
                ? List.of() : request.getQuizResponses();
        validateQuizResponses(destination, quizResponses);

        List<UUID> organizerCats = computeOrganizerCategories(destination, quizResponses);

        Map<UUID, Activity> activitiesById = loadAndValidateCuratedActivities(
                destination, organizerCats, request.getActivityIds());

        VoteSession session = newSession(destination, request.getInitiatorEmail(), request.getNumberOfTravelers(),
                request.getStartDate(), request.getEndDate(), VoteMode.QUIZ, request.getBudget());

        persistBallot(session, request.getActivityIds(), activitiesById);

        for (QuizResponseDTO response : quizResponses) {
            QuizQuestion question = quizQuestionRepository.findById(response.getQuestionId()).orElseThrow();
            QuizAnswer answer = quizAnswerRepository.findById(response.getAnswerId()).orElseThrow();
            VoteSessionQuizResponse row = new VoteSessionQuizResponse();
            row.setSession(session);
            row.setVoterToken(request.getVoterToken());
            row.setQuestion(question);
            row.setAnswer(answer);
            voteSessionQuizResponseRepository.save(row);
        }

        sendVoteCreatedConfirmationQuietly(session);

        long participantCount = voteActivityLikeRepository
                .countDistinctVoterTokensBySessionId(session.getId());
        return toResponse(session, participantCount, session.getManagerToken());
    }

    @Transactional
    public VoteSessionResponse createCartSession(VoteSessionCartCreateRequest request) {
        Destination destination = destinationRepository.findById(request.getDestinationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found"));

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }

        List<UUID> activityIds = new ArrayList<>(new LinkedHashSet<>(request.getActivityIds()));
        Map<UUID, Activity> activitiesById = loadAndValidateDestinationActivities(destination, activityIds);

        VoteSession session = newSession(destination, request.getInitiatorEmail(), request.getNumberOfTravelers(),
                request.getStartDate(), request.getEndDate(), VoteMode.CART, null);

        persistBallot(session, activityIds, activitiesById);
        sendVoteCreatedConfirmationQuietly(session);

        // A brand-new session has no voters yet.
        return toResponse(session, 0, session.getManagerToken());
    }

    private VoteSession newSession(Destination destination, String initiatorEmail, Integer numberOfTravelers,
                                   LocalDate startDate, LocalDate endDate,
                                   VoteMode voteMode, BigDecimal budget) {
        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail(initiatorEmail);
        session.setNumberOfTravelers(numberOfTravelers);
        session.setStartDate(startDate);
        session.setEndDate(endDate);
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setVoteMode(voteMode);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(24));
        session.setBudget(budget);
        return voteSessionRepository.save(session);
    }

    private void validateQuizResponses(Destination destination, List<QuizResponseDTO> responses) {
        if (responses.isEmpty()) {
            return;
        }
        List<QuizQuestion> destinationQuiz =
                quizQuestionRepository.findByDestinationIdOrderBySortOrder(destination.getId());
        Set<UUID> destinationQuestionIds = destinationQuiz.stream()
                .map(QuizQuestion::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<UUID> seenQuestions = new HashSet<>();
        for (QuizResponseDTO response : responses) {
            if (!destinationQuestionIds.contains(response.getQuestionId())) {
                throw new BadRequestException(
                        "questionId " + response.getQuestionId() + " is not part of this destination's quiz");
            }
            if (!seenQuestions.add(response.getQuestionId())) {
                throw new BadRequestException(
                        "two responses provided for questionId " + response.getQuestionId());
            }
            QuizAnswer answer = quizAnswerRepository.findById(response.getAnswerId())
                    .orElseThrow(() -> new BadRequestException(
                            "answerId " + response.getAnswerId() + " does not exist"));
            if (!answer.getQuestion().getId().equals(response.getQuestionId())) {
                throw new BadRequestException(
                        "answerId " + response.getAnswerId() + " does not belong to questionId " + response.getQuestionId());
            }
        }
        if (!destinationQuiz.isEmpty() && seenQuestions.size() != destinationQuestionIds.size()) {
            throw new BadRequestException("quizResponses is incomplete — every destination question must be answered");
        }
    }

    private List<UUID> computeOrganizerCategories(Destination destination, List<QuizResponseDTO> responses) {
        List<UUID> answerIds = responses.stream().map(QuizResponseDTO::getAnswerId).toList();
        List<UUID> snapshot = quizService.snapshot(answerIds);
        if (!snapshot.isEmpty()) {
            return snapshot;
        }
        return destination.getCategories().stream()
                .filter(Category::isVotable)
                .map(Category::getId)
                .toList();
    }

    private Map<UUID, Activity> loadAndValidateCuratedActivities(Destination destination,
                                                                 List<UUID> organizerCats,
                                                                 List<UUID> activityIds) {
        Set<UUID> organizerCatSet = new HashSet<>(organizerCats);
        Map<UUID, Activity> byId = loadAndValidateDestinationActivities(destination, activityIds);
        for (UUID id : activityIds) {
            Activity activity = byId.get(id);
            boolean hasEligibleCategory = activity.getCategories().stream()
                    .map(Category::getId)
                    .anyMatch(organizerCatSet::contains);
            if (!hasEligibleCategory) {
                throw new BadRequestException(
                        "activityId " + id + " is not in any of the organizer's quiz categories");
            }
        }
        return byId;
    }

    private Map<UUID, Activity> loadAndValidateDestinationActivities(Destination destination,
                                                                     List<UUID> activityIds) {
        Map<UUID, Activity> byId = activityRepository.findAllById(activityIds).stream()
                .collect(Collectors.toMap(Activity::getId, a -> a));
        for (UUID id : activityIds) {
            Activity activity = byId.get(id);
            if (activity == null) {
                throw new BadRequestException("activityId " + id + " does not exist");
            }
            if (!activity.getDestination().getId().equals(destination.getId())) {
                throw new BadRequestException(
                        "activityId " + id + " does not belong to destination " + destination.getId());
            }
        }
        return byId;
    }

    private void persistBallot(VoteSession session, List<UUID> activityIds,
                               Map<UUID, Activity> activitiesById) {
        int sortOrder = 0;
        for (UUID activityId : activityIds) {
            Activity activity = activitiesById.get(activityId);
            VoteSessionActivity row = new VoteSessionActivity();
            row.setSession(session);
            row.setActivity(activity);
            row.setActivityName(activity.getName());
            row.setPrice(activity.getPrice());
            row.setSortOrder(sortOrder++);
            voteSessionActivityRepository.save(row);
        }
    }

    private void sendVoteCreatedConfirmationQuietly(VoteSession session) {
        if (!emailEnabled) {
            return;
        }
        try {
            emailService.sendVoteCreatedConfirmation(session, frontendUrl);
        } catch (EmailSendException e) {
            // A failed confirmation email must never fail session creation — log and move on.
            log.error("Failed to send vote-created confirmation for session {}: {}",
                    session.getId(), e.getMessage(), e);
        }
    }

    public VoteSessionResponse getSession(UUID shareToken) {
        VoteSession session = findByShareToken(shareToken);
        long count = voteActivityLikeRepository.countDistinctVoterTokensBySessionId(session.getId());
        return toResponse(session, count);
    }

    public List<VoteActivityResponse> getActivities(UUID shareToken) {
        VoteSession session = findByShareToken(shareToken);
        String destinationSlug = session.getDestination().getSlug();

        List<VoteSessionActivity> curated = voteSessionActivityRepository
                .findBySessionIdOrderBySortOrder(session.getId());
        if (!curated.isEmpty()) {
            return curated.stream()
                    .map(row -> toActivityResponse(row.getActivity(), destinationSlug))
                    .toList();
        }

        // Legacy fallback: historical sessions written under the old category-swipe flow,
        // which only have vote_session_liked_categories. New sessions always have curated rows.
        Set<UUID> categoryIds = session.getLikedCategories().stream()
                .map(Category::getId)
                .collect(Collectors.toSet());
        List<Activity> activities = activityRepository.findByDestinationIdAndCategoriesIdIn(
                session.getDestination().getId(), categoryIds);
        return activities.stream()
                .map(activity -> toActivityResponse(activity, destinationSlug))
                .toList();
    }

    public PublicQuizDTO getParticipantQuiz(UUID shareToken) {
        VoteSession session = findByShareToken(shareToken);
        return quizService.getPublicQuiz(session.getDestination().getId());
    }

    @Transactional
    public void submitParticipantQuiz(UUID shareToken, ParticipantQuizSubmissionRequest request) {
        VoteSession session = findByShareToken(shareToken);
        if (session.getStatus() != VoteSessionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is no longer active");
        }

        List<QuizResponseDTO> responses = request.getResponses() == null ? List.of() : request.getResponses();
        validateQuizResponses(session.getDestination(), responses);

        boolean alreadySubmitted = voteSessionQuizResponseRepository
                .findBySessionId(session.getId()).stream()
                .anyMatch(r -> r.getVoterToken().equals(request.getVoterToken()));
        if (alreadySubmitted) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz already submitted for this voter");
        }

        for (QuizResponseDTO response : responses) {
            QuizQuestion question = quizQuestionRepository.findById(response.getQuestionId()).orElseThrow();
            QuizAnswer answer = quizAnswerRepository.findById(response.getAnswerId()).orElseThrow();
            VoteSessionQuizResponse row = new VoteSessionQuizResponse();
            row.setSession(session);
            row.setVoterToken(request.getVoterToken());
            row.setQuestion(question);
            row.setAnswer(answer);
            voteSessionQuizResponseRepository.save(row);
        }
    }

    @Transactional
    public void castVote(UUID shareToken, VoteRequest request) {
        VoteSession session = findByShareToken(shareToken);

        if (session.getStatus() != VoteSessionStatus.ACTIVE) {
            throw new BadRequestException("Session is no longer active");
        }

        if (session.getVoteMode() == VoteMode.CART && !request.getLiked()) {
            throw new BadRequestException("This vote session accepts upvotes only");
        }

        assertVoterAllowed(session, request.getVoterToken());

        Activity activity = activityRepository.findById(request.getActivityId())
                .orElseThrow(() -> new ResourceNotFoundException("Activity not found"));

        if (!activity.getDestination().getId().equals(session.getDestination().getId())) {
            throw new BadRequestException("Activity does not belong to this session's destination");
        }

        VoteActivityLike like = voteActivityLikeRepository
                .findBySessionIdAndVoterTokenAndActivityId(
                        session.getId(), request.getVoterToken(), request.getActivityId())
                .orElse(new VoteActivityLike());

        like.setSession(session);
        like.setVoterToken(request.getVoterToken());
        like.setActivity(activity);
        like.setLiked(request.getLiked());
        voteActivityLikeRepository.save(like);
    }

    @Transactional
    public void castVotes(UUID shareToken, VoteBatchRequest request) {
        VoteSession session = findByShareToken(shareToken);

        if (session.getStatus() != VoteSessionStatus.ACTIVE) {
            throw new BadRequestException("Session is no longer active");
        }

        if (session.getVoteMode() == VoteMode.CART
                && request.getVotes().stream().anyMatch(item -> !item.getLiked())) {
            throw new BadRequestException("This vote session accepts upvotes only");
        }

        assertVoterAllowed(session, request.getVoterToken());

        Map<UUID, VoteBatchRequest.VoteItem> deduplicatedVotes = new LinkedHashMap<>();
        for (VoteBatchRequest.VoteItem item : request.getVotes()) {
            deduplicatedVotes.put(item.getActivityId(), item);
        }

        Map<UUID, Activity> activitiesById = activityRepository
                .findAllById(deduplicatedVotes.keySet()).stream()
                .collect(Collectors.toMap(Activity::getId, a -> a));

        List<VoteActivityLike> likes = new ArrayList<>();
        for (VoteBatchRequest.VoteItem item : deduplicatedVotes.values()) {
            Activity activity = activitiesById.get(item.getActivityId());
            if (activity == null) {
                throw new ResourceNotFoundException("Activity not found: " + item.getActivityId());
            }
            if (!activity.getDestination().getId().equals(session.getDestination().getId())) {
                throw new BadRequestException("Activity does not belong to this session's destination");
            }
            VoteActivityLike like = voteActivityLikeRepository
                    .findBySessionIdAndVoterTokenAndActivityId(
                            session.getId(), request.getVoterToken(), item.getActivityId())
                    .orElse(new VoteActivityLike());
            like.setSession(session);
            like.setVoterToken(request.getVoterToken());
            like.setActivity(activity);
            like.setLiked(item.getLiked());
            likes.add(like);
        }
        voteActivityLikeRepository.saveAll(likes);
    }

    public long getParticipantCount(UUID shareToken) {
        VoteSession session = findByShareToken(shareToken);
        return voteActivityLikeRepository.countDistinctVoterTokensBySessionId(session.getId());
    }

    public VoteResultResponse getResult(UUID shareToken) {
        VoteSession session = findByShareToken(shareToken);
        if (session.getStatus() != VoteSessionStatus.COMPLETED) {
            throw new ResultNotReadyException("Result not available yet");
        }

        List<VoteSessionResultActivity> resultRows = resultActivityRepository
                .findBySessionIdOrderBySortOrder(session.getId());
        Map<UUID, VoteSessionActivity> curatedByActivity = voteSessionActivityRepository
                .findBySessionIdOrderBySortOrder(session.getId()).stream()
                .collect(Collectors.toMap(row -> row.getActivity().getId(), row -> row));
        Map<UUID, ActivityVoteCount> countsByActivity = voteActivityLikeRepository
                .findVoteCountsBySessionId(session.getId()).stream()
                .collect(Collectors.toMap(ActivityVoteCount::getActivityId, c -> c));

        List<ResultActivityDTO> result = resultRows.stream().map(r -> {
            Activity activity = r.getActivity();
            UUID activityId = activity.getId();
            VoteSessionActivity curated = curatedByActivity.get(activityId);
            ActivityVoteCount counts = countsByActivity.get(activityId);
            long like = counts == null ? 0 : counts.getLikeCount();
            long skip = counts == null ? 0 : counts.getSkipCount();
            String destinationSlug = activity.getDestination() == null
                    ? null : activity.getDestination().getSlug();
            return new ResultActivityDTO(activityId, curated.getActivityName(),
                    curated.getPrice(), like, skip,
                    activity.getSlug(), destinationSlug, activity.getImageUrl(),
                    activity.getDuration(), activity.getDescription(), activity.getIncludes());
        }).toList();

        BigDecimal travelers = BigDecimal.valueOf(session.getNumberOfTravelers());
        BigDecimal totalPrice = result.stream()
                .map(r -> r.getPrice().multiply(travelers))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal budget = session.getBudget();
        BigDecimal remaining = budget == null ? null : budget.subtract(totalPrice);

        List<SuggestionDTO> suggestions = voteSuggestionsService.buildSuggestions(session);

        return new VoteResultResponse(result, suggestions, session.getNumberOfTravelers(),
                totalPrice, budget, remaining,
                session.getDestination().getName(), session.getDestination().getSlug(),
                session.getStartDate(), session.getEndDate());
    }

    public VoteSession requireManager(UUID shareToken, UUID managerToken) {
        VoteSession session = findByShareToken(shareToken);
        if (!session.getManagerToken().equals(managerToken)) {
            throw new BadRequestException("Invalid manager token");
        }
        return session;
    }

    public VoteSession requireManagerById(UUID sessionId, UUID managerToken) {
        VoteSession session = voteSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Vote session not found"));
        if (!session.getManagerToken().equals(managerToken)) {
            throw new BadRequestException("Invalid manager token");
        }
        return session;
    }

    /**
     * Acquires a pessimistic write lock on the session row within the caller's transaction, so concurrent
     * deposit-creation requests for the same vote session serialize and cannot create duplicate
     * bookings/checkout sessions (M1). Joins the surrounding transaction (REQUIRED).
     */
    @Transactional
    public void lockSession(UUID sessionId) {
        voteSessionRepository.findByIdForUpdate(sessionId);
    }

    @Transactional
    public void closeSession(UUID shareToken, UUID managerToken) {
        VoteSession session = findByShareToken(shareToken);
        if (session.getStatus() != VoteSessionStatus.ACTIVE) {
            throw new BadRequestException("Session is not active");
        }
        if (!managerToken.equals(session.getManagerToken())) {
            throw new BadRequestException("Invalid manager token");
        }
        processSession(session);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSession(VoteSession session) {
        session = voteSessionRepository.findById(session.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Vote session not found"));

        List<VoteSessionActivity> curated =
                voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId());
        if (curated.isEmpty()) {
            // Legacy session pre-Plan-2: no curated list to resolve against.
            session.setStatus(VoteSessionStatus.COMPLETED);
            voteSessionRepository.save(session);
            return;
        }

        Map<UUID, ActivityVoteCount> counts =
                voteActivityLikeRepository.findVoteCountsBySessionId(session.getId()).stream()
                        .collect(Collectors.toMap(ActivityVoteCount::getActivityId, c -> c));

        record Ranked(VoteSessionActivity row, long score, int featuredWeight) {}

        List<Ranked> ranked = curated.stream()
                .map(row -> {
                    ActivityVoteCount c = counts.get(row.getActivity().getId());
                    long like = c == null ? 0 : c.getLikeCount();
                    long skip = c == null ? 0 : c.getSkipCount();
                    return new Ranked(row, like - skip, row.getActivity().getFeaturedWeight());
                })
                .filter(r -> r.score() > 0)
                .sorted(Comparator
                        .comparingLong(Ranked::score).reversed()
                        .thenComparing(Comparator.comparingInt(Ranked::featuredWeight).reversed())
                        .thenComparing(r -> r.row().getActivity().getId()))
                .toList();

        BigDecimal travelers = BigDecimal.valueOf(session.getNumberOfTravelers());
        BigDecimal budget = session.getBudget();
        BigDecimal running = BigDecimal.ZERO;
        int sortOrder = 0;
        for (Ranked r : ranked) {
            BigDecimal groupCost = r.row().getPrice().multiply(travelers);
            if (budget != null && running.add(groupCost).compareTo(budget) > 0) {
                continue;   // skip-and-continue
            }
            VoteSessionResultActivity resultRow = new VoteSessionResultActivity();
            resultRow.setSession(session);
            resultRow.setActivity(r.row().getActivity());
            resultRow.setSortOrder(sortOrder++);
            resultActivityRepository.save(resultRow);
            running = running.add(groupCost);
        }

        session.setStatus(VoteSessionStatus.COMPLETED);
        voteSessionRepository.save(session);
        log.info("Processed vote session {} — {} activities selected", session.getId(), sortOrder);

        if (emailEnabled) {
            List<VoteSessionResultActivity> results =
                    resultActivityRepository.findBySessionIdOrderBySortOrder(session.getId());
            emailService.sendVoteResult(session, results, frontendUrl);
        }
    }

    private void assertVoterAllowed(VoteSession session, UUID voterToken) {
        boolean isNewVoter = !voteActivityLikeRepository
                .existsBySessionIdAndVoterToken(session.getId(), voterToken);
        if (isNewVoter) {
            long voterCount = voteActivityLikeRepository
                    .countDistinctVoterTokensBySessionId(session.getId());
            if (voterCount >= session.getMaxParticipants()) {
                throw new SessionFullException("Session has reached the maximum number of participants");
            }
        }
    }

    private VoteSession findByShareToken(UUID shareToken) {
        return voteSessionRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new ResourceNotFoundException("Vote session not found"));
    }

    private VoteSessionResponse toResponse(VoteSession session, long participantCount) {
        return toResponse(session, participantCount, null);
    }

    private VoteSessionResponse toResponse(VoteSession session, long participantCount, UUID managerToken) {
        int travelers = session.getNumberOfTravelers() != null ? session.getNumberOfTravelers() : 0;
        Instant expiresAt = session.getExpiresAt().toInstant(ZoneOffset.UTC);
        return new VoteSessionResponse(
                session.getShareToken(),
                session.getDestination().getName(),
                session.getDestination().getSlug(),
                session.getStatus().name(),
                expiresAt,
                participantCount,
                travelers,
                managerToken,
                session.getVoteMode().name());
    }

    private Set<UUID> resolveDestinationCategoryIds(Destination destination) {
        Set<Category> explicit = destination.getCategories();
        if (!explicit.isEmpty()) {
            return explicit.stream().map(Category::getId).collect(Collectors.toSet());
        }
        List<Activity> activities = destination.getActivities();
        if (activities == null) {
            return Set.of();
        }
        return activities.stream()
                .flatMap(a -> a.getCategories().stream())
                .map(Category::getId)
                .collect(Collectors.toSet());
    }

    private VoteActivityResponse toActivityResponse(Activity activity, String destinationSlug) {
        return new VoteActivityResponse(
                activity.getId(),
                activity.getName(),
                activity.getDescription(),
                activity.getPrice(),
                activity.getDuration(),
                activity.getImageUrl(),
                activity.getSlug(),
                destinationSlug);
    }
}
