package com.myhive.backend.repository;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.EmailSuppression;
import com.myhive.backend.entity.TripLead;
import com.myhive.backend.entity.TripLeadActivity;
import com.myhive.backend.model.TripLeadSource;
import com.myhive.backend.model.TripLeadStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class TripLeadTablesTest {

    @Autowired private TripLeadRepository tripLeadRepository;
    @Autowired private TripLeadActivityRepository tripLeadActivityRepository;
    @Autowired private EmailSuppressionRepository emailSuppressionRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    private TripLead newLead(String email) {
        TripLead lead = new TripLead();
        lead.setEmail(email);
        lead.setSource(TripLeadSource.QUIZ);
        lead.setRestoreToken(UUID.randomUUID());
        lead.setUnsubscribeToken(UUID.randomUUID());
        lead.setStatus(TripLeadStatus.ACTIVE);
        lead.setReminderStage(0);
        lead.setLastActivityAt(LocalDateTime.now(ZoneOffset.UTC));
        return tripLeadRepository.saveAndFlush(lead);
    }

    @Test
    void tripLeadActivity_persistsSnapshotFields() {
        String expectedName = "Karting (snapshot)";
        BigDecimal expectedPrice = new BigDecimal("49.99");
        BigDecimal expectedMinPrice = new BigDecimal("200.00");

        Destination destination = destinationRepository.saveAndFlush(
                com.myhive.backend.TestDataFactory.destination("Prague"));
        Activity activity = activityRepository.saveAndFlush(
                com.myhive.backend.TestDataFactory.activity(destination, "Karting", new BigDecimal("50.00")));
        TripLead lead = newLead("lead@test.com");

        TripLeadActivity row = new TripLeadActivity();
        row.setLead(lead);
        row.setActivity(activity);
        row.setActivityName(expectedName);
        row.setPrice(expectedPrice);
        row.setMinPrice(expectedMinPrice);
        row.setSortOrder(0);
        tripLeadActivityRepository.saveAndFlush(row);

        List<TripLeadActivity> found = tripLeadActivityRepository.findByLeadIdOrderBySortOrder(lead.getId());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getActivityName()).isEqualTo(expectedName);
        assertThat(found.get(0).getPrice()).isEqualByComparingTo(expectedPrice);
        assertThat(found.get(0).getMinPrice()).isEqualByComparingTo(expectedMinPrice);
    }

    @Test
    void findFirstByEmailAndStatus_findsOnlyActiveLead() {
        TripLead active = newLead("dup@test.com");
        TripLead converted = newLead("dup2@test.com");
        converted.setEmail("dup@test.com");
        converted.setStatus(TripLeadStatus.CONVERTED);
        tripLeadRepository.saveAndFlush(converted);

        assertThat(tripLeadRepository.findFirstByEmailAndStatus("dup@test.com", TripLeadStatus.ACTIVE))
                .hasValueSatisfying(l -> assertThat(l.getId()).isEqualTo(active.getId()));
    }

    @Test
    void deleteByUpdatedAtBefore_removesOldLeads() {
        newLead("old@test.com");
        tripLeadRepository.flush();

        int deletedFuture = tripLeadRepository.deleteByUpdatedAtBefore(
                LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        int deletedPast = tripLeadRepository.deleteByUpdatedAtBefore(
                LocalDateTime.now(ZoneOffset.UTC).minusDays(30));

        assertThat(deletedFuture).isEqualTo(1);
        assertThat(deletedPast).isZero();
    }

    @Test
    void deletingLeadCascadesItsActivityRows() {
        Destination destination = destinationRepository.saveAndFlush(
                com.myhive.backend.TestDataFactory.destination("Prague"));
        Activity activity = activityRepository.saveAndFlush(
                com.myhive.backend.TestDataFactory.activity(destination, "Karting", new BigDecimal("50.00")));
        TripLead lead = newLead("cascade@test.com");

        TripLeadActivity row = new TripLeadActivity();
        row.setLead(lead);
        row.setActivity(activity);
        row.setActivityName("Karting");
        row.setPrice(new BigDecimal("50.00"));
        row.setSortOrder(0);
        tripLeadActivityRepository.saveAndFlush(row);

        UUID leadId = lead.getId();
        tripLeadRepository.deleteById(leadId);
        tripLeadRepository.flush();

        assertThat(tripLeadActivityRepository.findByLeadIdOrderBySortOrder(leadId)).isEmpty();
    }

    @Test
    void emailSuppression_existsByEmail() {
        EmailSuppression suppression = new EmailSuppression();
        suppression.setEmail("gone@test.com");
        emailSuppressionRepository.saveAndFlush(suppression);

        assertThat(emailSuppressionRepository.existsByEmail("gone@test.com")).isTrue();
        assertThat(emailSuppressionRepository.existsByEmail("here@test.com")).isFalse();
    }
}
