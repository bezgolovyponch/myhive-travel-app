package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.TripLeadCreateRequest;
import com.myhive.backend.dto.TripLeadCreateResponse;
import com.myhive.backend.dto.TripLeadRestoreResponse;
import com.myhive.backend.dto.TripLeadSyncRequest;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.TripLead;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.model.TripLeadStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.EmailSuppressionRepository;
import com.myhive.backend.repository.TripLeadActivityRepository;
import com.myhive.backend.repository.TripLeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class TripLeadServiceTest {

    @Autowired private TripLeadService tripLeadService;
    @Autowired private TripLeadRepository tripLeadRepository;
    @Autowired private TripLeadActivityRepository tripLeadActivityRepository;
    @Autowired private EmailSuppressionRepository emailSuppressionRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    private Destination destination;
    private Activity karting;

    @BeforeEach
    void setUp() {
        destination = destinationRepository.saveAndFlush(TestDataFactory.destination("Prague"));
        karting = activityRepository.saveAndFlush(
                TestDataFactory.activity(destination, "Karting", new BigDecimal("50.00")));
    }

    private TripLeadCreateRequest createRequest(String email) {
        TripLeadCreateRequest request = new TripLeadCreateRequest();
        request.setEmail(email);
        request.setDestinationId(destination.getId());
        request.setNumberOfTravelers(6);
        request.setStartDate(LocalDate.now().plusDays(30));
        request.setEndDate(LocalDate.now().plusDays(32));
        return request;
    }

    @Test
    void create_normalizesEmailAndIssuesTokens() {
        TripLeadCreateResponse response = tripLeadService.create(createRequest("  Stag.Lead@Example.COM "));

        TripLead saved = tripLeadRepository.findById(response.id()).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("stag.lead@example.com");
        assertThat(saved.getRestoreToken()).isEqualTo(response.restoreToken());
        assertThat(saved.getUnsubscribeToken()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(TripLeadStatus.ACTIVE);
        assertThat(saved.getReminderStage()).isZero();
    }

    @Test
    void create_supersedesExistingActiveLeadForSameEmail() {
        TripLeadCreateResponse first = tripLeadService.create(createRequest("dup@example.com"));
        TripLeadCreateResponse second = tripLeadService.create(createRequest("DUP@example.com"));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.restoreToken()).isNotEqualTo(first.restoreToken());
        List<TripLead> activeLeads =
                tripLeadRepository.findAllByEmailAndStatus("dup@example.com", TripLeadStatus.ACTIVE);
        assertThat(activeLeads).hasSize(1);
        assertThat(activeLeads.get(0).getId()).isEqualTo(second.id());
        assertThat(tripLeadRepository.findById(first.id()).orElseThrow().getStatus())
                .isEqualTo(TripLeadStatus.COMPLETED);
    }

    @Test
    void sync_snapshotsItemsFromCatalogAndDropsUnknownIds() {
        TripLeadCreateResponse lead = tripLeadService.create(createRequest("sync@example.com"));

        TripLeadSyncRequest.SyncItem known = new TripLeadSyncRequest.SyncItem();
        known.setActivityId(karting.getId());
        known.setSortOrder(0);
        TripLeadSyncRequest.SyncItem unknown = new TripLeadSyncRequest.SyncItem();
        unknown.setActivityId(UUID.randomUUID());
        unknown.setSortOrder(1);
        TripLeadSyncRequest request = new TripLeadSyncRequest();
        request.setRestoreToken(lead.restoreToken());
        request.setItems(List.of(known, unknown));

        tripLeadService.sync(lead.id(), request);

        var rows = tripLeadActivityRepository.findByLeadIdOrderBySortOrder(lead.id());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getActivityName()).isEqualTo("Karting");
        assertThat(rows.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void sync_dedupesDuplicateActivityIds() {
        TripLeadCreateResponse lead = tripLeadService.create(createRequest("dupitems@example.com"));

        TripLeadSyncRequest.SyncItem first = new TripLeadSyncRequest.SyncItem();
        first.setActivityId(karting.getId());
        first.setSortOrder(0);
        TripLeadSyncRequest.SyncItem duplicate = new TripLeadSyncRequest.SyncItem();
        duplicate.setActivityId(karting.getId());
        duplicate.setSortOrder(1);
        TripLeadSyncRequest request = new TripLeadSyncRequest();
        request.setRestoreToken(lead.restoreToken());
        request.setItems(List.of(first, duplicate));

        tripLeadService.sync(lead.id(), request);

        assertThat(tripLeadActivityRepository.findByLeadIdOrderBySortOrder(lead.id())).hasSize(1);
    }

    @Test
    void sync_rejectsWrongToken() {
        TripLeadCreateResponse lead = tripLeadService.create(createRequest("token@example.com"));
        TripLeadSyncRequest request = new TripLeadSyncRequest();
        request.setRestoreToken(UUID.randomUUID());

        assertThatThrownBy(() -> tripLeadService.sync(lead.id(), request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void restore_returnsSnapshotWithLiveCatalogData() {
        TripLeadCreateResponse lead = tripLeadService.create(createRequest("restore@example.com"));
        TripLeadSyncRequest.SyncItem item = new TripLeadSyncRequest.SyncItem();
        item.setActivityId(karting.getId());
        item.setSortOrder(0);
        TripLeadSyncRequest sync = new TripLeadSyncRequest();
        sync.setRestoreToken(lead.restoreToken());
        sync.setQuizResponsesJson("[{\"questionId\":\"q\",\"answerId\":\"a\"}]");
        sync.setItems(List.of(item));
        tripLeadService.sync(lead.id(), sync);

        TripLeadRestoreResponse response = tripLeadService.restore(lead.restoreToken());

        assertThat(response.leadId()).isEqualTo(lead.id());
        assertThat(response.email()).isEqualTo("restore@example.com");
        assertThat(response.destinationSlug()).isEqualTo(destination.getSlug());
        assertThat(response.numberOfTravelers()).isEqualTo(6);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).activityId()).isEqualTo(karting.getId());
        assertThat(response.items().get(0).name()).isEqualTo("Karting");
    }

    @Test
    void restore_unknownTokenThrows404() {
        assertThatThrownBy(() -> tripLeadService.restore(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unsubscribe_suppressesEmailAndMarksActiveLeads() {
        TripLeadCreateResponse lead = tripLeadService.create(createRequest("bye@example.com"));
        UUID unsubscribeToken = tripLeadRepository.findById(lead.id()).orElseThrow().getUnsubscribeToken();

        tripLeadService.unsubscribe(unsubscribeToken);

        assertThat(emailSuppressionRepository.existsByEmail("bye@example.com")).isTrue();
        assertThat(tripLeadRepository.findById(lead.id()).orElseThrow().getStatus())
                .isEqualTo(TripLeadStatus.UNSUBSCRIBED);
    }

    @Test
    void unsubscribe_unknownTokenIsSilentlyIgnored() {
        tripLeadService.unsubscribe(UUID.randomUUID());
        // No exception — idempotent, token validity is never leaked.
    }
}
