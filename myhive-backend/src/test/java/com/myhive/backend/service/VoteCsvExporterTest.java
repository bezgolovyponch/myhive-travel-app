package com.myhive.backend.service;

import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.repository.BookingRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionOpenRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteCsvExporterTest {

    @Mock private VoteSessionRepository voteSessionRepository;
    @Mock private VoteSessionOpenRepository voteSessionOpenRepository;
    @Mock private VoteActivityLikeRepository voteActivityLikeRepository;
    @Mock private BookingRepository bookingRepository;

    @InjectMocks private VoteCsvExporter exporter;

    @Test
    void exportsOneRowPerSessionWithBookingJoin() {
        VoteSession s = new VoteSession();
        s.setId(UUID.randomUUID());
        s.setShareToken(UUID.randomUUID());
        s.setGroomName("Tom");
        when(voteSessionRepository.findAll()).thenReturn(List.of(s));
        when(voteSessionOpenRepository.countBySessionId(s.getId())).thenReturn(7L);
        when(voteActivityLikeRepository.countDistinctVoterTokensBySessionId(s.getId())).thenReturn(5L);
        Booking b = new Booking();
        b.setVoteSessionId(s.getId());
        when(bookingRepository.findByVoteSessionIdIn(any())).thenReturn(List.of(b));

        String csv = exporter.exportAll();
        String[] lines = csv.replace("﻿", "").split("\r?\n");
        assertThat(lines[0]).isEqualTo("session_id,share_token,groom_name,vote_mode,created_at,opened_count,voted_count,booking_id,booking_created_at,paid_at");
        assertThat(lines[1]).contains("Tom").contains(",7,").contains(",5,");
    }
}
