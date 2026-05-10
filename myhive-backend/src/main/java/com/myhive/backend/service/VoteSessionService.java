package com.myhive.backend.service;

import com.myhive.backend.dto.VoteActivityResponse;
import com.myhive.backend.dto.VoteBatchRequest;
import com.myhive.backend.dto.VoteRequest;
import com.myhive.backend.dto.VoteResultResponse;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteActivityLike;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.exception.ResultNotReadyException;
import com.myhive.backend.exception.SessionFullException;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityLikeCount;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.repository.VoteSessionResultActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

    private static final int ACTIVITY_BUDGET_MINUTES_PER_DAY = 480;

    private final VoteSessionRepository voteSessionRepository;
    private final VoteActivityLikeRepository voteActivityLikeRepository;
    private final VoteSessionResultActivityRepository resultActivityRepository;
    private final DestinationRepository destinationRepository;
    private final CategoryRepository categoryRepository;
    private final ActivityRepository activityRepository;
    private final EmailService emailService;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.site.url:https://trivlu.com}")
    private String siteUrl;

    @Transactional
    public VoteSessionResponse createSession(VoteSessionCreateRequest request) {
        Destination destination = destinationRepository.findById(request.getDestinationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found"));

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }

        Set<UUID> destinationCategoryIds = resolveDestinationCategoryIds(destination);

        boolean allValid = request.getLikedCategoryIds().stream()
                .allMatch(destinationCategoryIds::contains);
        if (!allValid) {
            throw new BadRequestException("Some categories do not belong to this destination");
        }

        Set<Category> likedCategories = request.getLikedCategoryIds().stream()
                .map(id -> categoryRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id)))
                .collect(Collectors.toSet());

        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail(request.getInitiatorEmail());
        session.setNumberOfTravelers(request.getNumberOfTravelers());
        session.setStartDate(request.getStartDate());
        session.setEndDate(request.getEndDate());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(24));
        session.setLikedCategories(likedCategories);

        session = voteSessionRepository.save(session);
        long participantCount = voteActivityLikeRepository
                .countDistinctVoterTokensBySessionId(session.getId());
        return toResponse(session, participantCount, session.getManagerToken());
    }

    public VoteSessionResponse getSession(UUID shareToken) {
        VoteSession session = findByShareToken(shareToken);
        long count = voteActivityLikeRepository.countDistinctVoterTokensBySessionId(session.getId());
        return toResponse(session, count);
    }

    public List<VoteActivityResponse> getActivities(UUID shareToken) {
        VoteSession session = findByShareToken(shareToken);
        Set<UUID> categoryIds = session.getLikedCategories().stream()
                .map(Category::getId)
                .collect(Collectors.toSet());

        List<Activity> activities = activityRepository.findByDestinationIdAndCategoriesIdIn(
                session.getDestination().getId(), categoryIds);
        return activities.stream().map(this::toActivityResponse).toList();
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

        assertVoterAllowed(session, request.getVoterToken());

        Set<UUID> activityIds = request.getVotes().stream()
                .map(VoteBatchRequest.VoteItem::getActivityId)
                .collect(Collectors.toSet());
        Map<UUID, Activity> activitiesById = activityRepository.findAllById(activityIds).stream()
                .collect(Collectors.toMap(Activity::getId, a -> a));

        List<VoteActivityLike> likes = new ArrayList<>();
        for (VoteBatchRequest.VoteItem item : request.getVotes()) {
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

        List<VoteSessionResultActivity> results = resultActivityRepository
                .findBySessionIdOrderBySortOrder(session.getId());

        List<VoteActivityResponse> activities = results.stream()
                .map(r -> toActivityResponse(r.getActivity()))
                .toList();

        BigDecimal totalPrice = activities.stream()
                .map(VoteActivityResponse::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(session.getNumberOfTravelers()));

        return new VoteResultResponse(
                session.getDestination().getName(),
                session.getDestination().getSlug(),
                activities,
                totalPrice,
                session.getNumberOfTravelers(),
                session.getStartDate(),
                session.getEndDate());
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

        long tripDays = ChronoUnit.DAYS.between(session.getStartDate(), session.getEndDate()) + 1;
        int budgetMinutes = (int) (tripDays * ACTIVITY_BUDGET_MINUTES_PER_DAY);

        List<ActivityLikeCount> likedRows =
                voteActivityLikeRepository.findLikedActivitiesWithCounts(session.getId());

        int remaining = budgetMinutes;
        int sortOrder = 0;

        for (ActivityLikeCount row : likedRows) {
            Integer duration = row.getDuration();
            if (duration == null || duration > remaining) {
                continue;
            }
            Optional<Activity> activityOpt = activityRepository.findById(row.getActivityId());
            if (activityOpt.isEmpty()) {
                continue;
            }
            VoteSessionResultActivity result = new VoteSessionResultActivity();
            result.setSession(session);
            result.setActivity(activityOpt.get());
            result.setSortOrder(sortOrder++);
            resultActivityRepository.save(result);
            remaining -= duration;
        }

        session.setStatus(VoteSessionStatus.COMPLETED);
        voteSessionRepository.save(session);
        log.info("Processed vote session {} — {} activities selected", session.getId(), sortOrder);

        if (emailEnabled) {
            List<VoteSessionResultActivity> results =
                    resultActivityRepository.findBySessionIdOrderBySortOrder(session.getId());
            emailService.sendVoteResult(session, results, siteUrl);
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
                managerToken);
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

    private VoteActivityResponse toActivityResponse(Activity activity) {
        return new VoteActivityResponse(
                activity.getId(),
                activity.getName(),
                activity.getDescription(),
                activity.getPrice(),
                activity.getDuration(),
                activity.getImageUrl(),
                activity.getSlug());
    }
}
