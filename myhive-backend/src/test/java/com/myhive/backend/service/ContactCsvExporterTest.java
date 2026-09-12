package com.myhive.backend.service;

import com.myhive.backend.dto.ContactDTO;
import com.myhive.backend.model.ContactSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContactCsvExporterTest {

    @Test
    void exportAll_writesBomHeaderAndSanitizedRows() {
        String expectedEmail = "anna@example.com";
        ContactDTO dto = new ContactDTO(UUID.randomUUID(), expectedEmail, "=HYPERLINK(evil)", "de",
                ContactSource.VOTE, ContactSource.BOOKING,
                LocalDateTime.of(2026, 9, 1, 10, 0), LocalDateTime.of(2026, 9, 12, 8, 30), 3, true);
        ContactService contactService = mock(ContactService.class);
        when(contactService.findAllForExport()).thenReturn(List.of(dto));
        ContactCsvExporter exporter = new ContactCsvExporter(contactService);

        String csv = exporter.exportAll();

        String[] lines = csv.split("\r?\n");
        assertThat(lines[0]).startsWith("﻿");
        assertThat(lines[0].substring(1)).isEqualTo(
                "email,name,locale,first_source,last_source,first_seen_at,last_seen_at,touch_count,unsubscribed");
        assertThat(lines[1]).isEqualTo(
                expectedEmail + ",'=HYPERLINK(evil),de,VOTE,BOOKING,2026-09-01T10:00,2026-09-12T08:30,3,true");
    }

    @Test
    void exportAll_sanitizesEmailStartingWithFormulaTrigger() {
        String expectedEmail = "+1+1@example.com";
        ContactDTO dto = new ContactDTO(UUID.randomUUID(), expectedEmail, null, null,
                ContactSource.VOTE, ContactSource.VOTE,
                LocalDateTime.of(2026, 9, 1, 10, 0), LocalDateTime.of(2026, 9, 12, 8, 30), 1, false);
        ContactService contactService = mock(ContactService.class);
        when(contactService.findAllForExport()).thenReturn(List.of(dto));
        ContactCsvExporter exporter = new ContactCsvExporter(contactService);

        String csv = exporter.exportAll();

        String[] lines = csv.split("\r?\n");
        assertThat(lines[1]).isEqualTo(
                "'" + expectedEmail + ",,,VOTE,VOTE,2026-09-01T10:00,2026-09-12T08:30,1,false");
    }
}
