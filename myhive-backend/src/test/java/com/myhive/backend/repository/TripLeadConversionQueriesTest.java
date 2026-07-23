package com.myhive.backend.repository;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.model.VoteMode;
import com.myhive.backend.model.VoteSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class TripLeadConversionQueriesTest {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private DestinationRepository destinationRepository;

    @Test
    void existsByUserEmailIgnoreCaseAndCreatedAtAfter_matchesCaseInsensitively() {
        Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
        booking.setId(null);
        booking.setUserEmail("Alice@Example.COM");
        bookingRepository.saveAndFlush(booking);

        LocalDateTime persistedAt = booking.getCreatedAt();
        assertThat(bookingRepository
                .existsByUserEmailIgnoreCaseAndCreatedAtAfter("alice@example.com", persistedAt.minusHours(1))).isTrue();
        assertThat(bookingRepository
                .existsByUserEmailIgnoreCaseAndCreatedAtAfter("other@example.com", persistedAt.minusHours(1))).isFalse();
        assertThat(bookingRepository.existsByUserEmailIgnoreCaseAndCreatedAtAfter(
                "alice@example.com", persistedAt.plusHours(1))).isFalse();
    }

    @Test
    void existsByVoteSessionId_findsLinkedBooking() {
        UUID expectedSessionId = UUID.randomUUID();
        Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
        booking.setId(null);
        booking.setVoteSessionId(expectedSessionId);
        bookingRepository.saveAndFlush(booking);

        assertThat(bookingRepository.existsByVoteSessionId(expectedSessionId)).isTrue();
        assertThat(bookingRepository.existsByVoteSessionId(UUID.randomUUID())).isFalse();
    }

    @Test
    void existsByInitiatorEmailIgnoreCaseAndCreatedAtAfter_matchesSessions() {
        Destination destination = destinationRepository.saveAndFlush(TestDataFactory.destination("Prague"));

        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail("Bob@Example.com");
        session.setNumberOfTravelers(4);
        session.setStartDate(LocalDate.now().plusDays(7));
        session.setEndDate(LocalDate.now().plusDays(9));
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setVoteMode(VoteMode.CART);
        session.setExpiresAt(LocalDateTime.now().plusHours(24));
        voteSessionRepository.saveAndFlush(session);

        LocalDateTime persistedAt = session.getCreatedAt();
        assertThat(voteSessionRepository
                .existsByInitiatorEmailIgnoreCaseAndCreatedAtAfter("bob@example.com", persistedAt.minusHours(1))).isTrue();
        assertThat(voteSessionRepository
                .existsByInitiatorEmailIgnoreCaseAndCreatedAtAfter("nobody@example.com", persistedAt.minusHours(1))).isFalse();
    }
}
