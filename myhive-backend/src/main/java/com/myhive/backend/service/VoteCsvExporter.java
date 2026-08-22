package com.myhive.backend.service;

import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.repository.BookingRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionOpenRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VoteCsvExporter {

    static final String[] HEADER = {
            "session_id", "share_token", "groom_name", "vote_mode", "created_at",
            "opened_count", "voted_count", "booking_id", "booking_created_at", "paid_at"
    };

    private static final String BOM = "﻿";

    private final VoteSessionRepository voteSessionRepository;
    private final VoteSessionOpenRepository voteSessionOpenRepository;
    private final VoteActivityLikeRepository voteActivityLikeRepository;
    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public String exportAll() {
        List<VoteSession> sessions = voteSessionRepository.findAll();

        // Multiple bookings can share a vote_session_id (retries, consultation leads).
        // Sort by createdAt ascending first so toMap's merge function (a, b) -> a
        // deterministically keeps the earliest booking per session.
        Map<UUID, Booking> bookingBySession = bookingRepository
                .findByVoteSessionIdIn(sessions.stream().map(VoteSession::getId).toList())
                .stream()
                .sorted(Comparator.comparing(Booking::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toMap(Booking::getVoteSessionId, b -> b, (a, b) -> a));

        StringWriter out = new StringWriter();
        out.write(BOM);
        try (CSVWriter writer = new CSVWriter(out,
                CSVWriter.DEFAULT_SEPARATOR,
                CSVWriter.DEFAULT_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {
            writer.writeNext(HEADER, false);
            for (VoteSession s : sessions) {
                writer.writeNext(toRow(s, bookingBySession.get(s.getId())), false);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write CSV", e);
        }
        return out.toString();
    }

    private String[] toRow(VoteSession s, Booking booking) {
        long opened = voteSessionOpenRepository.countBySessionId(s.getId());
        long voted = voteActivityLikeRepository.countDistinctVoterTokensBySessionId(s.getId());
        return new String[]{
                String.valueOf(s.getId()),
                String.valueOf(s.getShareToken()),
                sanitize(nullSafe(s.getGroomName())),
                s.getVoteMode() == null ? "" : s.getVoteMode().toString(),
                s.getCreatedAt() == null ? "" : s.getCreatedAt().toString(),
                String.valueOf(opened),
                String.valueOf(voted),
                booking == null || booking.getId() == null ? "" : booking.getId().toString(),
                booking == null || booking.getCreatedAt() == null ? "" : booking.getCreatedAt().toString(),
                booking == null || booking.getPaidAt() == null ? "" : booking.getPaidAt().toString()
        };
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
