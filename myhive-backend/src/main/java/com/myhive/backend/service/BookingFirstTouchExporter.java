package com.myhive.backend.service;

import com.myhive.backend.entity.Booking;
import com.myhive.backend.repository.BookingRepository;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingFirstTouchExporter {

    static final String[] HEADER = {
            "booking_id", "trip_id", "status", "first_touch_at", "created_at", "paid_at",
            "days_first_touch_to_paid", "first_utm_source", "first_utm_campaign",
            "utm_source", "utm_medium", "utm_campaign", "ref", "vote_session_id"
    };

    private static final String BOM = "﻿";

    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public String exportPaid() {
        List<Booking> bookings = bookingRepository.findByPaidAtIsNotNull();

        StringWriter out = new StringWriter();
        out.write(BOM);
        try (CSVWriter writer = new CSVWriter(out,
                CSVWriter.DEFAULT_SEPARATOR,
                CSVWriter.DEFAULT_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {
            writer.writeNext(HEADER, false);
            for (Booking b : bookings) {
                writer.writeNext(toRow(b), false);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write CSV", e);
        }
        return out.toString();
    }

    private String[] toRow(Booking b) {
        return new String[]{
                String.valueOf(b.getId()),
                sanitize(nullSafe(b.getTripId())),
                b.getStatus() == null ? "" : b.getStatus().toString(),
                b.getFirstTouchAt() == null ? "" : b.getFirstTouchAt().toString(),
                b.getCreatedAt() == null ? "" : b.getCreatedAt().toString(),
                b.getPaidAt() == null ? "" : b.getPaidAt().toString(),
                daysFirstTouchToPaid(b),
                sanitize(nullSafe(b.getFirstUtmSource())),
                sanitize(nullSafe(b.getFirstUtmCampaign())),
                sanitize(nullSafe(b.getUtmSource())),
                sanitize(nullSafe(b.getUtmMedium())),
                sanitize(nullSafe(b.getUtmCampaign())),
                sanitize(nullSafe(b.getRef())),
                b.getVoteSessionId() == null ? "" : b.getVoteSessionId().toString()
        };
    }

    // first_touch_at is stored as naive UTC (captured client-side via toISOString), while paidAt
    // is a server-local LocalDateTime — mixing the two clocks in ChronoUnit.DAYS.between is a
    // day-granularity metric with a possible off-by-one near midnight boundaries until the
    // JVM/DB timezone is pinned to UTC. Acceptable for this report; not fixed here.
    private String daysFirstTouchToPaid(Booking b) {
        if (b.getFirstTouchAt() == null || b.getPaidAt() == null) {
            return "";
        }
        return String.valueOf(ChronoUnit.DAYS.between(b.getFirstTouchAt(), b.getPaidAt()));
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private String sanitize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        char c = s.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
            return "'" + s;
        }
        return s;
    }
}
