package com.myhive.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolves {@code application-prod.properties} and {@code application-proddb.properties} in the order
 * {@code SPRING_PROFILES_ACTIVE=prod,proddb} loads them, over the test classpath's base file (which
 * shadows main's, as everywhere in the test suite), and checks that the overlay wins over prod where
 * it must. Config data only, no beans: nothing here needs Postgres. The stray {@code EMAIL_ENABLED} /
 * {@code SCHEDULING_ENABLED} values stand in for a leftover line in the developer's env file, which
 * the overlay has to shrug off: prod would turn them into {@code true}, so an overlay that lost its
 * literal {@code false} fails here.
 */
class ProddbProfileTest {

    private static final String EXPECTED_DATABASE_URL = "jdbc:postgresql://db.example:5432/trivlu?sslmode=require";
    private static final String EXPECTED_DB_USERNAME = "trivlu_user";
    private static final String EXPECTED_DB_PASSWORD = "secret";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues(
                    "spring.profiles.active=prod,proddb",
                    "DATABASE_URL=" + EXPECTED_DATABASE_URL,
                    "DB_USERNAME=" + EXPECTED_DB_USERNAME,
                    "DB_PASSWORD=" + EXPECTED_DB_PASSWORD,
                    "PORT=10000",
                    "EMAIL_ENABLED=true",
                    "SCHEDULING_ENABLED=true",
                    "AUTH0_ISSUER_URI=https://issuer.example/",
                    "AUTH0_AUDIENCE=https://api.example");

    @Test
    void pinsOffEverythingASecondProcessMustNotDo() {
        contextRunner.run(context -> {
            Environment environment = context.getEnvironment();

            assertThat(environment.getProperty("app.email.enabled")).isEqualTo("false");
            assertThat(environment.getProperty("app.scheduling.enabled")).isEqualTo("false");
            assertThat(environment.getProperty("app.startup-repairs.enabled")).isEqualTo("false");
            assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("false");
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
        });
    }

    @Test
    void keepsProdsDatasourceAndServesAtTheRootOnPort8080() {
        contextRunner.run(context -> {
            Environment environment = context.getEnvironment();

            assertThat(environment.getProperty("spring.datasource.url")).isEqualTo(EXPECTED_DATABASE_URL);
            assertThat(environment.getProperty("spring.datasource.username")).isEqualTo(EXPECTED_DB_USERNAME);
            assertThat(environment.getProperty("spring.datasource.password")).isEqualTo(EXPECTED_DB_PASSWORD);
            assertThat(environment.getProperty("server.port")).isEqualTo("8080");
            assertThat(environment.getProperty("server.servlet.context-path")).isEmpty();
        });
    }
}
