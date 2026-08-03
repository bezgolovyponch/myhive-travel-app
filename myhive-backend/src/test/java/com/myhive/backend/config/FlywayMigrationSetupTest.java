package com.myhive.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Pins the wiring that lets prod schema migrations actually run.
 *
 * <p>Spring Boot 4 ships {@code FlywayAutoConfiguration} in the separate
 * {@code spring-boot-flyway} module. With only {@code flyway-core} on the
 * classpath, the {@code spring.flyway.*} properties in
 * {@code application-prod.properties} are silently ignored and migrations
 * never execute — which left {@code vote_sessions.initiator_email} NOT NULL
 * in prod and broke vote-session creation with a 500.
 */
class FlywayMigrationSetupTest {

    @Test
    void flywayAutoConfigurationIsOnClasspath() throws Exception {
        Class<?> autoConfiguration =
                Class.forName("org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration");

        assertThat(autoConfiguration).isNotNull();
    }

    @Test
    void initiatorEmailMigrationIsPackaged() {
        ClassPathResource migration =
                new ClassPathResource("db/migration/V1__initiator_email_optional.sql");

        assertThat(migration.exists()).isTrue();
    }

    /**
     * Flyway + {@code spring.jpa.defer-datasource-initialization=true} is an
     * unsupported combination: Boot wires flyway → deferred script initializer
     * → entityManagerFactory → flyway, and prod startup dies with "Circular
     * depends-on relationship between 'flyway' and 'entityManagerFactory'".
     */
    @Test
    void prodProfileDoesNotCombineFlywayWithDeferredDatasourceInit() throws Exception {
        Properties prodProperties = new Properties();
        try (InputStream stream =
                new ClassPathResource("application-prod.properties").getInputStream()) {
            prodProperties.load(stream);
        }

        assertThat(prodProperties.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(prodProperties.getProperty("spring.jpa.defer-datasource-initialization"))
                .as("defer-datasource-initialization must not be set in prod while Flyway is enabled")
                .isNull();
    }
}
