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
import com.myhive.backend.dto.VoteSessionActivityAddRequest;
import com.myhive.backend.dto.VoteSessionCartCreateRequest;
import com.myhive.backend.dto.VoteSessionContactRequest;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.dto.VoteTallyResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.entity.VoteActivityLike;
import com.myhive.backend.entity.VoteRecommendation;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.entity.VoteSessionQuizResponse;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ConflictException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.exception.ResultNotReadyException;
import com.myhive.backend.exception.SessionFullException;
import com.myhive.backend.model.ContactSource;
import com.myhive.backend.model.VoteMode;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRecommendationCount;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.ActivityVoteCount;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.QuizAnswerRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteRecommendationRepository;
import com.myhive.backend.repository.VoteSessionActivityRepository;
import com.myhive.backend.repository.VoteSessionQuizResponseRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.repository.VoteSessionResultActivityRepository;
import com.myhive.backend.util.PhoneNumbers;
import com.myhive.backend.util.Translations;
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
import java.util.Optional;
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
    private final TripLeadService tripLeadService;
    private final ContactService contactService;
    private final VoteRecommendationRepository voteRecommendationRepository;

    @Value("${app.frontend.url:https://trivlu.com}")
    private String frontendUrl;

    @Transactional
    public VoteSessionResponse createSession(VoteSessionCreateRequest request) {
        Optional<VoteSessionResponse> alreadyCreated = alreadyCreated(request.getShareToken(), request.getManagerToken());
        if (alreadyCreated.isPresent()) {
            return alreadyCreated.get();
        }
        Destination destination = destinationRepository.findById(request.getDestinationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found"));

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }

        List<QuizResponseDTO> quizResponses = request.getQuizResponses() == null
                ? List.of() : request.getQuizResponses();
        validateQuizResponses(destination, quizResponses);

        // The ballot is the organizer's Trip Builder cart, which may hold any
        // activity of the destination — not only quiz-matched ones — so this
        // validates destination membership only, same as the CART flow.
        Map<UUID, Activity> activitiesById =
                loadAndValidateDestinationActivities(destination, request.getActivityIds());

        VoteSession session = newSession(request.getShareToken(), request.getManagerToken(), destination,
                request.getInitiatorEmail(), parsePhone(request.getInitiatorPhone()), request.getNumberOfTravelers(),
                request.getStartDate(), request.getEndDate(), VoteMode.QUIZ, request.getBudget(), request.getLocale());

        persistBallot(session, request.getActivityIds(), activitiesById, null);

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
        Optional<VoteSessionResponse> alreadyCreated = alreadyCreated(request.getShareToken(), request.getManagerToken());
        if (alreadyCreated.isPresent()) {
            return alreadyCreated.get();
        }
        Destination destination = destinationRepository.findById(request.getDestinationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found"));

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }

        // The vote modal only creates a vote once the organiser left a way to reach them.
        String phone = parsePhone(request.getInitiatorPhone());
        if (normalizeEmail(request.getInitiatorEmail()) == null && phone == null) {
            throw new BadRequestException("Leave a WhatsApp number or an email");
        }

        List<UUID> activityIds = new ArrayList<>(new LinkedHashSet<>(request.getActivityIds()));
        Map<UUID, Activity> activitiesById = loadAndValidateDestinationActivities(destination, activityIds);

        VoteSession session = newSession(request.getShareToken(), request.getManagerToken(), destination,
                request.getInitiatorEmail(), phone, request.getNumberOfTravelers(),
                request.getStartDate(), request.getEndDate(), VoteMode.CART, null, request.getLocale());

        persistBallot(session, activityIds, activitiesById, request.getActivityDays());
        sendVoteCreatedConfirmationQuietly(session);

        // A brand-new session has no voters yet.
        return toResponse(session, 0, session.getManagerToken());
    }

    /**
     * The vote this browser already created with these two tokens, for a retry whose first response was
     * lost on the way to the phone: it gets that vote back, with the same manager token, so the message
     * already sent to the group keeps pointing at it and no second vote splits the group. A link token in
     * use with any other manager token — or with none — is a conflict, as it always was.
     */
    private Optional<VoteSessionResponse> alreadyCreated(UUID shareToken, UUID managerToken) {
        if (shareToken == null) {
            return Optional.empty();
        }
        Optional<VoteSession> existing = voteSessionRepository.findByShareToken(shareToken);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        VoteSession session = existing.get();
        if (managerToken == null || !managerToken.equals(session.getManagerToken())) {
            throw new ConflictException("This vote link is already in use");
        }
        long participantCount = voteActivityLikeRepository.countDistinctVoterTokensBySessionId(session.getId());
        return Optional.of(toResponse(session, participantCount, session.getManagerToken()));
    }

    private VoteSession newSession(UUID requestedShareToken, UUID requestedManagerToken, Destination destination,
                                   String initiatorEmail, String initiatorPhone, Integer numberOfTravelers,
                                   LocalDate startDate, LocalDate endDate, VoteMode voteMode, BigDecimal budget,
                                   String locale) {
        // A link token in use was answered by alreadyCreated; two creates racing for one token meet the
        // unique index on share_token, which GlobalExceptionHandler reports as a conflict.
        VoteSession session = new VoteSession();
        session.setShareToken(requestedShareToken != null ? requestedShareToken : UUID.randomUUID());
        session.setManagerToken(requestedManagerToken != null ? requestedManagerToken : UUID.randomUUID());
        session.setDestination(destination);
        String email = normalizeEmail(initiatorEmail);
        session.setInitiatorEmail(email);
        if (email != null) {
            session.setEmailCapturedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        session.setInitiatorPhone(initiatorPhone);
        session.setLocale(Translations.normalize(locale));
        session.setNumberOfTravelers(numberOfTravelers);
        session.setStartDate(startDate);
        session.setEndDate(endDate);
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setVoteMode(voteMode);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(24));
        session.setBudget(budget);
        VoteSession saved = voteSessionRepository.save(session);
        contactService.touch(saved.getInitiatorEmail(), ContactSource.VOTE, null, saved.getLocale());
        contactService.touchPhone(saved.getInitiatorPhone(), ContactSource.VOTE, saved.getLocale());
        return saved;
    }

    /** E.164 for a typed number, null when none was typed; a typed but unusable number is a 400. */
    private static String parsePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String phone = PhoneNumbers.normalize(raw);
        if (phone == null) {
            throw new BadRequestException("initiatorPhone must include the country code, like +447700900123");
        }
        return phone;
    }

    /**
     * Adds the organiser's other contact to a running vote, so a WhatsApp-first organiser who later
     * types an email (or the reverse) stays one vote, never two. A newly added email gets the
     * vote-created confirmation with the dashboard link; an address already on the vote is kept.
     */
    @Transactional
    public void updateContact(UUID shareToken, UUID managerToken, VoteSessionContactRequest request) {
        VoteSession session = requireManager(shareToken, managerToken);
        String email = normalizeEmail(request.getInitiatorEmail());
        String phone = parsePhone(request.getInitiatorPhone());
        if (email == null && phone == null) {
            throw new BadRequestException("Leave a WhatsApp number or an email");
        }
        boolean emailAdded = email != null && session.getInitiatorEmail() == null;
        if (emailAdded) {
            session.setInitiatorEmail(email);
            session.setEmailCapturedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        if (phone != null && session.getInitiatorPhone() == null) {
            session.setInitiatorPhone(phone);
        }
        voteSessionRepository.save(session);
        contactService.touch(email, ContactSource.VOTE, null, session.getLocale());
        contactService.touchPhone(phone, ContactSource.VOTE, session.getLocale());
        if (emailAdded) {
            sendVoteCreatedConfirmationQuietly(session);
        }
    }

    /** Trimmed address, or null for null/blank — a blank email must never read as "captured". */
    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
                               Map<UUID, Activity> activitiesById, Map<UUID, Integer> activityDays) {
        int sortOrder = 0;
        for (UUID activityId : activityIds) {
            Activity activity = activitiesById.get(activityId);
            VoteSessionActivity row = new VoteSessionActivity();
            row.setSession(session);
            row.setActivity(activity);
            row.setActivityName(activity.getName());
            row.setPrice(activity.getPrice());
            row.setSortOrder(sortOrder++);
            row.setDayNumber(activityDays == null ? null : activityDays.get(activityId));
            voteSessionActivityRepository.save(row);
        }
    }

    private void sendVoteCreatedConfirmationQuietly(VoteSession session) {
        if (session.getInitiatorEmail() == null || session.getInitiatorEmail().isBlank()) {
            return; // no organizer email on this session (API-created or legacy) — nothing to confirm to
        }
        try {
            emailService.sendVoteCreatedConfirmation(session, frontendUrl);
        } catch (Exception e) {
            // A failed confirmation email must never fail session creation — log and move on.
            log.error("Failed to send vote-created confirmation for session {}: {}",
                    session.getId(), e.getMessage(), e);
        }
    }

    // Read methods take the request locale (en/de/…) and resolve the translatable
    // content (destination/activity names, descriptions, quiz copy) for it; the
    // no-locale overloads serve English.

    public VoteSessionResponse getSession(UUID shareToken) {
        return getSession(shareToken, null);
    }

    public VoteSessionResponse getSession(UUID shareToken, String locale) {
        VoteSession session = findByShareToken(shareToken);
        long count = voteActivityLikeRepository.countDistinctVoterTokensBySessionId(session.getId());
        return toResponse(session, count, null, Translations.normalize(locale));
    }

    public List<VoteActivityResponse> getActivities(UUID shareToken) {
        return getActivities(shareToken, null);
    }

    public List<VoteActivityResponse> getActivities(UUID shareToken, String locale) {
        String lc = Translations.normalize(locale);
        VoteSession session = findByShareToken(shareToken);
        String destinationSlug = session.getDestination().getSlug();

        List<VoteSessionActivity> curated = voteSessionActivityRepository
                .findBySessionIdOrderBySortOrder(session.getId());
        if (!curated.isEmpty()) {
            // Activities the organiser dropped are no longer voted on.
            return curated.stream()
                    .filter(row -> row.getExcludedAt() == null)
                    .map(row -> toActivityResponse(row.getActivity(), destinationSlug, lc, row.getDayNumber()))
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
                .map(activity -> toActivityResponse(activity, destinationSlug, lc, null))
                .toList();
    }

    public PublicQuizDTO getParticipantQuiz(UUID shareToken) {
        return getParticipantQuiz(shareToken, null);
    }

    public PublicQuizDTO getParticipantQuiz(UUID shareToken, String locale) {
        VoteSession session = findByShareToken(shareToken);
        return quizService.getPublicQuiz(session.getDestination().getId(), locale);
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

        // One ballot per friend: after sending, the vote is final (no dashboard, no second go).
        if (voteActivityLikeRepository.existsBySessionIdAndVoterToken(session.getId(), request.getVoterToken())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already voted");
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
        saveRecommendations(session, request.getVoterToken(), request.getRecommendedActivityIds());
    }

    /** Saved in the ballot's transaction: a rejected recommendation rejects the whole vote, so it can be resent. */
    private void saveRecommendations(VoteSession session, UUID voterToken, List<UUID> recommendedActivityIds) {
        if (recommendedActivityIds == null || recommendedActivityIds.isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(recommendedActivityIds));
        Map<UUID, Activity> activitiesById = loadAndValidateDestinationActivities(session.getDestination(), ids);
        List<VoteRecommendation> rows = new ArrayList<>();
        for (UUID id : ids) {
            VoteRecommendation row = new VoteRecommendation();
            row.setSession(session);
            row.setVoterToken(voterToken);
            row.setActivity(activitiesById.get(id));
            rows.add(row);
        }
        voteRecommendationRepository.saveAll(rows);
    }

    public long getParticipantCount(UUID shareToken) {
        VoteSession session = findByShareToken(shareToken);
        return voteActivityLikeRepository.countDistinctVoterTokensBySessionId(session.getId());
    }

    public VoteResultResponse getResult(UUID shareToken) {
        return getResult(shareToken, null);
    }

    public VoteResultResponse getResult(UUID shareToken, String locale) {
        String lc = Translations.normalize(locale);
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
            // The curated row snapshots the English name at vote time; the live entity
            // carries the translations, so the localized name wins when present.
            Map<String, Map<String, String>> tr = activity.getTranslations();
            return new ResultActivityDTO(activityId,
                    Translations.pick(tr, lc, "name", curated.getActivityName()),
                    curated.getPrice(), activity.getMinPrice(), like, skip,
                    activity.getSlug(), destinationSlug, activity.getImageUrl(),
                    activity.getDuration(),
                    Translations.pick(tr, lc, "description", activity.getDescription()),
                    Translations.pick(tr, lc, "includes", activity.getIncludes()));
        }).toList();

        BigDecimal travelers = BigDecimal.valueOf(session.getNumberOfTravelers());
        BigDecimal totalPrice = result.stream()
                .map(r -> flooredLine(r.getPrice(), r.getMinPrice(), travelers))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal budget = session.getBudget();
        BigDecimal remaining = budget == null ? null : budget.subtract(totalPrice);

        List<SuggestionDTO> suggestions = session.getVoteMode() == VoteMode.CART
                ? List.of()
                : voteSuggestionsService.buildSuggestions(session, locale);

        long participantCount = voteActivityLikeRepository
                .countDistinctVoterTokensBySessionId(session.getId());

        Destination destination = session.getDestination();
        return new VoteResultResponse(result, suggestions, session.getNumberOfTravelers(),
                totalPrice, budget, remaining,
                Translations.pick(destination.getTranslations(), lc, "name", destination.getName()),
                destination.getSlug(),
                session.getStartDate(), session.getEndDate(),
                session.getVoteMode().name(), participantCount);
    }

    /**
     * Live tally for a CART vote session, for the organiser's dashboard only: friends never see the
     * standings (403), before or after they vote. QUIZ sessions have no live tally (409): their
     * winners depend on quiz-weighted scoring and budget trimming, not a simple like count.
     * {@code voterToken} is accepted and ignored, so older clients still get a clean 403.
     */
    public VoteTallyResponse getTally(UUID shareToken, UUID voterToken, UUID managerToken) {
        return getTally(shareToken, voterToken, managerToken, null);
    }

    public VoteTallyResponse getTally(UUID shareToken, UUID voterToken, UUID managerToken, String locale) {
        String lc = Translations.normalize(locale);
        VoteSession session = findByShareToken(shareToken);
        if (session.getVoteMode() != VoteMode.CART) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Live tally is not available for this session");
        }

        boolean isManager = managerToken != null && managerToken.equals(session.getManagerToken());
        if (!isManager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the organiser sees the live tally");
        }

        List<VoteSessionActivity> curated = voteSessionActivityRepository
                .findBySessionIdOrderBySortOrder(session.getId());
        Map<UUID, ActivityVoteCount> counts = voteActivityLikeRepository
                .findVoteCountsBySessionId(session.getId()).stream()
                .collect(Collectors.toMap(ActivityVoteCount::getActivityId, c -> c));

        List<VoteTallyResponse.TallyRow> rows = curated.stream()
                .sorted(VoteRanking.byLikes(counts))
                .map(row -> {
                    ActivityVoteCount c = counts.get(row.getActivity().getId());
                    return new VoteTallyResponse.TallyRow(
                            row.getActivity().getId(),
                            Translations.pick(row.getActivity().getTranslations(), lc, "name", row.getActivityName()),
                            row.getActivity().getImageUrl(),
                            row.getPrice(),
                            VoteRanking.likeCountOf(counts, row),
                            c == null ? 0 : c.getSkipCount(),
                            row.getExcludedAt() != null,
                            row.getDayNumber());
                })
                .toList();

        long participantCount = voteActivityLikeRepository
                .countDistinctVoterTokensBySessionId(session.getId());
        int travelers = session.getNumberOfTravelers() != null ? session.getNumberOfTravelers() : 0;

        return new VoteTallyResponse(session.getStatus().name(),
                session.getExpiresAt().toInstant(ZoneOffset.UTC), participantCount, travelers, rows,
                recommendationRows(session, curated, lc));
    }

    /** What friends recommended, minus what is already on the ballot (an excluded row counts as off it). */
    private List<VoteTallyResponse.RecommendationRow> recommendationRows(VoteSession session,
                                                                        List<VoteSessionActivity> curated,
                                                                        String lc) {
        Set<UUID> onBallot = curated.stream()
                .filter(row -> row.getExcludedAt() == null)
                .map(row -> row.getActivity().getId())
                .collect(Collectors.toSet());
        List<ActivityRecommendationCount> counts = voteRecommendationRepository.countBySessionId(session.getId())
                .stream()
                .filter(c -> !onBallot.contains(c.getActivityId()))
                .toList();
        Map<UUID, Activity> activities = activityRepository
                .findAllById(counts.stream().map(ActivityRecommendationCount::getActivityId).toList()).stream()
                .collect(Collectors.toMap(Activity::getId, a -> a));
        return counts.stream()
                .filter(c -> activities.containsKey(c.getActivityId()))
                .map(c -> {
                    Activity a = activities.get(c.getActivityId());
                    return new VoteTallyResponse.RecommendationRow(a.getId(),
                            Translations.pick(a.getTranslations(), lc, "name", a.getName()),
                            a.getSlug(), a.getImageUrl(), a.getPrice(), a.getMinPrice(), a.getDuration(),
                            c.getRecommendationCount());
                })
                .toList();
    }

    /** The organiser drops an activity from the running vote: friends who vote now no longer see it. */
    @Transactional
    public void excludeActivity(UUID shareToken, UUID managerToken, UUID activityId) {
        VoteSessionActivity row = requireActiveBallotRow(shareToken, managerToken, activityId);
        if (row.getExcludedAt() == null) {
            row.setExcludedAt(LocalDateTime.now(ZoneOffset.UTC));
            voteSessionActivityRepository.save(row);
        }
    }

    /** Undoes {@link #excludeActivity}: the activity is voted on again; earlier votes on it still count. */
    @Transactional
    public void restoreActivity(UUID shareToken, UUID managerToken, UUID activityId) {
        VoteSessionActivity row = requireActiveBallotRow(shareToken, managerToken, activityId);
        if (row.getExcludedAt() != null) {
            row.setExcludedAt(null);
            voteSessionActivityRepository.save(row);
        }
    }

    /**
     * The organiser adds an activity (usually one the group recommended) to the running vote, at the
     * end of the ballot. Adding one that was dropped restores it instead.
     */
    @Transactional
    public void addActivity(UUID shareToken, UUID managerToken, VoteSessionActivityAddRequest request) {
        VoteSession session = requireActiveManager(shareToken, managerToken);
        List<VoteSessionActivity> curated = voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId());
        for (VoteSessionActivity row : curated) {
            if (row.getActivity().getId().equals(request.getActivityId())) {
                if (row.getExcludedAt() != null) {
                    row.setExcludedAt(null);
                    voteSessionActivityRepository.save(row);
                }
                return;
            }
        }
        Map<UUID, Activity> byId = loadAndValidateDestinationActivities(session.getDestination(),
                List.of(request.getActivityId()));
        Activity activity = byId.get(request.getActivityId());
        VoteSessionActivity row = new VoteSessionActivity();
        row.setSession(session);
        row.setActivity(activity);
        row.setActivityName(activity.getName());
        row.setPrice(activity.getPrice());
        row.setSortOrder(curated.stream().mapToInt(VoteSessionActivity::getSortOrder).max().orElse(-1) + 1);
        voteSessionActivityRepository.save(row);
    }

    private VoteSession requireActiveManager(UUID shareToken, UUID managerToken) {
        VoteSession session = requireManager(shareToken, managerToken);
        if (session.getStatus() != VoteSessionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voting has closed");
        }
        return session;
    }

    private VoteSessionActivity requireActiveBallotRow(UUID shareToken, UUID managerToken, UUID activityId) {
        VoteSession session = requireActiveManager(shareToken, managerToken);
        return voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId()).stream()
                .filter(row -> row.getActivity().getId().equals(activityId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Activity is not part of this vote"));
    }

    public VoteSession requireManager(UUID shareToken, UUID managerToken) {
        VoteSession session = findByShareToken(shareToken);
        if (managerToken == null || !session.getManagerToken().equals(managerToken)) {
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

        List<VoteSessionActivity> allCurated =
                voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId());
        // What the organiser dropped is out of the result, whatever its votes.
        List<VoteSessionActivity> curated = allCurated.stream()
                .filter(row -> row.getExcludedAt() == null)
                .toList();
        if (allCurated.isEmpty()) {
            // Legacy session pre-Plan-2: no curated list to resolve against.
            session.setStatus(VoteSessionStatus.COMPLETED);
            voteSessionRepository.save(session);
            return;
        }

        Map<UUID, ActivityVoteCount> counts =
                voteActivityLikeRepository.findVoteCountsBySessionId(session.getId()).stream()
                        .collect(Collectors.toMap(ActivityVoteCount::getActivityId, c -> c));

        if (session.getVoteMode() == VoteMode.CART) {
            freezeCartRanking(session, curated, counts);
        } else {
            freezeQuizWinners(session, curated, counts);
        }

        session.setStatus(VoteSessionStatus.COMPLETED);
        voteSessionRepository.save(session);

        // The ranked results are frozen above — a failed notification must never roll back
        // COMPLETED, or the scheduler would re-process the same failing session every tick.
        try {
            if (session.getInitiatorEmail() != null && !session.getInitiatorEmail().isBlank()) {
                List<VoteSessionResultActivity> results =
                        resultActivityRepository.findBySessionIdOrderBySortOrder(session.getId());
                emailService.sendVoteResult(session, results, frontendUrl);
            }
        } catch (Exception e) {
            log.error("Failed to send vote result email for session {}: {}",
                    session.getId(), e.getMessage(), e);
        }

        try {
            List<UUID> rankedActivityIds = resultActivityRepository
                    .findBySessionIdOrderBySortOrder(session.getId()).stream()
                    .map(row -> row.getActivity().getId())
                    .toList();
            tripLeadService.createFromVoteSession(session.getId(), rankedActivityIds);
        } catch (Exception e) {
            // A failed lead capture must never fail vote completion — log and move on.
            log.error("Failed to create trip lead for session {}: {}", session.getId(), e.getMessage(), e);
        }
    }

    private void freezeCartRanking(VoteSession session, List<VoteSessionActivity> curated,
                                   Map<UUID, ActivityVoteCount> counts) {
        // Advisory ranking: every ballot activity is kept, ordered by like count;
        // ties resolve to the initiator's original cart order.
        List<VoteSessionActivity> ranked = curated.stream()
                .sorted(VoteRanking.byLikes(counts))
                .toList();
        int sortOrder = 0;
        for (VoteSessionActivity row : ranked) {
            saveResultRow(session, row.getActivity(), sortOrder++);
        }
        log.info("Processed cart vote session {} — {} activities ranked", session.getId(), sortOrder);
    }

    private void freezeQuizWinners(VoteSession session, List<VoteSessionActivity> curated,
                                   Map<UUID, ActivityVoteCount> counts) {
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
            saveResultRow(session, r.row().getActivity(), sortOrder++);
            running = running.add(groupCost);
        }
        log.info("Processed vote session {} — {} activities selected", session.getId(), sortOrder);
    }

    private void saveResultRow(VoteSession session, Activity activity, int sortOrder) {
        VoteSessionResultActivity resultRow = new VoteSessionResultActivity();
        resultRow.setSession(session);
        resultRow.setActivity(activity);
        resultRow.setSortOrder(sortOrder);
        resultActivityRepository.save(resultRow);
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
        return toResponse(session, participantCount, managerToken, null);
    }

    private VoteSessionResponse toResponse(VoteSession session, long participantCount, UUID managerToken, String lc) {
        int travelers = session.getNumberOfTravelers() != null ? session.getNumberOfTravelers() : 0;
        Instant expiresAt = session.getExpiresAt().toInstant(ZoneOffset.UTC);
        Destination destination = session.getDestination();
        return new VoteSessionResponse(
                session.getShareToken(),
                Translations.pick(destination.getTranslations(), lc, "name", destination.getName()),
                session.getDestination().getSlug(),
                session.getStatus().name(),
                expiresAt,
                participantCount,
                travelers,
                managerToken,
                session.getVoteMode().name(),
                session.getStartDate(),
                session.getEndDate());
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

    /** Group-minimum floor for the result estimate: mirrors BookingService.lineTotal. */
    private static BigDecimal flooredLine(BigDecimal price, BigDecimal minPrice, BigDecimal travelers) {
        BigDecimal line = price.multiply(travelers);
        if (minPrice != null && line.compareTo(minPrice) < 0) {
            return minPrice;
        }
        return line;
    }

    private VoteActivityResponse toActivityResponse(Activity activity, String destinationSlug, String lc,
                                                    Integer dayNumber) {
        return new VoteActivityResponse(
                activity.getId(),
                Translations.pick(activity.getTranslations(), lc, "name", activity.getName()),
                Translations.pick(activity.getTranslations(), lc, "description", activity.getDescription()),
                activity.getPrice(),
                activity.getMinPrice(),
                activity.getDuration(),
                activity.getImageUrl(),
                activity.getSlug(),
                destinationSlug,
                dayNumber);
    }
}
