package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.service.activity.ActivityCsvImporter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.myhive.backend.util.JwtTestHelper.adminJwt;
import static com.myhive.backend.util.JwtTestHelper.managerJwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestSecurityConfig.class)
class ActivityCsvImportExportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DestinationRepository destinationRepository;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ActivityCsvImporter activityCsvImporter;

    private Destination destination;
    private Activity activity;

    @BeforeEach
    void setUp() {
        activityCsvImporter.clearCacheForTest();
        destination = new Destination();
        destination.setName("Bali");
        destination.setSlug("bali");
        destination.setCountry("Indonesia");
        destination = destinationRepository.save(destination);

        Category beach = new Category();
        beach.setName("Beach");
        beach.setSlug("beach");
        beach = categoryRepository.save(beach);

        activity = new Activity();
        activity.setDestination(destination);
        activity.setSlug("surf");
        activity.setName("Surf lesson");
        activity.setDescription("Old desc");
        activity.setPrice(new BigDecimal("50.00"));
        activity.setDuration(90);
        activity.setIncludes("Board");
        activity.setCategories(new HashSet<>(Set.of(beach)));
        activity = activityRepository.save(activity);
    }

    @Test
    void export_asAdmin_returnsCsv() throws Exception {
        mockMvc.perform(get("/admin/activities/export").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(content().string(containsString("Surf lesson")));
    }

    @Test
    void export_asManager_returnsForbidden() throws Exception {
        mockMvc.perform(get("/admin/activities/export").with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void export_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/activities/export"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void importRoundTrip_descriptionChange_persists() throws Exception {
        MvcResult exportResult = mockMvc.perform(get("/admin/activities/export").with(adminJwt()))
                .andExpect(status().isOk()).andReturn();
        String csv = exportResult.getResponse().getContentAsString();

        String edited = csv.replace("Old desc", "New desc");
        MockMultipartFile file = new MockMultipartFile(
                "file", "activities.csv", "text/csv", edited.getBytes());

        MvcResult previewResult = mockMvc.perform(multipart("/admin/activities/import/preview")
                        .file(file).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.rowsToUpdate").value(1))
                .andReturn();

        JsonNode node = objectMapper.readTree(previewResult.getResponse().getContentAsString());
        String token = node.get("token").asText();

        mockMvc.perform(post("/admin/activities/import/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsUpdated").value(1));

        Activity reloaded = activityRepository.findById(activity.getId()).orElseThrow();
        assertThat(reloaded.getDescription()).isEqualTo("New desc");
    }

    @Test
    void importPreview_asManager_returnsForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "activities.csv", "text/csv", "id\n".getBytes());
        mockMvc.perform(multipart("/admin/activities/import/preview")
                        .file(file).with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void importApply_unknownToken_returnsBadRequestWithCode() throws Exception {
        mockMvc.perform(post("/admin/activities/import/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + UUID.randomUUID() + "\"}")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("TOKEN_NOT_FOUND"));
    }

    @Test
    void importApply_asManager_returnsForbidden() throws Exception {
        mockMvc.perform(post("/admin/activities/import/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + UUID.randomUUID() + "\"}")
                        .with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void importApply_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/admin/activities/import/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void importPreview_rowWithUnknownId_returnsJsonWithRowError() throws Exception {
        // Start from a valid exported CSV and replace the id with a random UUID that doesn't exist
        MvcResult exportResult = mockMvc.perform(get("/admin/activities/export").with(adminJwt()))
                .andExpect(status().isOk()).andReturn();
        String csv = exportResult.getResponse().getContentAsString();

        UUID fakeId = UUID.randomUUID();
        String edited = csv.replace(activity.getId().toString(), fakeId.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "activities.csv", "text/csv", edited.getBytes());

        mockMvc.perform(multipart("/admin/activities/import/preview")
                        .file(file).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(nullValue()))
                .andExpect(jsonPath("$.errors[0].code").value("ROW_NOT_FOUND"))
                .andExpect(jsonPath("$.errors[0].csvRowNumber").value(2));
    }

    @AfterEach
    void tearDown() {
        // Clean up rows committed by tests that bypass the class-level @Transactional rollback.
        // No-op for happy-path tests since their txn auto-rollbacks.
        if (TestTransaction.isActive()) {
            return;
        }
        TestTransaction.start();
        activityRepository.deleteAll();
        categoryRepository.deleteAll();
        destinationRepository.deleteAll();
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    @Test
    void apply_stateChangedMidLoop_rollsBackEarlierWrites() throws Exception {
        // Commit the @BeforeEach setup so subsequent apply() rollback is observable
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // Seed a SECOND activity in its own committed transaction
        TestTransaction.start();
        Activity second = new Activity();
        second.setDestination(destination);
        second.setSlug("dive");
        second.setName("Dive trip");
        second.setDescription("Original second desc");
        second.setPrice(new BigDecimal("80.00"));
        second.setDuration(120);
        second.setIncludes("Tank");
        second = activityRepository.save(second);
        UUID secondId = second.getId();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // 1) Export to get current state
        TestTransaction.start();
        MvcResult exportResult = mockMvc.perform(get("/admin/activities/export").with(adminJwt()))
                .andExpect(status().isOk()).andReturn();
        String csv = exportResult.getResponse().getContentAsString();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // 2) Edit BOTH activities (descriptions of A and B)
        String edited = csv
                .replace("Old desc", "Edited A — should be rolled back")
                .replace("Original second desc", "Edited B — never reached");
        MockMultipartFile file = new MockMultipartFile(
                "file", "activities.csv", "text/csv", edited.getBytes());

        // 3) Preview
        TestTransaction.start();
        MvcResult previewResult = mockMvc.perform(multipart("/admin/activities/import/preview")
                        .file(file).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsToUpdate").value(2))
                .andReturn();
        String previewToken = objectMapper.readTree(previewResult.getResponse().getContentAsString())
                .get("token").asText();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // 4) Delete the SECOND activity in the DB so apply hits STATE_CHANGED on it
        TestTransaction.start();
        activityRepository.deleteById(secondId);
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // 5) Apply — must fail with STATE_CHANGED. No TestTransaction wrapper: the apply()
        // call creates and manages its own @Transactional boundary which will roll back on
        // the thrown exception. Wrapping in a TestTransaction would cause apply()'s txn to
        // join ours, mark it rollback-only, and break flagForCommit().
        mockMvc.perform(post("/admin/activities/import/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + previewToken + "\"}")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("STATE_CHANGED"));

        // 6) The FIRST activity must still have its OLD description (rollback proved)
        TestTransaction.start();
        Activity reloaded = activityRepository.findById(activity.getId()).orElseThrow();
        assertThat(reloaded.getDescription()).isEqualTo("Old desc");
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }
}
