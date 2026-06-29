package com.myhive.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.BookingPaymentShare;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.model.PaymentShareType;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class BookingPaymentShareRepositoryTest {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingPaymentShareRepository shareRepository;

    @Test
    void persistsSharesAndLooksUpByStripeIds() {
        Booking booking = new Booking();
        booking.setUserEmail("initiator@test.com");
        booking.setStatus(BookingStatus.DEPOSIT_PAID);
        booking.setTotalAmount(new BigDecimal("100.00"));
        booking.setAmountPaid(new BigDecimal("30.00"));
        Booking saved = bookingRepository.save(booking);

        BookingPaymentShare share = new BookingPaymentShare();
        share.setBooking(saved);
        share.setType(PaymentShareType.BALANCE_SHARE);
        share.setShareIndex(1);
        share.setAmount(new BigDecimal("23.34"));
        share.setStripePaymentLinkId("plink_123");
        share.setPaid(false);
        shareRepository.save(share);

        Optional<BookingPaymentShare> found = shareRepository.findByStripePaymentLinkId("plink_123");
        assertThat(found).isPresent();
        assertThat(found.get().getBooking().getId()).isEqualTo(saved.getId());
        assertThat(shareRepository.findByBookingId(saved.getId())).hasSize(1);
    }
}
