package com.myhive.backend.repository;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.model.BookingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class BookingRepositoryTest {

    @Autowired
    private BookingRepository bookingRepository;

    private Booking createBooking(String email, BookingStatus status) {
        Booking b = new Booking();
        b.setUserEmail(email);
        b.setStatus(status);
        b.setTotalAmount(new BigDecimal("100.00"));
        return bookingRepository.save(b);
    }

    @Test
    void findByStripeSessionId_returnsBooking() {
        Booking b = createBooking("user@test.com", BookingStatus.PAID);
        b.setStripeSessionId("sess_abc123");
        bookingRepository.save(b);

        var result = bookingRepository.findByStripeSessionId("sess_abc123");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(b.getId());
    }

    @Test
    void countByStatus_returnsCorrectCount() {
        createBooking("a@test.com", BookingStatus.PENDING);
        createBooking("b@test.com", BookingStatus.PENDING);
        createBooking("c@test.com", BookingStatus.PAID);

        assertThat(bookingRepository.countByStatus(BookingStatus.PENDING)).isEqualTo(2);
        assertThat(bookingRepository.countByStatus(BookingStatus.PAID)).isEqualTo(1);
        assertThat(bookingRepository.countByStatus(BookingStatus.CONFIRMED)).isEqualTo(0);
    }

    @Test
    void findByStatus_returnsMatchingBookings() {
        createBooking("a@test.com", BookingStatus.PENDING);
        createBooking("b@test.com", BookingStatus.CONFIRMED);

        var result = bookingRepository.findByStatus(BookingStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getStatus()).isEqualTo(BookingStatus.PENDING);
    }
}
