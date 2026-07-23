package com.myhive.backend.service;

import com.myhive.backend.dto.TripLeadCreateRequest;
import com.myhive.backend.dto.TripLeadCreateResponse;
import com.myhive.backend.dto.TripLeadRestoreResponse;
import com.myhive.backend.dto.TripLeadSyncRequest;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.EmailSuppression;
import com.myhive.backend.entity.TripLead;
import com.myhive.backend.entity.TripLeadActivity;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.model.TripLeadSource;
import com.myhive.backend.model.TripLeadStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.EmailSuppressionRepository;
import com.myhive.backend.repository.TripLeadActivityRepository;
import com.myhive.backend.repository.TripLeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TripLeadService {

    private final TripLeadRepository tripLeadRepository;
    private final TripLeadActivityRepository tripLeadActivityRepository;
    private final EmailSuppressionRepository emailSuppressionRepository;
    private final DestinationRepository destinationRepository;
    private final ActivityRepository activityRepository;

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public TripLeadCreateResponse create(TripLeadCreateRequest request) {
        String email = normalizeEmail(request.getEmail());
        TripLead lead = tripLeadRepository.findFirstByEmailAndStatus(email, TripLeadStatus.ACTIVE)
                .orElseGet(() -> newLead(email));
        applySetup(lead, request.getDestinationId(), request.getNumberOfTravelers(),
                request.getStartDate(), request.getEndDate(), request.getBudget());
        lead.setLastActivityAt(LocalDateTime.now(ZoneOffset.UTC));
        lead = tripLeadRepository.save(lead);
        return new TripLeadCreateResponse(lead.getId(), lead.getRestoreToken());
    }

    TripLead newLead(String email) {
        TripLead lead = new TripLead();
        lead.setEmail(email);
        lead.setSource(TripLeadSource.QUIZ);
        lead.setRestoreToken(UUID.randomUUID());
        lead.setUnsubscribeToken(UUID.randomUUID());
        lead.setStatus(TripLeadStatus.ACTIVE);
        lead.setReminderStage(0);
        return lead;
    }

    private void applySetup(TripLead lead, UUID destinationId, Integer numberOfTravelers,
                            LocalDate startDate, LocalDate endDate, BigDecimal budget) {
        if (destinationId != null) {
            destinationRepository.findById(destinationId).ifPresent(lead::setDestination);
        }
        if (numberOfTravelers != null) {
            lead.setNumberOfTravelers(numberOfTravelers);
        }
        if (startDate != null) {
            lead.setStartDate(startDate);
        }
        if (endDate != null) {
            lead.setEndDate(endDate);
        }
        if (budget != null) {
            lead.setBudget(budget);
        }
    }

    @Transactional
    public void sync(UUID leadId, TripLeadSyncRequest request) {
        // Same 404 for a missing lead and a token mismatch — existence is never leaked.
        TripLead lead = tripLeadRepository.findById(leadId)
                .filter(l -> l.getRestoreToken().equals(request.getRestoreToken()))
                .orElseThrow(() -> new ResourceNotFoundException("Trip lead not found"));
        if (lead.getStatus() != TripLeadStatus.ACTIVE) {
            return; // converted/finished leads accept no further sync — not an error for the client
        }
        applySetup(lead, null, request.getNumberOfTravelers(), request.getStartDate(),
                request.getEndDate(), request.getBudget());
        if (request.getQuizResponsesJson() != null) {
            lead.setQuizResponsesJson(request.getQuizResponsesJson());
        }
        if (request.getItems() != null) {
            replaceItemsFromCatalog(lead, request.getItems());
        }
        lead.setLastActivityAt(LocalDateTime.now(ZoneOffset.UTC));
        tripLeadRepository.save(lead);
    }

    /** Snapshots name/price/minPrice from the catalog — client-sent prices are never trusted. */
    private void replaceItemsFromCatalog(TripLead lead, List<TripLeadSyncRequest.SyncItem> items) {
        tripLeadActivityRepository.deleteByLeadId(lead.getId());
        List<TripLeadSyncRequest.SyncItem> ordered = items.stream()
                .sorted(Comparator.comparingInt(TripLeadSyncRequest.SyncItem::getSortOrder))
                .toList();
        Map<UUID, Activity> activitiesById = activityRepository
                .findAllById(ordered.stream().map(TripLeadSyncRequest.SyncItem::getActivityId).toList())
                .stream()
                .collect(Collectors.toMap(Activity::getId, a -> a));
        int sortOrder = 0;
        for (TripLeadSyncRequest.SyncItem item : ordered) {
            Activity activity = activitiesById.get(item.getActivityId());
            if (activity == null) {
                continue; // stale cart entry — the activity is no longer in the catalog
            }
            saveItemSnapshot(lead, activity, sortOrder++);
        }
    }

    void saveItemSnapshot(TripLead lead, Activity activity, int sortOrder) {
        TripLeadActivity row = new TripLeadActivity();
        row.setLead(lead);
        row.setActivity(activity);
        row.setActivityName(activity.getName());
        row.setPrice(activity.getPrice());
        row.setMinPrice(activity.getMinPrice());
        row.setSortOrder(sortOrder);
        tripLeadActivityRepository.save(row);
    }

    public TripLeadRestoreResponse restore(UUID restoreToken) {
        TripLead lead = tripLeadRepository.findByRestoreToken(restoreToken)
                .orElseThrow(() -> new ResourceNotFoundException("Trip lead not found"));
        List<TripLeadRestoreResponse.RestoreItem> items = tripLeadActivityRepository
                .findByLeadIdOrderBySortOrder(lead.getId()).stream()
                .map(TripLeadService::toRestoreItem)
                .toList();
        Destination destination = lead.getDestination();
        return new TripLeadRestoreResponse(
                lead.getId(),
                lead.getEmail(),
                destination == null ? null : destination.getId(),
                destination == null ? null : destination.getSlug(),
                destination == null ? null : destination.getName(),
                lead.getNumberOfTravelers(),
                lead.getStartDate(),
                lead.getEndDate(),
                lead.getBudget(),
                lead.getQuizResponsesJson(),
                items);
    }

    /** Restore serves live catalog data (current names/prices) — the snapshot only preserves order/membership. */
    private static TripLeadRestoreResponse.RestoreItem toRestoreItem(TripLeadActivity row) {
        Activity activity = row.getActivity();
        String destinationSlug = activity.getDestination() == null
                ? null : activity.getDestination().getSlug();
        return new TripLeadRestoreResponse.RestoreItem(
                activity.getId(), activity.getName(), activity.getPrice(), activity.getMinPrice(),
                activity.getImageUrl(), activity.getDuration(), activity.getSlug(), destinationSlug,
                activity.getDescription(), activity.getIncludes());
    }

    @Transactional
    public void unsubscribe(UUID token) {
        tripLeadRepository.findByUnsubscribeToken(token).ifPresent(lead -> {
            suppress(lead.getEmail());
            for (TripLead active : tripLeadRepository
                    .findAllByEmailAndStatus(lead.getEmail(), TripLeadStatus.ACTIVE)) {
                active.setStatus(TripLeadStatus.UNSUBSCRIBED);
                tripLeadRepository.save(active);
            }
        });
    }

    private void suppress(String email) {
        if (!emailSuppressionRepository.existsByEmail(email)) {
            EmailSuppression suppression = new EmailSuppression();
            suppression.setEmail(email);
            emailSuppressionRepository.save(suppression);
        }
    }
}
