package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.CreateBookingRequest;
import com.myhive.backend.dto.TripExportRequest;
import com.myhive.backend.dto.TripLeadCreateRequest;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Contact;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.model.ContactSource;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.ContactRepository;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Not @Transactional: ContactService.touch commits in a REQUIRES_NEW transaction. Unique emails
// per test; contacts are deleted afterwards, catalog rows are left (as other non-transactional
// tests in this package do).
@SpringBootTest
@Import(TestSecurityConfig.class)
class ContactCaptureIntegrationTest {

    @Autowired private TripLeadService tripLeadService;
    @Autowired private VoteSessionService voteSessionService;
    @Autowired private BookingService bookingService;
    @Autowired private ContactRepository contactRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    private final List<String> createdEmails = new ArrayList<>();
    private Destination destination;
    private Activity activity;

    @BeforeEach
    void setUp() {
        destination = destinationRepository.saveAndFlush(TestDataFactory.destination("Prague"));
        activity = activityRepository.saveAndFlush(
                TestDataFactory.activity(destination, "Karting", new BigDecimal("50.00")));
    }

    @AfterEach
    void cleanUp() {
        for (String email : createdEmails) {
            contactRepository.findByEmail(email).ifPresent(contactRepository::delete);
        }
    }

    private String uniqueEmail() {
        String email = "c-" + UUID.randomUUID() + "@example.com";
        createdEmails.add(email);
        return email;
    }

    @Test
    void tripLeadCreate_recordsTripBuilderContact() {
        String expectedEmail = uniqueEmail();
        TripLeadCreateRequest request = new TripLeadCreateRequest();
        request.setEmail(expectedEmail);
        request.setDestinationId(destination.getId());
        request.setLocale("de");

        tripLeadService.create(request);

        Contact contact = contactRepository.findByEmail(expectedEmail).orElseThrow();
        assertThat(contact.getFirstSource()).isEqualTo(ContactSource.TRIP_BUILDER);
        assertThat(contact.getLocale()).isEqualTo("de");
    }

    @Test
    void voteSessionCreate_recordsVoteContact() {
        String expectedEmail = uniqueEmail();
        VoteSessionCreateRequest request = new VoteSessionCreateRequest();
        request.setDestinationId(destination.getId());
        request.setInitiatorEmail(expectedEmail);
        request.setNumberOfTravelers(2);
        request.setStartDate(LocalDate.of(2026, 10, 1));
        request.setEndDate(LocalDate.of(2026, 10, 3));
        request.setVoterToken(UUID.randomUUID());
        request.setQuizResponses(List.of());
        request.setActivityIds(List.of(activity.getId()));

        voteSessionService.createSession(request);

        Contact contact = contactRepository.findByEmail(expectedEmail).orElseThrow();
        assertThat(contact.getFirstSource()).isEqualTo(ContactSource.VOTE);
    }

    @Test
    void voteSessionCreate_withoutEmail_recordsNothing() {
        long before = contactRepository.count();
        VoteSessionCreateRequest request = new VoteSessionCreateRequest();
        request.setDestinationId(destination.getId());
        request.setInitiatorEmail(null);
        request.setNumberOfTravelers(2);
        request.setStartDate(LocalDate.of(2026, 10, 1));
        request.setEndDate(LocalDate.of(2026, 10, 3));
        request.setVoterToken(UUID.randomUUID());
        request.setQuizResponses(List.of());
        request.setActivityIds(List.of(activity.getId()));

        voteSessionService.createSession(request);

        assertThat(contactRepository.count()).isEqualTo(before);
    }

    @Test
    void createBooking_recordsBookingContact() {
        String expectedEmail = uniqueEmail();
        CreateBookingRequest request = TestDataFactory.createBookingRequest(activity.getId());
        request.setUserEmail(expectedEmail);

        bookingService.createBooking(request);

        Contact contact = contactRepository.findByEmail(expectedEmail).orElseThrow();
        assertThat(contact.getFirstSource()).isEqualTo(ContactSource.BOOKING);
    }

    @Test
    void createBookingEntity_recordsBookingContactWithCustomerName() {
        String expectedEmail = uniqueEmail();
        String expectedName = "Anna Example";
        TripExportRequest request = TestDataFactory.tripExportRequest();
        request.setUserEmail(expectedEmail);
        request.setCustomerName(expectedName);
        // TestDataFactory.tripExportRequest() stamps a random, never-persisted activityId — fine
        // for the Mockito tests in BookingServiceTest (which stub activityRepository.findById to
        // return an activity regardless of id), but this is a real repository against H2, so it
        // must point at an activity that actually exists.
        request.getDestinations().getFirst().getActivities().getFirst().setActivityId(activity.getId());

        bookingService.createBookingEntity(request, false);

        Contact contact = contactRepository.findByEmail(expectedEmail).orElseThrow();
        assertThat(contact.getFirstSource()).isEqualTo(ContactSource.BOOKING);
        assertThat(contact.getName()).isEqualTo(expectedName);
    }
}
