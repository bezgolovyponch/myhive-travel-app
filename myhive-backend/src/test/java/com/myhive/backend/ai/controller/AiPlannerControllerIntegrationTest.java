package com.myhive.backend.ai.controller;

import com.jayway.jsonpath.JsonPath;
import com.myhive.backend.TestDataFactory;
import com.myhive.backend.ai.AiTestConfig;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.llm.AiProperties;
import com.myhive.backend.ai.llm.ChatTurnResult;
import com.myhive.backend.ai.llm.FakeLlmGateway;
import com.myhive.backend.ai.llm.LlmGateway;
import com.myhive.backend.ai.llm.LlmUnavailableException;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.PlanDraft;
import com.myhive.backend.ai.service.AiSessionService;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.AiGeneration;
import com.myhive.backend.entity.AiGenerationStatus;
import com.myhive.backend.entity.AiSession;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.AiGenerationRepository;
import com.myhive.backend.repository.AiSessionRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The {@code /ai} HTTP surface against the real graph, the real {@code aiTaskExecutor} job and a
 * scripted model. Deliberately not {@code @Transactional}: the generation job runs on a pool thread
 * with its own persistence context and would never see rows an open test transaction still holds.
 * Every test therefore cleans up what it created in {@link #cleanUp()}.
 */
@SpringBootTest(properties = "app.ai.enabled=true")
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, AiTestConfig.class})
class AiPlannerControllerIntegrationTest {

    private static final int ACTIVITY_COUNT = 6;
    private static final int BASIC_INDEX = 0;
    private static final int MEDIUM_INDEX = 2;
    private static final int PREMIUM_FIRST_INDEX = 4;
    private static final int PREMIUM_SECOND_INDEX = 5;
    private static final int DURATION_MINUTES = 90;
    private static final int GROUP_SIZE = 4;
    /** Floors the last activity's line (70 x 4 = 280) so the contract's group-minimum fields are exercised. */
    private static final BigDecimal GROUP_MINIMUM = new BigDecimal("500.00");
    private static final int POLL_ATTEMPTS = 100;
    private static final long POLL_INTERVAL_MILLIS = 50L;

