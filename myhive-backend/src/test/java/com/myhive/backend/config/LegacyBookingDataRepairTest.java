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
class LegacyBookingVersionBackfillTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LegacyBookingVersionBackfill backfill;

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

        backfill.run(null);

        Long expectedBackfilledVersion = 0L;
        Long expectedUntouchedVersion = 5L;
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM bookings WHERE id = ?", Long.class, legacyId))
                .isEqualTo(expectedBackfilledVersion);
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM bookings WHERE id = ?", Long.class, modernId))
                .isEqualTo(expectedUntouchedVersion);
    }
}
