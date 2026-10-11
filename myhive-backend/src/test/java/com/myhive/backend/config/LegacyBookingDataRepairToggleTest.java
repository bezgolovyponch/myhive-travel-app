package com.myhive.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The startup repair is a write, so a second process on the database (proddb) has a switch to keep it off. */
class LegacyBookingDataRepairToggleTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withUserConfiguration(LegacyBookingDataRepair.class);

    @Test
    void theRepairRunsWhenThePropertyIsAbsent() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(LegacyBookingDataRepair.class));
    }

    @Test
    void theRepairIsOffWhenThePropertyIsFalse() {
        contextRunner.withPropertyValues("app.startup-repairs.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(LegacyBookingDataRepair.class));
    }
}
