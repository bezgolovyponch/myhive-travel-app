package com.myhive.backend.service;

import com.myhive.backend.dto.ContactDTO;
import com.myhive.backend.util.CsvCells;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ContactCsvExporter {

    static final String[] HEADER = {
            "email", "name", "locale", "first_source", "last_source",
            "first_seen_at", "last_seen_at", "touch_count", "unsubscribed"
    };

    private static final String BOM = "﻿";

    private final ContactService contactService;

    public String exportAll() {
        StringWriter out = new StringWriter();
        out.write(BOM);
        try (CSVWriter writer = new CSVWriter(out,
                CSVWriter.DEFAULT_SEPARATOR,
                CSVWriter.DEFAULT_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {
            writer.writeNext(HEADER, false);
            for (ContactDTO contact : contactService.findAllForExport()) {
                writer.writeNext(toRow(contact), false);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write CSV", e);
        }
        return out.toString();
    }

    private static String[] toRow(ContactDTO c) {
        return new String[] {
                c.getEmail(),
                CsvCells.sanitize(CsvCells.nullSafe(c.getName())),
                CsvCells.nullSafe(c.getLocale()),
                c.getFirstSource().name(),
                c.getLastSource().name(),
                timestamp(c.getFirstSeenAt()),
                timestamp(c.getLastSeenAt()),
                String.valueOf(c.getTouchCount()),
                String.valueOf(c.isUnsubscribed())
        };
    }

    private static String timestamp(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }
}