    private static final String CREATE_BODY = """
            {"destinationSlug": "%s", "locale": "en"}
            """;
    private static final String CREATE_WITH_MESSAGE_BODY = """
            {"destinationSlug": "%s", "locale": "en", "initialMessage": "%s"}
            """;
    private static final String MESSAGE_BODY = """
            {"content": "%s"}
            """;
    private static final String SELECT_BODY = """
            {"packageKey": "%s"}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LlmGateway llmGateway;
    @Autowired
    private AiProperties aiProperties;
    @Autowired
    private DestinationRepository destinationRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private AiSessionRepository sessionRepository;
    @Autowired
    private AiGenerationRepository generationRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private FakeLlmGateway llm;
    private TransactionTemplate transactions;
    private Category category;
    private Destination destination;
    private String expectedCategoryName;
    private final List<Activity> activities = new ArrayList<>();

    @BeforeEach
    void setUp() {
        llm = (FakeLlmGateway) llmGateway;
        llm.reset();
        transactions = new TransactionTemplate(transactionManager);
        // Names and slugs are unique per test: the rows are committed, so a leftover from a previous
        // method would collide on the unique constraints of categories/destinations/activities.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        expectedCategoryName = "Nightlife " + suffix;
        category = new Category();
        category.setName(expectedCategoryName);
        category.setSlug("nightlife-" + suffix);
        category = categoryRepository.save(category);

        destination = TestDataFactory.destination("Prague");
        destination.setSlug("prague-" + suffix);
        destination.getCategories().add(category);
        destination = destinationRepository.save(destination);

        activities.clear();
        for (int i = 0; i < ACTIVITY_COUNT; i++) {
            Activity activity = TestDataFactory.activity(destination, "Act " + i, new BigDecimal(20 + i * 10));
            activity.setSlug("act-" + i + "-" + suffix);
            activity.setImageUrl("https://example.com/act-" + i + ".jpg");
            activity.setDescription("Act " + i + " description");
            activity.setDuration(DURATION_MINUTES);
            activity.getCategories().add(category);
            if (i == PREMIUM_SECOND_INDEX) {
                activity.setMinPrice(GROUP_MINIMUM);
            }
            activities.add(activityRepository.save(activity));
        }
    }

    @AfterEach
    void cleanUp() {
        transactions.executeWithoutResult(status -> {
            for (AiSession session : sessionRepository.findAll()) {
                if (destination.getId().equals(session.getDestination().getId())) {
                    generationRepository.deleteBySessionId(session.getId());
                    sessionRepository.delete(session);
                }
            }
        });
        activityRepository.deleteAll(activities);
        destinationRepository.delete(destination);
        categoryRepository.delete(category);
    }

    @Test
    void fullCycle_chat_generate_poll_select() throws Exception {
        String expectedReply = "Building it!";
        String expectedPremiumName = activities.get(PREMIUM_FIRST_INDEX).getName();
        String expectedPremiumSlug = activities.get(PREMIUM_FIRST_INDEX).getSlug();
        UUID expectedPremiumId = activities.get(PREMIUM_FIRST_INDEX).getId();
        String token = createSession();
        queueReadyTurn(expectedReply);

        MvcResult turn = mockMvc.perform(post("/ai/sessions/" + token + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MESSAGE_BODY.formatted("1 day, 4 of us, bars")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.role", is("ASSISTANT")))
                .andExpect(jsonPath("$.message.content", is(expectedReply)))
                .andExpect(jsonPath("$.message.at", notNullValue()))
                .andExpect(jsonPath("$.brief.groupSize", is(GROUP_SIZE)))
                .andExpect(jsonPath("$.brief.days", is(1)))
                .andExpect(jsonPath("$.brief.arrival", is("AFTERNOON")))
                .andExpect(jsonPath("$.missingFields", hasSize(0)))
                .andExpect(jsonPath("$.readyToGenerate", is(true)))
                .andExpect(jsonPath("$.generation.status", is("QUEUED")))
                .andExpect(jsonPath("$.generation.sessionToken", is(token)))
                .andReturn();
        String generationId = JsonPath.read(turn.getResponse().getContentAsString(), "$.generation.id");

        String body = awaitGeneration(generationId);
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("READY");
        assertThat((String) JsonPath.read(body, "$.sessionToken")).isEqualTo(token);
        assertThat((Boolean) JsonPath.read(body, "$.degraded")).isFalse();
        assertThat((Integer) JsonPath.read(body, "$.brief.groupSize")).isEqualTo(GROUP_SIZE);
        assertThat((List<?>) JsonPath.read(body, "$.packages")).hasSize(Tier.values().length);
        assertThat((String) JsonPath.read(body, "$.packages[0].key")).isEqualTo("BASIC");
        assertThat((String) JsonPath.read(body, "$.packages[1].key")).isEqualTo("MEDIUM");
        assertThat((String) JsonPath.read(body, "$.packages[2].key")).isEqualTo("PREMIUM");
        assertThat((String) JsonPath.read(body, "$.packages[2].currency")).isEqualTo("EUR");
        assertThat((String) JsonPath.read(body, "$.packages[2].title")).isEqualTo(Tier.PREMIUM.name());
        assertThat((Integer) JsonPath.read(body, "$.packages[2].totalDurationMinutes"))
                .isEqualTo(2 * DURATION_MINUTES);
        assertThat((List<?>) JsonPath.read(body, "$.packages[2].activityIds")).hasSize(2);
        assertThat((Integer) JsonPath.read(body, "$.packages[2].days[0].dayNumber")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(body, "$.packages[2].days[0].items.length()")).isEqualTo(2);

        String firstItem = "$.packages[2].days[0].items[0].";
        assertThat((String) JsonPath.read(body, firstItem + "slot")).isEqualTo(Slot.AFTERNOON.name());
        assertThat((String) JsonPath.read(body, firstItem + "activityId")).isEqualTo(expectedPremiumId.toString());
        assertThat((String) JsonPath.read(body, firstItem + "name")).isEqualTo(expectedPremiumName);
        assertThat((String) JsonPath.read(body, firstItem + "slug")).isEqualTo(expectedPremiumSlug);
        assertThat((Integer) JsonPath.read(body, firstItem + "durationMinutes")).isEqualTo(DURATION_MINUTES);
        assertThat((Boolean) JsonPath.read(body, firstItem + "groupMinApplied")).isFalse();
        assertThat((String) JsonPath.read(body, firstItem + "why")).isEqualTo("why");
        // The group minimum floors the second PREMIUM line, exactly as the Trip Builder shows it.
        String flooredItem = "$.packages[2].days[0].items[1].";
        assertThat((Boolean) JsonPath.read(body, flooredItem + "groupMinApplied")).isTrue();
        assertThat(money(body, flooredItem + "minPrice")).isEqualByComparingTo(GROUP_MINIMUM);
        assertThat(money(body, flooredItem + "lineTotal")).isEqualByComparingTo(GROUP_MINIMUM);
        BigDecimal expectedPremiumPerPerson = new BigDecimal("185.00");
        assertThat(money(body, "$.packages[2].pricePerPerson")).isEqualByComparingTo(expectedPremiumPerPerson);

        mockMvc.perform(post("/ai/generations/" + generationId + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SELECT_BODY.formatted(Tier.PREMIUM)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packageKey", is(Tier.PREMIUM.name())))
                .andExpect(jsonPath("$.groupSize", is(GROUP_SIZE)))
                .andExpect(jsonPath("$.tripItems", hasSize(2)))
                .andExpect(jsonPath("$.tripItems[0].activityId", is(expectedPremiumId.toString())))
                .andExpect(jsonPath("$.tripItems[0].name", is(expectedPremiumName)))
                .andExpect(jsonPath("$.tripItems[0].slug", is(expectedPremiumSlug)))
                .andExpect(jsonPath("$.tripItems[0].destinationSlug", is(destination.getSlug())))
                .andExpect(jsonPath("$.tripItems[0].duration", is(DURATION_MINUTES)))
                .andExpect(jsonPath("$.tripItems[0].categories", hasSize(1)))
                .andExpect(jsonPath("$.tripItems[0].categories[0]", is(expectedCategoryName)))
                .andExpect(jsonPath("$.tripItems[1].activityId",
                        is(activities.get(PREMIUM_SECOND_INDEX).getId().toString())));

        mockMvc.perform(get("/ai/sessions/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is(token)))
                .andExpect(jsonPath("$.destinationSlug", is(destination.getSlug())))
                .andExpect(jsonPath("$.locale", is("en")))
                .andExpect(jsonPath("$.status", is("READY")))
                .andExpect(jsonPath("$.latestGeneration.id", is(generationId)))
                .andExpect(jsonPath("$.latestGeneration.selectedPackageKey", is(Tier.PREMIUM.name())))
                .andExpect(jsonPath("$.messages", hasSize(3)))
                .andExpect(jsonPath("$.messages[1].role", is("USER")))
                .andExpect(jsonPath("$.readyToGenerate", is(true)))
                .andExpect(jsonPath("$.limits.messagesLeft", is(AiSessionService.MAX_MESSAGES - 1)))
                .andExpect(jsonPath("$.limits.generationsLeft", is(AiSessionService.MAX_GENERATIONS - 1)));
    }

    @Test
    void createSession_withInitialMessage_alreadyCarriesTheFirstReply() throws Exception {
        String expectedReply = "How many of you are coming?";
        llm.queueChat(chatTurn(expectedReply, Brief.empty()));

        mockMvc.perform(post("/ai/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_WITH_MESSAGE_BODY.formatted(destination.getSlug(), "we want a stag do")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("COLLECTING")))
                .andExpect(jsonPath("$.messages", hasSize(3)))
                .andExpect(jsonPath("$.messages[2].content", is(expectedReply)))
                .andExpect(jsonPath("$.missingFields", hasSize(3)))
                .andExpect(jsonPath("$.readyToGenerate", is(false)))
                .andExpect(jsonPath("$.latestGeneration").doesNotExist());
    }

    @Test
    void manualGenerate_afterTheBriefIsReady_is202Accepted() throws Exception {
        String token = createSession();
        queueReadyTurn("Building it!");
        sendMessage(token, "1 day, 4 of us, bars");
        String firstGenerationId = latestGenerationId(token);
        awaitGeneration(firstGenerationId);
        llm.queuePlan(planDraft());

        MvcResult accepted = mockMvc.perform(post("/ai/sessions/" + token + "/generations"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is(AiGenerationStatus.QUEUED.name())))
                .andExpect(jsonPath("$.sessionToken", is(token)))
                .andReturn();
        String secondGenerationId = JsonPath.read(accepted.getResponse().getContentAsString(), "$.id");

        assertThat(secondGenerationId).isNotEqualTo(firstGenerationId);
        assertThat((String) JsonPath.read(awaitGeneration(secondGenerationId), "$.status")).isEqualTo("READY");
    }

    @Test
    void manualGenerate_beforeBriefReady_is409BriefIncomplete() throws Exception {
        String token = createSession();

        mockMvc.perform(post("/ai/sessions/" + token + "/generations"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("BRIEF_INCOMPLETE")));
    }

    @Test
    void unknownToken_is404SessionNotFound_andBadContentIs400() throws Exception {
        mockMvc.perform(get("/ai/sessions/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("SESSION_NOT_FOUND")));

        String token = createSession();
        // The handler's own label, not the contract's VALIDATION_ERROR - see the task report.
        mockMvc.perform(post("/ai/sessions/" + token + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content(MESSAGE_BODY.formatted("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    @Test
    void unknownGeneration_is404GenerationNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(get("/ai/generations/" + unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("GENERATION_NOT_FOUND")));
        mockMvc.perform(post("/ai/generations/" + unknownId + "/select").contentType(MediaType.APPLICATION_JSON)
                        .content(SELECT_BODY.formatted(Tier.BASIC)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("GENERATION_NOT_FOUND")));
    }

    @Test
    void llmFailureOnChatTurn_is502LlmUnavailable_andMessageIsKept() throws Exception {
        String token = createSession();

        // No chat answer queued: FakeLlmGateway throws IllegalStateException, mapped to LLM_UNAVAILABLE.
        mockMvc.perform(post("/ai/sessions/" + token + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content(MESSAGE_BODY.formatted("hello")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error", is("LLM_UNAVAILABLE")));

        mockMvc.perform(get("/ai/sessions/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[1].role", is("USER")));
    }

    @Test
    void llmTimeoutOnChatTurn_is502LlmTimeout() throws Exception {
        String token = createSession();
        llm.failNextChat(new LlmUnavailableException("chat timed out after 20s", null));

        mockMvc.perform(post("/ai/sessions/" + token + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content(MESSAGE_BODY.formatted("hello")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error", is("LLM_TIMEOUT")));
    }

    @Test
    void select_onAGenerationThatIsNotReady_is409GenerationNotReady() throws Exception {
        String token = createSession();
        AiGeneration queued = queuedGeneration(token);

        mockMvc.perform(post("/ai/generations/" + queued.getId() + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SELECT_BODY.formatted(Tier.BASIC)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("GENERATION_NOT_READY")));
    }

    @Test
    void selectAndRegenerate_whileAnotherGenerationIsQueued_are409GenerationInProgress() throws Exception {
        String token = createSession();
        queueReadyTurn("Building it!");
        sendMessage(token, "1 day, 4 of us, bars");
        String readyGenerationId = latestGenerationId(token);
        awaitGeneration(readyGenerationId);
        // A second generation that never runs: the guard is "a job owns this chat's graph thread".
        queuedGeneration(token);

        mockMvc.perform(post("/ai/generations/" + readyGenerationId + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SELECT_BODY.formatted(Tier.BASIC)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("GENERATION_IN_PROGRESS")));
        mockMvc.perform(post("/ai/sessions/" + token + "/generations"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("GENERATION_IN_PROGRESS")));
    }

    @Test
    void message_overTheTurnLimit_is429SessionTurnLimit() throws Exception {
        String token = createSession();
        AiSession session = sessionRepository.findByToken(UUID.fromString(token)).orElseThrow();
        session.setMessageCount(AiSessionService.MAX_MESSAGES);
        sessionRepository.save(session);

        mockMvc.perform(post("/ai/sessions/" + token + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content(MESSAGE_BODY.formatted("one more")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error", is("SESSION_TURN_LIMIT")));
    }

    @Test
    void manualGenerate_overTheGenerationLimit_is429GenerationLimit() throws Exception {
        String token = createSession();
        queueReadyTurn("Building it!");
        sendMessage(token, "1 day, 4 of us, bars");
        awaitGeneration(latestGenerationId(token));
        AiSession session = sessionRepository.findByToken(UUID.fromString(token)).orElseThrow();
        session.setGenerationCount(AiSessionService.MAX_GENERATIONS);
        sessionRepository.save(session);

        mockMvc.perform(post("/ai/sessions/" + token + "/generations"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error", is("GENERATION_LIMIT")));
    }

    @Test
    void createSession_overTheDailyCapForOneClientIp_is429SessionDailyLimit() throws Exception {
        // The proxy chain's FIRST entry is the client. The trailing hop alternates on purpose: reading
        // the last entry instead would split these calls over two buckets and never reach the cap.
        String expectedClientIp = "9.9.9.9";
        for (int i = 0; i < aiProperties.getDailySessionsPerIp(); i++) {
            mockMvc.perform(post("/ai/sessions").contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", expectedClientIp + ", 10.0.0." + (i % 2 + 1))
                            .content(CREATE_BODY.formatted(destination.getSlug())))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/ai/sessions").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", expectedClientIp + ", 10.0.0.3")
                        .content(CREATE_BODY.formatted(destination.getSlug())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error", is("SESSION_DAILY_LIMIT")));
    }

    @Test
    void createSession_withoutTurnstileTokenWhenRequired_is403TurnstileFailed() throws Exception {
        // Flipped on the live properties bean rather than in a second Spring context: this class owns
        // its context, JUnit runs its methods one at a time, and the switch is restored either way.
        aiProperties.setTurnstileRequired(true);
        try {
            mockMvc.perform(post("/ai/sessions").contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY.formatted(destination.getSlug())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error", is("TURNSTILE_FAILED")));
        } finally {
            aiProperties.setTurnstileRequired(false);
        }
    }

    @Test
    void createSession_forAnUnknownDestination_is400() throws Exception {
        mockMvc.perform(post("/ai/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.formatted("no-such-destination")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }

    private String createSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/ai/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.formatted(destination.getSlug())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("COLLECTING")))
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].role", is("ASSISTANT")))
                .andExpect(jsonPath("$.readyToGenerate", is(false)))
                .andExpect(jsonPath("$.limits.messagesLeft", is(AiSessionService.MAX_MESSAGES)))
                .andExpect(jsonPath("$.limits.generationsLeft", is(AiSessionService.MAX_GENERATIONS)))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private void sendMessage(String token, String content) throws Exception {
        mockMvc.perform(post("/ai/sessions/" + token + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content(MESSAGE_BODY.formatted(content)))
                .andExpect(status().isOk());
    }

    private String latestGenerationId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/ai/sessions/" + token)).andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.latestGeneration.id");
    }

    private String awaitGeneration(String generationId) throws Exception {
        for (int i = 0; i < POLL_ATTEMPTS; i++) {
            MvcResult result = mockMvc.perform(get("/ai/generations/" + generationId))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = result.getResponse().getContentAsString();
            String status = JsonPath.read(body, "$.status");
            if (AiGenerationStatus.READY.name().equals(status) || AiGenerationStatus.FAILED.name().equals(status)) {
                return body;
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
        throw new AssertionError("generation " + generationId + " did not finish");
    }

    /** Scripts one chat turn that completes the brief plus the plan its automatic generation needs. */
    private void queueReadyTurn(String reply) {
        Brief ready = new Brief(1, GROUP_SIZE, List.of(category.getSlug()), null, null, null,
                DayEdge.AFTERNOON, DayEdge.EVENING, null);
        llm.queueChat(chatTurn(reply, ready)).queuePlan(planDraft());
    }

    private PlanDraft planDraft() {
        return new PlanDraft(List.of(packageDraft(Tier.BASIC, BASIC_INDEX), packageDraft(Tier.MEDIUM, MEDIUM_INDEX),
                packageDraft(Tier.PREMIUM, PREMIUM_FIRST_INDEX, PREMIUM_SECOND_INDEX)));
    }

    private PlanDraft.PackageDraft packageDraft(Tier tier, int... indexes) {
        Slot[] slots = {Slot.AFTERNOON, Slot.EVENING, Slot.NIGHT};
        List<PlanDraft.ItemDraft> items = new ArrayList<>();
        for (int i = 0; i < indexes.length; i++) {
            items.add(new PlanDraft.ItemDraft(slots[i], null, activities.get(indexes[i]).getId(), "why"));
        }
        return new PlanDraft.PackageDraft(tier, tier.name(), "tagline", "description",
                List.of(new PlanDraft.DayDraft(1, "Day one", "summary", items)));
    }

    /** A generation row nothing will ever run; enough to make the "a job owns this chat" guards fire. */
    private AiGeneration queuedGeneration(String token) {
        AiGeneration generation = new AiGeneration();
        generation.setSession(sessionRepository.findByToken(UUID.fromString(token)).orElseThrow());
        generation.setStatus(AiGenerationStatus.QUEUED);
        generation.setBriefSnapshot(JsonCodec.write(Brief.empty()));
        generation.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return generationRepository.save(generation);
    }

    private static ChatTurnResult chatTurn(String reply, Brief update) {
        return new ChatTurnResult(reply, update, List.of(), LlmUsage.none());
    }

    /** JsonPath's provider may hand back a Double or a BigDecimal for the same money field; compare values. */
    private static BigDecimal money(String body, String path) {
        return new BigDecimal(((Number) JsonPath.read(body, path)).toString());
    }
}
