package com.myhive.backend.service;

import com.myhive.backend.entity.Booking;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingFirstTouchExporterTest {

    @Mock private BookingRepository bookingRepository;

    @InjectMocks private BookingFirstTouchExporter exporter;

    @Test
    void exportsHeaderAndDaysFirstTouchToPaid() {
        Booking b = new Booking();
        b.setId(UUID.randomUUID());
        b.setTripId("trip-123");
        b.setStatus(BookingStatus.PAID);
        LocalDateTime paidAt = LocalDateTime.of(2026, 1, 10, 12, 0);
        b.setPaidAt(paidAt);
        b.setFirstTouchAt(paidAt.minusDays(3));
        b.setFirstUtmSource("google");
        b.setFirstUtmCampaign("spring");
        b.setUtmSource("google");
        b.setUtmMedium("cpc");
        b.setUtmCampaign("spring");
        b.setRef("friend");
        b.setVoteSessionId(UUID.randomUUID());
        when(bookingRepository.findByPaidAtIsNotNull()).thenReturn(List.of(b));

        String csv = exporter.exportPaid();
        String[] lines = csv.replace("﻿", "").split("\r?\n");

        assertThat(lines[0]).isEqualTo(
                "booking_id,trip_id,status,first_touch_at,created_at,paid_at,days_first_touch_to_paid,first_utm_source,first_utm_campaign,utm_source,utm_medium,utm_campaign,ref,vote_session_id");
        assertThat(lines[1]).contains(",3,");
    }

    @Test
    void tripIdFormulaInjectionIsSanitized() {
        Booking b = new Booking();
        b.setId(UUID.randomUUID());
        b.setTripId("=1+1");
        b.setStatus(BookingStatus.PAID);
        b.setPaidAt(LocalDateTime.of(2026, 1, 10, 12, 0));
        when(bookingRepository.findByPaidAtIsNotNull()).thenReturn(List.of(b));

        String csv = exporter.exportPaid();
        String dataLine = csv.replace("﻿", "").split("\r?\n")[1];
        String[] columns = dataLine.split(",", -1);
        // trip_id is column index 1
        assertThat(columns[1]).isEqualTo("'=1+1");
    }

    @Test
    void blankDaysWhenFirstTouchMissing() {
        Booking b = new Booking();
        b.setId(UUID.randomUUID());
        b.setStatus(BookingStatus.PAID);
        b.setPaidAt(LocalDateTime.of(2026, 1, 10, 12, 0));
        when(bookingRepository.findByPaidAtIsNotNull()).thenReturn(List.of(b));

        String csv = exporter.exportPaid();
        String dataLine = csv.replace("﻿", "").split("\r?\n")[1];
        String[] columns = dataLine.split(",", -1);
        // days_first_touch_to_paid is column index 6
        assertThat(columns[6]).isEmpty();
    }
}
