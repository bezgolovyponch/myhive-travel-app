package com.myhive.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotent startup repairs for legacy booking rows (also covers restored backups):
 *
 * <p>1. Bookings created before {@code Booking} gained its {@code @Version} column carry
 * {@code version = NULL} ({@code ddl-auto=update} added the column without a default), and
 * Hibernate throws an NPE on any update of such a row.
 *
 * <p>2. Booking items created while the Trip Builder's "Custom Travel Package" placeholder
 * was snapshotted as the destination name carry that label instead of the activity's real
 * catalog destination, which breaks the admin bookings destination filter.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegacyBookingDataRepair implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        int versionsBackfilled = jdbcTemplate.update(
                "UPDATE bookings SET version = 0 WHERE version IS NULL");
        if (versionsBackfilled > 0) {
            log.info("Backfilled version=0 on {} legacy bookings", versionsBackfilled);
        }

        int destinationsRepaired = jdbcTemplate.update("""
                UPDATE booking_items bi SET destination_name = (
                    SELECT d.name FROM activities a
                    JOIN destinations d ON d.id = a.destination_id
                    WHERE a.id = bi.activity_id)
                WHERE bi.destination_name = 'Custom Travel Package'
                  AND EXISTS (SELECT 1 FROM activities a WHERE a.id = bi.activity_id)
                """);
        if (destinationsRepaired > 0) {
            log.info("Repaired catalog destination on {} legacy booking items", destinationsRepaired);
        }
    }
}
