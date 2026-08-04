# Postmortem: "Create vote" returned 500 in production (2026-07-28 → 2026-08-03)

## Impact

Every vote-session creation — both flows, `POST /api/vote/sessions` (QUIZ) and
`POST /api/vote/sessions/cart` (CART) — failed with HTTP 500 from the deploy of
PR #9 (2026-07-28 20:23 UTC) until the fix went live (2026-08-03 15:12 UTC),
~6 days. Users saw "An unexpected error occurred" on every "Create vote" click.
No data was lost; no other endpoints were affected.

## Root cause

PR #9 ("Fixes main flow", `9b5b5c2`) stopped collecting the organizer's email in
the Start Group Vote modal and made `VoteSession.initiatorEmail` optional. The
matching schema change — dropping `NOT NULL` from `vote_sessions.initiator_email`,
which Hibernate `ddl-auto=update` cannot do — shipped as a Flyway migration
(`V1__initiator_email_optional.sql`) intended to run at prod startup.

The migration never ran, because of three stacked problems:

1. **Missing Spring Boot 4 module.** Spring Boot 4 moved `FlywayAutoConfiguration`
   out of `spring-boot-autoconfigure` into the separate
   `org.springframework.boot:spring-boot-flyway` module. PR #9 added only
   `flyway-core` + `flyway-database-postgresql`, so the Flyway library sat inert
   on the classpath: no `flyway_schema_history` table, no log output, no error —
   all `spring.flyway.*` properties were silently ignored. The prod column kept
   its `NOT NULL` constraint while the frontend stopped sending an email, so
   every insert died with a `PSQLException` → 500.

2. **Unsupported property combination.** Once the module was added (`6ccbc72`),
   the first redeploy crashed at startup: `application-prod.properties` carried
   `spring.jpa.defer-datasource-initialization=true`, which is incompatible with
   Flyway ("Circular depends-on relationship between 'flyway' and
   'entityManagerFactory'"). The flag was inert in prod anyway
   (`spring.sql.init.mode=never`); it was removed in `f23f03c`. Dev keeps it
   (Flyway is disabled there and `data.sql` needs it).

3. **Test-classpath shadowing.** `src/test/resources/application.properties`
   shadows the main `application.properties`, so the main file's
   `spring.flyway.enabled=false` did not apply to tests. With the auto-config
   present, Flyway tried to run the Postgres migration against fresh H2 before
   Hibernate's `create-drop` schema existed — 254 test failures. The opt-out is
   now repeated in the test properties file.

Contributing factor: Render's `update_failed` deploys keep the previous instance
serving, so the site stayed "up" while the new code crashed at startup — easy to
miss without checking deploy status or logs.

## Resolution

- `6ccbc72` — depend on `spring-boot-flyway` (Boot 4 auto-configuration module);
  repeat `spring.flyway.enabled=false` in the test-classpath properties.
- `f23f03c` — remove `spring.jpa.defer-datasource-initialization=true` from
  the prod profile.
- On deploy, Flyway baselined the existing schema at version 0 and applied V1:
  `flyway_schema_history` = 2 rows, `initiator_email` now nullable.

## Verification

- Direct API: `POST /api/vote/sessions` without an email → 201.
- Browser (Playwright, prod): CART flow (Trip Builder → "Let your mates vote" →
  Create vote → `/cart` 201 → waiting page) and QUIZ flow (hero → setup modal →
  4-question quiz → 20-card curate deck → Trip Builder handoff → Create vote →
  201 → waiting page). Lifecycle guards confirmed working: "A vote is already
  running" modal while a session is active; vote button hidden after a finished
  vote.
- Regression tests: `FlywayMigrationSetupTest` pins (a) the auto-configuration
  class on the classpath, (b) the packaged V1 migration, (c) that the prod
  profile never combines Flyway with `defer-datasource-initialization`.

## Lessons

1. **Spring Boot 4 modular auto-configuration fails silently.** A library on the
   classpath no longer implies its Boot integration is active. After any deploy
   that relies on a migration, verify `flyway_schema_history` in the target DB
   or a Flyway line in startup logs — absence of errors is not success.
2. **`ddl-auto=update` + Flyway is a split-brain risk.** Hibernate keeps the app
   running against a schema the migrations were supposed to change, so the
   failure surfaces at runtime (insert time), not at startup.
3. **A failed Render deploy masks startup crashes.** `update_failed` leaves the
   old build live; monitor deploy status, not just site availability.
4. **Prod-only configuration is invisible to the test suite.** The test
   classpath shadows main properties and the prod profile never boots in CI;
   property-combination lint tests are a cheap guard for known-bad combos.
