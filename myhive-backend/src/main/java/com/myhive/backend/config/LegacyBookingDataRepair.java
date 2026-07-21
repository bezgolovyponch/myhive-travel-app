package com.myhive.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Data repair for bookings created before {@code Booking} gained its {@code @Version} column:
 * {@code ddl-auto=update} added the column without a default, leaving legacy rows with
 * {@code version = NULL}, and Hibernate throws an NPE on any update of such a row.
 * Idempotent and cheap, so it simply runs at every startup (also covers restored backups).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegacyBookingVersionBackfill implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        int updated = jdbcTemplate.update("UPDATE bookings SET version = 0 WHERE version IS NULL");
        if (updated > 0) {
            log.info("Backfilled version=0 on {} legacy bookings", updated);
        }
    }
}
