package com.myhive.backend.ai.graph;

import com.myhive.backend.ai.catalog.CatalogSnapshotter;
import com.myhive.backend.ai.graph.nodes.PersistResultNode;
import com.myhive.backend.ai.graph.nodes.SelectNode;
import com.myhive.backend.ai.llm.ChatTurnResult;
import com.myhive.backend.ai.llm.FakeLlmGateway;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.model.Brief;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * What production actually runs the planner on: {@link PostgresSaver} against a real Postgres, with
 * the checkpoint tables coming from Flyway rather than from the saver itself.
 *
 * <p>Needs Docker, so it is tagged {@code docker} and disables itself when none is running. To run it
 * on purpose: {@code ./gradlew test --tests '*PostgresCheckpointPersistenceTest' -i}.
 *
 * <p>Two schemas are compared: {@link #SAVER_SCHEMA}, built by {@code createTables(true)}, and
 * {@link #MIGRATION_SCHEMA}, built by the saver block of {@code V7__ai_planner.sql}. Prod builds the
 * saver with {@code createTables(false)}, so a langgraph4j upgrade that changes the schema has to be
 * mirrored into V7 by hand — {@link #flywayDdl_matchesTheSaversOwnDdl} is what notices it did not.
 */
@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
class PostgresCheckpointPersistenceTest {

    /** Prod is Render Postgres 18; the saver's partial unique index and JSONB need nothing older. */
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final String SAVER_SCHEMA = "saver_ddl";
    private static final String MIGRATION_SCHEMA = "migration_ddl";
    private static final String MIGRATION_RESOURCE = "/db/migration/V7__ai_planner.sql";
    /** The line V7 introduces its copy of the saver's DDL with; everything after it is that block. */
    private static final String SAVER_DDL_MARKER = "-- langgraph4j PostgresSaver tables";

    @BeforeAll
    static void createSchemas() throws SQLException {
        execute(dataSource(null),
                "CREATE SCHEMA IF NOT EXISTS " + SAVER_SCHEMA + "; CREATE SCHEMA IF NOT EXISTS " + MIGRATION_SCHEMA);
    }

    private static PGSimpleDataSource dataSource(String schema) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        if (schema != null) {
            ds.setCurrentSchema(schema);
        }
        return ds;
    }

    /** The production bean itself - {@code createTables(false)} and the planner's own serializer. */
    private static BaseCheckpointSaver prodSaver(String schema) throws SQLException {
        return new CheckpointSaverConfig().postgresCheckpointSaver(dataSource(schema));
    }

    private static PlannerGraph graph(FakeLlmGateway llm, BaseCheckpointSaver saver) {
        return TestPlannerGraphs.withSaver(llm, mock(CatalogSnapshotter.class),
                mock(PersistResultNode.GenerationResultSink.class), mock(SelectNode.SelectionSink.class), saver);
    }

    private static Map<String, Object> seed() {
        return Map.of(
                PlannerState.LOCALE, "en",
                PlannerState.DESTINATION_ID, UUID.randomUUID().toString(),
                PlannerState.DESTINATION_NAME, "Prague",
                PlannerState.CATEGORY_SLUGS, List.of("nightlife"),
                PlannerState.BRIEF, JsonCodec.write(Brief.empty()),
                PlannerState.MESSAGES, List.of(Map.of("role", "ASSISTANT", "content", "hi", "at", "t")));
    }

    /**
     * Seeds a parked thread, throws the graph and its saver away, and resumes the thread through a
     * brand-new saver on the same database - which is what a deploy does to a chat mid-conversation.
     */
    private static void seedThenResumeThroughAFreshSaver(String schema, BaseCheckpointSaver first)
            throws SQLException {
        FakeLlmGateway llm = new FakeLlmGateway();
        UUID token = UUID.randomUUID();
        int expectedMessageCount = 3;
        graph(llm, first).seedParked(token, seed());

        // "restart": a brand-new graph and saver, sharing nothing but the database.
        PlannerGraph second = graph(llm, prodSaver(schema));
        assertThat(second.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_USER);
        llm.queueChat(new ChatTurnResult("How many days?", Brief.empty(), List.of(), LlmUsage.none()));
        second.update(token, Map.of(
                PlannerState.RESUME_REASON, ResumeReason.USER_MESSAGE.name(),
                PlannerState.MESSAGES, List.of(Map.of("role", "USER", "content", "8 of us", "at", "t"))));
        second.runUntilInterrupt(token);

        assertThat(second.snapshot(token).state().messages()).hasSize(expectedMessageCount);
        assertThat(second.snapshot(token).next()).isEqualTo(PlannerGraph.AWAIT_USER);
        second.release(token);
    }

    @Test
    void threadParkedAtAwaitUser_resumesAfterSaverIsRebuilt() throws SQLException {
        seedThenResumeThroughAFreshSaver(SAVER_SCHEMA, saverWithItsOwnTables());
    }

    @Test
    void flywayDdl_isEnoughForTheSaver() throws SQLException, IOException {
        applyMigrationDdl();

        // The prod bean on both sides: nothing but V7 ever creates a table here.
        seedThenResumeThroughAFreshSaver(MIGRATION_SCHEMA, prodSaver(MIGRATION_SCHEMA));
    }

    @Test
    void flywayDdl_matchesTheSaversOwnDdl() throws SQLException, IOException {
        saverWithItsOwnTables();
        applyMigrationDdl();

        assertThat(columnsOf(MIGRATION_SCHEMA)).isEqualTo(columnsOf(SAVER_SCHEMA));
        assertThat(indexesOf(MIGRATION_SCHEMA)).isEqualTo(indexesOf(SAVER_SCHEMA));
        assertThat(constraintsOf(MIGRATION_SCHEMA)).isEqualTo(constraintsOf(SAVER_SCHEMA));
    }

    /**
     * The one place {@code createTables(true)} is used: building this saver is what puts the
     * library's own DDL into {@link #SAVER_SCHEMA}. {@code CREATE TABLE IF NOT EXISTS} makes it
     * idempotent, so every test may call it.
     */
    private static BaseCheckpointSaver saverWithItsOwnTables() throws SQLException {
        return PostgresSaver.builder()
                .datasource(dataSource(SAVER_SCHEMA))
                .stateSerializer(new PlannerStateSerializer())
                .createTables(true)
                .build();
    }

    /** Runs the saver block of V7 the way Flyway would, into its own schema. */
    private static void applyMigrationDdl() throws SQLException, IOException {
        execute(dataSource(MIGRATION_SCHEMA), saverDdlFromMigration());
    }

    private static String saverDdlFromMigration() throws IOException {
        try (InputStream in = PostgresCheckpointPersistenceTest.class.getResourceAsStream(MIGRATION_RESOURCE)) {
            assertThat(in).as("migration %s is on the test classpath", MIGRATION_RESOURCE).isNotNull();
            String migration = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int start = migration.indexOf(SAVER_DDL_MARKER);
            assertThat(start).as("%s must introduce the saver DDL with \"%s\"", MIGRATION_RESOURCE, SAVER_DDL_MARKER)
                    .isNotNegative();
            return migration.substring(start);
        }
    }

    private static void execute(PGSimpleDataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static List<String> columnsOf(String schema) throws SQLException {
        return query(schema, """
                SELECT table_name || '.' || column_name || ' ' || data_type
                       || ' nullable=' || is_nullable
                       || ' default=' || COALESCE(column_default, '-')
                FROM information_schema.columns
                WHERE table_schema = ?
                ORDER BY table_name, column_name
                """);
    }

    private static List<String> indexesOf(String schema) throws SQLException {
        return query(schema, """
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = ?
                ORDER BY indexname
                """);
    }

    private static List<String> constraintsOf(String schema) throws SQLException {
        return query(schema, """
                SELECT conname || ' ' || pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE connamespace = ?::regnamespace
                ORDER BY conname
                """);
    }

    /** Runs a one-parameter schema query and strips the schema name, so two schemas compare equal. */
    private static List<String> query(String schema, String sql) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection connection = dataSource(null).getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(rs.getString(1).replace(schema + ".", ""));
                }
            }
        }
        return rows;
    }
}
