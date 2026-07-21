package com.myhive.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class LegacyBookingDataRepairTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LegacyBookingDataRepair repair;

    @Test
    void backfillsOnlyNullVersions() {
        UUID legacyId = UUID.randomUUID();
        UUID modernId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bookings (id, user_email, status, version) VALUES (?, 'legacy@x.y', 'PENDING', NULL)",
                legacyId);
        jdbcTemplate.update(
                "INSERT INTO bookings (id, user_email, status, version) VALUES (?, 'modern@x.y', 'PENDING', 5)",
                modernId);

        repair.run(null);

        Long expectedBackfilledVersion = 0L;
        Long expectedUntouchedVersion = 5L;
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM bookings WHERE id = ?", Long.class, legacyId))
                .isEqualTo(expectedBackfilledVersion);
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM bookings WHERE id = ?", Long.class, modernId))
                .isEqualTo(expectedUntouchedVersion);
    }

    @Test
    void repairsPlaceholderDestinationFromCatalog() {
        String expectedDestinationName = "Prague";
        UUID destinationId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID itemWithActivityId = UUID.randomUUID();
        UUID itemWithoutActivityId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO destinations (id, name) VALUES (?, ?)",
                destinationId, expectedDestinationName);
        jdbcTemplate.update(
                "INSERT INTO activities (id, name, price, destination_id) VALUES (?, 'Tottie Tour', 10.00, ?)",
                activityId, destinationId);
        jdbcTemplate.update(
                "INSERT INTO bookings (id, user_email, status, version) VALUES (?, 'x@y.z', 'PENDING', 0)",
                bookingId);
        jdbcTemplate.update(
                "INSERT INTO booking_items (id, booking_id, activity_id, destination_name) "
                        + "VALUES (?, ?, ?, 'Custom Travel Package')",
                itemWithActivityId, bookingId, activityId);
        // No catalog reference — the placeholder cannot be resolved and must stay untouched.
        jdbcTemplate.update(
                "INSERT INTO booking_items (id, booking_id, destination_name) "
                        + "VALUES (?, ?, 'Custom Travel Package')",
                itemWithoutActivityId, bookingId);

        repair.run(null);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT destination_name FROM booking_items WHERE id = ?", String.class, itemWithActivityId))
                .isEqualTo(expectedDestinationName);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT destination_name FROM booking_items WHERE id = ?", String.class, itemWithoutActivityId))
                .isEqualTo("Custom Travel Package");
    }
}
