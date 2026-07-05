# Cart Vote Flow ("Let your mates vote") Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a second vote-session entry point: the initiator's Trip Builder cart becomes an upvote-only ballot friends vote on from a list; results render as a ranked tally card and annotate the Trip Builder itinerary.

**Architecture:** Extend the existing `VoteSession` aggregate with a `voteMode` discriminator (`QUIZ` | `CART`). CART sessions reuse tokens, expiry scheduler, early close, the `vote_activity_likes` table, and result freezing. New: `POST /vote/sessions/cart`, `GET /vote/sessions/{shareToken}/tally`, an upvote-only guard, and a full-ranking (no filter/no knapsack) result resolver for CART. Frontend adds a vote button + email mini-modal in Trip Builder, a list-voting page, a shared tally card, and vote-badge annotation of the itinerary.

**Tech Stack:** Spring Boot 4.0 / Java 25 / JPA / H2+Postgres; React 19 (CRA), Jest + RTL.

**Spec:** `docs/superpowers/specs/2026-07-05-cart-vote-flow-design.md`

## Global Constraints

- Google Java Style: no wildcard imports; always `@Override`; braces on all `if/else/for/while`; one variable per declaration; K&R braces.
- Backend test style: `expected`-prefixed variables for values in both arrange and assert; DTOs built inline; JUnit 5 + AssertJ; `@SpringBootTest @Transactional @Import(TestSecurityConfig.class)` for service tests against H2.
- Frontend: CRA jest has `resetMocks: true` — set mock implementations **inside `beforeEach`/tests**, never only at module scope.
- Stay-in-flow rule: activity details in vote flows open in `ActivityPreviewModal`, never navigate away.
- Button label (user-approved copy): **"Let your mates vote"**. Modal title: "Let your mates vote". Modal helper: "We'll email you a private link to manage the vote. Voting closes automatically after 24 hours."
- localStorage keys: existing `myhive-manager-{shareToken}`, `myhive-initiator-{shareToken}`, `myhive-voted-{shareToken}`, `myhive.voterToken`; new `myhive-trip-vote-session` (shareToken of the CART session created from the current cart).
- The QUIZ flow must not change behavior: every existing test keeps passing untouched (except mechanical constructor-arity updates in Task 1).
- No email-template changes: the invite URL already targets `/vote/{shareToken}/activities` and the result email already targets `/destination/{slug}?tab=trip-builder&voteSession={token}` — both are exactly right for CART.
- Backend test runs: `cd myhive-backend && ./gradlew test --tests '<pattern>'`. Frontend: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=<pattern>`.

---

### Task 1: `VoteMode` enum, entity column, `voteMode` in `VoteSessionResponse`

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/model/VoteMode.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/entity/VoteSession.java` (add field after `budget`)
- Modify: `myhive-backend/src/main/java/com/myhive/backend/dto/VoteSessionResponse.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java:538-550` (`toResponse`)
- Modify: `myhive-backend/src/test/java/com/myhive/backend/controller/VoteSessionControllerTest.java` (constructor arity)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionVoteModeTest.java`

**Interfaces:**
- Produces: `enum VoteMode { QUIZ, CART }` in `com.myhive.backend.model`; `VoteSession.getVoteMode()/setVoteMode(VoteMode)` defaulting to `QUIZ`; `VoteSessionResponse` gains trailing `String voteMode` constructor arg.

- [ ] **Step 1: Write the failing test**

```java
package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteSessionVoteModeTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private DestinationRepository destinationRepository;

    @Test
    void getSession_exposesQuizVoteModeByDefault() {
        String expectedVoteMode = "QUIZ";

        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail("organiser@example.com");
        session.setNumberOfTravelers(2);
        session.setStartDate(LocalDate.of(2026, 8, 1));
        session.setEndDate(LocalDate.of(2026, 8, 3));
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setExpiresAt(LocalDateTime.now().plusHours(24));
        voteSessionRepository.save(session);

        VoteSessionResponse response = voteSessionService.getSession(session.getShareToken());

        assertThat(response.getVoteMode()).isEqualTo(expectedVoteMode);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionVoteModeTest'`
Expected: COMPILE FAILURE — `getVoteMode()` does not exist on `VoteSessionResponse`.

- [ ] **Step 3: Implement**

Create `myhive-backend/src/main/java/com/myhive/backend/model/VoteMode.java`:

```java
package com.myhive.backend.model;

public enum VoteMode {
    QUIZ,
    CART
}
```

In `VoteSession.java`, add after the `budget` field (import `com.myhive.backend.model.VoteMode`):

```java
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    // columnDefinition carries a DEFAULT so ddl-auto=update can add this NOT NULL column to the
    // already-populated vote_sessions table in prod; every pre-existing session is quiz-driven.
    @Column(name = "vote_mode", nullable = false, length = 20,
            columnDefinition = "varchar(20) not null default 'QUIZ'")
    private VoteMode voteMode = VoteMode.QUIZ;
```

In `VoteSessionResponse.java`, add a trailing field:

```java
    private UUID managerToken;
    private String voteMode;
```

In `VoteSessionService.toResponse(VoteSession, long, UUID)` append the arg:

```java
        return new VoteSessionResponse(
                session.getShareToken(),
                session.getDestination().getName(),
                session.getDestination().getSlug(),
                session.getStatus().name(),
                expiresAt,
                participantCount,
                travelers,
                managerToken,
                session.getVoteMode().name());
```

Fix compile errors at every other `new VoteSessionResponse(` call site (search the repo; known sites are in `VoteSessionControllerTest`) by appending `"QUIZ"` as the last argument.

- [ ] **Step 4: Run tests**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionVoteModeTest' --tests '*VoteSessionControllerTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A myhive-backend
git commit -m "feat(vote): add voteMode discriminator (QUIZ/CART) to vote sessions"
```

---

### Task 2: `POST /vote/sessions/cart`

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/VoteSessionCartCreateRequest.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java` (new `createCartSession`; extract `loadAndValidateDestinationActivities`, `persistBallot`, `sendVoteCreatedConfirmationQuietly` and reuse them from `createSession`)
- Modify: `myhive-backend/src/main/java/com/myhive/backend/controller/VoteSessionController.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionCartCreateTest.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/controller/VoteSessionControllerTest.java` (one new test)

**Interfaces:**
- Consumes: `VoteMode` (Task 1).
- Produces: `VoteSessionResponse createCartSession(VoteSessionCartCreateRequest)`; HTTP `POST /vote/sessions/cart` → 201 with `managerToken` + `voteMode: "CART"`. Request body: `{destinationId: UUID, initiatorEmail: String, numberOfTravelers: int, startDate: ISO date, endDate: ISO date, activityIds: UUID[]}`.

- [ ] **Step 1: Write the failing tests**

```java
package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteSessionCartCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.model.VoteMode;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteSessionActivityRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteSessionCartCreateTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteSessionActivityRepository voteSessionActivityRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void createCartSession_persistsCartSessionWithBallotInCartOrder() {
        String expectedFirstName = "Bar Crawl";
        String expectedSecondName = "Karting";
        BigDecimal expectedFirstPrice = new BigDecimal("45.00");

        Destination prague = newDestination("Prague");
        Activity barCrawl = newActivity(prague, expectedFirstName, expectedFirstPrice);
        Activity karting = newActivity(prague, expectedSecondName, new BigDecimal("60.00"));

        VoteSessionCartCreateRequest request =
                cartRequest(prague.getId(), List.of(barCrawl.getId(), karting.getId()));

        VoteSessionResponse response = voteSessionService.createCartSession(request);

        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        List<VoteSessionActivity> ballot =
                voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId());

        assertThat(session.getVoteMode()).isEqualTo(VoteMode.CART);
        assertThat(session.getStatus()).isEqualTo(VoteSessionStatus.ACTIVE);
        assertThat(session.getBudget()).isNull();
        assertThat(response.getManagerToken()).isNotNull();
        assertThat(response.getVoteMode()).isEqualTo("CART");
        assertThat(ballot).hasSize(2);
        assertThat(ballot.get(0).getActivityName()).isEqualTo(expectedFirstName);
        assertThat(ballot.get(0).getPrice()).isEqualByComparingTo(expectedFirstPrice);
        assertThat(ballot.get(1).getActivityName()).isEqualTo(expectedSecondName);
    }

    @Test
    void createCartSession_allowsActivityWithoutCategories() {
        // Unlike the quiz flow there is no quiz-category eligibility check.
        Destination prague = newDestination("Prague");
        Activity uncategorised = newActivity(prague, "Mystery Tour", new BigDecimal("30.00"));

        VoteSessionResponse response = voteSessionService.createCartSession(
                cartRequest(prague.getId(), List.of(uncategorised.getId())));

        assertThat(response.getShareToken()).isNotNull();
    }

    @Test
    void createCartSession_dedupesRepeatedActivityIds() {
        Destination prague = newDestination("Prague");
        Activity barCrawl = newActivity(prague, "Bar Crawl", new BigDecimal("45.00"));

        VoteSessionResponse response = voteSessionService.createCartSession(
                cartRequest(prague.getId(), List.of(barCrawl.getId(), barCrawl.getId())));

        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        assertThat(voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId())).hasSize(1);
    }

    @Test
    void createCartSession_rejectsActivityFromOtherDestination_400() {
        Destination prague = newDestination("Prague");
        Destination berlin = newDestination("Berlin");
        Activity berlinActivity = newActivity(berlin, "Techno Tour", new BigDecimal("50.00"));

        VoteSessionCartCreateRequest request =
                cartRequest(prague.getId(), List.of(berlinActivity.getId()));

        assertThatThrownBy(() -> voteSessionService.createCartSession(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not belong to destination");
    }

    @Test
    void createCartSession_rejectsUnknownActivity_400() {
        Destination prague = newDestination("Prague");
        UUID unknownId = UUID.randomUUID();

        VoteSessionCartCreateRequest request = cartRequest(prague.getId(), List.of(unknownId));

        assertThatThrownBy(() -> voteSessionService.createCartSession(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void createCartSession_rejectsEndDateBeforeStartDate_400() {
        Destination prague = newDestination("Prague");
        Activity barCrawl = newActivity(prague, "Bar Crawl", new BigDecimal("45.00"));

        VoteSessionCartCreateRequest request = cartRequest(prague.getId(), List.of(barCrawl.getId()));
        request.setStartDate(LocalDate.of(2026, 8, 10));
        request.setEndDate(LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> voteSessionService.createCartSession(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("endDate must be on or after startDate");
    }

    private VoteSessionCartCreateRequest cartRequest(UUID destinationId, List<UUID> activityIds) {
        VoteSessionCartCreateRequest request = new VoteSessionCartCreateRequest();
        request.setDestinationId(destinationId);
        request.setInitiatorEmail("initiator+" + UUID.randomUUID() + "@example.com");
        request.setNumberOfTravelers(4);
        request.setStartDate(LocalDate.of(2026, 8, 1));
        request.setEndDate(LocalDate.of(2026, 8, 3));
        request.setActivityIds(activityIds);
        return request;
    }

    private Destination newDestination(String name) {
        Destination destination = new Destination();
        destination.setName(name);
        return destinationRepository.save(destination);
    }

    private Activity newActivity(Destination destination, String name, BigDecimal price) {
        Activity activity = new Activity();
        activity.setDestination(destination);
        activity.setName(name);
        activity.setPrice(price);
        activity.setCategories(new HashSet<>());
        return activityRepository.saveAndFlush(activity);
    }
}
```

Add to `VoteSessionControllerTest.java` (uses the file's existing mocked service + MockMvc):

```java
    @Test
    void createCartSession_returns201WithManagerToken() throws Exception {
        UUID expectedToken = UUID.randomUUID();
        UUID expectedManagerToken = UUID.randomUUID();
        VoteSessionResponse response = new VoteSessionResponse(
                expectedToken, "Prague", "prague", "ACTIVE",
                java.time.Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS), 0L, 4,
                expectedManagerToken, "CART");

        when(voteSessionService.createCartSession(any())).thenReturn(response);

        String requestJson = """
                {
                    "destinationId": "%s",
                    "initiatorEmail": "alice@example.com",
                    "numberOfTravelers": 4,
                    "startDate": "2026-08-01",
                    "endDate": "2026-08-03",
                    "activityIds": ["%s"]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/vote/sessions/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.managerToken").value(expectedManagerToken.toString()))
                .andExpect(jsonPath("$.voteMode").value("CART"));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionCartCreateTest' --tests '*VoteSessionControllerTest'`
Expected: COMPILE FAILURE — `VoteSessionCartCreateRequest` / `createCartSession` do not exist.

- [ ] **Step 3: Implement**

Create `VoteSessionCartCreateRequest.java`:

```java
package com.myhive.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class VoteSessionCartCreateRequest {
    @NotNull private UUID destinationId;
    @NotNull @Email private String initiatorEmail;
    @NotNull @Min(1) @Max(50) private Integer numberOfTravelers;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;

    @NotEmpty(message = "activityIds must not be empty")
    @Size(max = 50, message = "activityIds may not exceed 50")
    private List<UUID> activityIds;
}
```

In `VoteSessionService`: import `VoteMode` and `VoteSessionCartCreateRequest`, then add the new method and helpers, and refactor `createSession` to reuse them.

```java
    @Transactional
    public VoteSessionResponse createCartSession(VoteSessionCartCreateRequest request) {
        Destination destination = destinationRepository.findById(request.getDestinationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found"));

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }

        List<UUID> activityIds = new ArrayList<>(new LinkedHashSet<>(request.getActivityIds()));
        Map<UUID, Activity> activitiesById = loadAndValidateDestinationActivities(destination, activityIds);

        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail(request.getInitiatorEmail());
        session.setNumberOfTravelers(request.getNumberOfTravelers());
        session.setStartDate(request.getStartDate());
        session.setEndDate(request.getEndDate());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setVoteMode(VoteMode.CART);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(24));
        session = voteSessionRepository.save(session);

        persistBallot(session, activityIds, activitiesById);
        sendVoteCreatedConfirmationQuietly(session);

        // A brand-new session has no voters yet.
        return toResponse(session, 0, session.getManagerToken());
    }
```

Extract these private helpers and make the existing code use them:

```java
    private Map<UUID, Activity> loadAndValidateDestinationActivities(Destination destination,
                                                                     List<UUID> activityIds) {
        Map<UUID, Activity> byId = activityRepository.findAllById(activityIds).stream()
                .collect(Collectors.toMap(Activity::getId, a -> a));
        for (UUID id : activityIds) {
            Activity activity = byId.get(id);
            if (activity == null) {
                throw new BadRequestException("activityId " + id + " does not exist");
            }
            if (!activity.getDestination().getId().equals(destination.getId())) {
                throw new BadRequestException(
                        "activityId " + id + " does not belong to destination " + destination.getId());
            }
        }
        return byId;
    }

    private void persistBallot(VoteSession session, List<UUID> activityIds,
                               Map<UUID, Activity> activitiesById) {
        int sortOrder = 0;
        for (UUID activityId : activityIds) {
            Activity activity = activitiesById.get(activityId);
            VoteSessionActivity row = new VoteSessionActivity();
            row.setSession(session);
            row.setActivity(activity);
            row.setActivityName(activity.getName());
            row.setPrice(activity.getPrice());
            row.setSortOrder(sortOrder++);
            voteSessionActivityRepository.save(row);
        }
    }

    private void sendVoteCreatedConfirmationQuietly(VoteSession session) {
        if (!emailEnabled) {
            return;
        }
        try {
            emailService.sendVoteCreatedConfirmation(session, frontendUrl);
        } catch (EmailSendException e) {
            // A failed confirmation email must never fail session creation — log and move on.
            log.error("Failed to send vote-created confirmation for session {}: {}",
                    session.getId(), e.getMessage(), e);
        }
    }
```

Refactor inside the existing methods (no behavior change):
- `loadAndValidateCuratedActivities` (service lines 205-229) now starts with `Map<UUID, Activity> byId = loadAndValidateDestinationActivities(destination, activityIds);` and keeps only its category-eligibility loop.
- In `createSession`: replace the inline ballot loop (lines 122-132) with `persistBallot(session, request.getActivityIds(), activitiesById);` and the inline email block (lines 145-153) with `sendVoteCreatedConfirmationQuietly(session);`.

Add to `VoteSessionController`:

```java
    @PostMapping("/cart")
    @ResponseStatus(HttpStatus.CREATED)
    public VoteSessionResponse createCartSession(@Valid @RequestBody VoteSessionCartCreateRequest request) {
        return voteSessionService.createCartSession(request);
    }
```

(plus the `VoteSessionCartCreateRequest` import).

- [ ] **Step 4: Run tests**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionCartCreateTest' --tests '*VoteSessionControllerTest' --tests '*VoteSessionCreateSessionTest'`
Expected: PASS (including untouched quiz-flow create tests).

- [ ] **Step 5: Commit**

```bash
git add -A myhive-backend
git commit -m "feat(vote): cart-seeded vote sessions via POST /vote/sessions/cart"
```

---

### Task 3: Upvote-only guard for CART votes

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java` (`castVote` ~line 295, `castVotes` ~line 324)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionCartVotingTest.java`

**Interfaces:**
- Consumes: `createCartSession` (Task 2).
- Produces: CART sessions reject any `liked=false` vote with `BadRequestException("This vote session accepts upvotes only")`; QUIZ behavior unchanged.

- [ ] **Step 1: Write the failing tests**

```java
package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteBatchRequest;
import com.myhive.backend.dto.VoteRequest;
import com.myhive.backend.dto.VoteSessionCartCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteSessionCartVotingTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void castVote_rejectsDownvoteOnCartSession_400() {
        Destination prague = newDestination("Prague");
        Activity barCrawl = newActivity(prague, "Bar Crawl");
        VoteSessionResponse session = createCartSession(prague, barCrawl);

        VoteRequest downvote = new VoteRequest();
        downvote.setVoterToken(UUID.randomUUID());
        downvote.setActivityId(barCrawl.getId());
        downvote.setLiked(false);

        assertThatThrownBy(() -> voteSessionService.castVote(session.getShareToken(), downvote))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("upvotes only");
    }

    @Test
    void castVotes_rejectsBatchContainingDownvote_400() {
        Destination prague = newDestination("Prague");
        Activity barCrawl = newActivity(prague, "Bar Crawl");
        Activity karting = newActivity(prague, "Karting");
        VoteSessionResponse session = createCartSession(prague, barCrawl, karting);

        VoteBatchRequest batch = batch(UUID.randomUUID(),
                vote(barCrawl.getId(), true), vote(karting.getId(), false));

        assertThatThrownBy(() -> voteSessionService.castVotes(session.getShareToken(), batch))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("upvotes only");
    }

    @Test
    void castVotes_acceptsUpvoteBatchOnCartSession() {
        long expectedParticipants = 1L;

        Destination prague = newDestination("Prague");
        Activity barCrawl = newActivity(prague, "Bar Crawl");
        VoteSessionResponse session = createCartSession(prague, barCrawl);

        VoteBatchRequest batch = batch(UUID.randomUUID(), vote(barCrawl.getId(), true));

        assertThatCode(() -> voteSessionService.castVotes(session.getShareToken(), batch))
                .doesNotThrowAnyException();
        assertThat(voteSessionService.getParticipantCount(session.getShareToken()))
                .isEqualTo(expectedParticipants);
    }

    private VoteSessionResponse createCartSession(Destination destination, Activity... activities) {
        VoteSessionCartCreateRequest request = new VoteSessionCartCreateRequest();
        request.setDestinationId(destination.getId());
        request.setInitiatorEmail("initiator@example.com");
        request.setNumberOfTravelers(4);
        request.setStartDate(LocalDate.of(2026, 8, 1));
        request.setEndDate(LocalDate.of(2026, 8, 3));
        request.setActivityIds(List.of(activities).stream().map(Activity::getId).toList());
        return voteSessionService.createCartSession(request);
    }

    private VoteBatchRequest batch(UUID voterToken, VoteBatchRequest.VoteItem... items) {
        VoteBatchRequest request = new VoteBatchRequest();
        request.setVoterToken(voterToken);
        request.setVotes(List.of(items));
        return request;
    }

    private VoteBatchRequest.VoteItem vote(UUID activityId, boolean liked) {
        VoteBatchRequest.VoteItem item = new VoteBatchRequest.VoteItem();
        item.setActivityId(activityId);
        item.setLiked(liked);
        return item;
    }

    private Destination newDestination(String name) {
        Destination destination = new Destination();
        destination.setName(name);
        return destinationRepository.save(destination);
    }

    private Activity newActivity(Destination destination, String name) {
        Activity activity = new Activity();
        activity.setDestination(destination);
        activity.setName(name);
        activity.setPrice(new BigDecimal("45.00"));
        activity.setCategories(new HashSet<>());
        return activityRepository.saveAndFlush(activity);
    }
}
```

Note: check `VoteRequest`'s actual setters before finalizing (`myhive-backend/src/main/java/com/myhive/backend/dto/VoteRequest.java`) — adjust the arrange code if field names differ.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionCartVotingTest'`
Expected: FAIL — the two rejection tests get no exception (guard missing); the accept test passes.

- [ ] **Step 3: Implement**

In `castVote`, directly after the `Session is no longer active` check:

```java
        if (session.getVoteMode() == VoteMode.CART && !request.getLiked()) {
            throw new BadRequestException("This vote session accepts upvotes only");
        }
```

In `castVotes`, directly after the `Session is no longer active` check:

```java
        if (session.getVoteMode() == VoteMode.CART
                && request.getVotes().stream().anyMatch(item -> !item.getLiked())) {
            throw new BadRequestException("This vote session accepts upvotes only");
        }
```

- [ ] **Step 4: Run tests**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionCartVotingTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A myhive-backend
git commit -m "feat(vote): reject downvotes on cart vote sessions"
```

---

### Task 4: `processSession` CART ranking

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java:455-515` (`processSession` → branch; extract `freezeQuizWinners` / `freezeCartRanking`)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionCartProcessTest.java`

**Interfaces:**
- Consumes: Tasks 1-3.
- Produces: for CART sessions `processSession` freezes **all** ballot activities into `vote_session_result_activities` ordered by like count desc, ties by original cart order (`sortOrder` asc). QUIZ path byte-for-byte identical in behavior (score>0 filter, featuredWeight, budget knapsack).

- [ ] **Step 1: Write the failing tests**

```java
package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteBatchRequest;
import com.myhive.backend.dto.VoteSessionCartCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.repository.VoteSessionResultActivityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// No class-level @Transactional: processSession uses REQUIRES_NEW, and results
// must be visible after it commits. Clean up via unique data per test (H2 create-drop).
@SpringBootTest
@Import(TestSecurityConfig.class)
class VoteSessionCartProcessTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteSessionResultActivityRepository resultActivityRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void processSession_cart_freezesFullRankingByLikesDescThenCartOrder() {
        Destination prague = newDestination("Prague");
        Activity first = newActivity(prague, "Bar Crawl");     // 2 votes
        Activity second = newActivity(prague, "Karting");      // 0 votes
        Activity third = newActivity(prague, "Shooting");      // 1 vote
        VoteSessionResponse created = createCartSession(prague, first, second, third);

        castUpvotes(created.getShareToken(), List.of(first.getId(), third.getId()));
        castUpvotes(created.getShareToken(), List.of(first.getId()));

        VoteSession session = voteSessionRepository.findByShareToken(created.getShareToken()).orElseThrow();
        voteSessionService.processSession(session);

        VoteSession processed = voteSessionRepository.findByShareToken(created.getShareToken()).orElseThrow();
        List<VoteSessionResultActivity> results =
                resultActivityRepository.findBySessionIdOrderBySortOrder(processed.getId());

        assertThat(processed.getStatus()).isEqualTo(VoteSessionStatus.COMPLETED);
        // All three ballot rows survive — zero-vote activities included, ranked last.
        assertThat(results).extracting(r -> r.getActivity().getId())
                .containsExactly(first.getId(), third.getId(), second.getId());
    }

    @Test
    void processSession_cart_breaksTiesByCartOrder() {
        Destination prague = newDestination("Prague");
        Activity first = newActivity(prague, "Bar Crawl");
        Activity second = newActivity(prague, "Karting");
        VoteSessionResponse created = createCartSession(prague, first, second);

        castUpvotes(created.getShareToken(), List.of(first.getId(), second.getId()));

        VoteSession session = voteSessionRepository.findByShareToken(created.getShareToken()).orElseThrow();
        voteSessionService.processSession(session);

        List<VoteSessionResultActivity> results = resultActivityRepository
                .findBySessionIdOrderBySortOrder(
                        voteSessionRepository.findByShareToken(created.getShareToken()).orElseThrow().getId());

        assertThat(results).extracting(r -> r.getActivity().getId())
                .containsExactly(first.getId(), second.getId());
    }

    private void castUpvotes(UUID shareToken, List<UUID> activityIds) {
        VoteBatchRequest batch = new VoteBatchRequest();
        batch.setVoterToken(UUID.randomUUID());
        batch.setVotes(activityIds.stream().map(id -> {
            VoteBatchRequest.VoteItem item = new VoteBatchRequest.VoteItem();
            item.setActivityId(id);
            item.setLiked(true);
            return item;
        }).toList());
        voteSessionService.castVotes(shareToken, batch);
    }

    private VoteSessionResponse createCartSession(Destination destination, Activity... activities) {
        VoteSessionCartCreateRequest request = new VoteSessionCartCreateRequest();
        request.setDestinationId(destination.getId());
        request.setInitiatorEmail("initiator@example.com");
        request.setNumberOfTravelers(4);
        request.setStartDate(LocalDate.of(2026, 8, 1));
        request.setEndDate(LocalDate.of(2026, 8, 3));
        request.setActivityIds(Stream.of(activities).map(Activity::getId).toList());
        return voteSessionService.createCartSession(request);
    }

    private Destination newDestination(String name) {
        Destination destination = new Destination();
        destination.setName(name + "-" + UUID.randomUUID());
        return destinationRepository.save(destination);
    }

    private Activity newActivity(Destination destination, String name) {
        Activity activity = new Activity();
        activity.setDestination(destination);
        activity.setName(name);
        activity.setPrice(new BigDecimal("45.00"));
        activity.setCategories(new HashSet<>());
        return activityRepository.saveAndFlush(activity);
    }
}
```

Check `VoteSessionProcessSessionTest.java` first: if it runs `processSession` under class-level `@Transactional` successfully, mirror its transaction setup instead of the comment above.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionCartProcessTest'`
Expected: FAIL — zero-vote activity is filtered out by the QUIZ `score > 0` rule (`containsExactly` mismatch).

- [ ] **Step 3: Implement**

Restructure `processSession`; the QUIZ body moves unchanged into `freezeQuizWinners`:

```java
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSession(VoteSession session) {
        session = voteSessionRepository.findById(session.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Vote session not found"));

        List<VoteSessionActivity> curated =
                voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId());
        if (curated.isEmpty()) {
            // Legacy session pre-Plan-2: no curated list to resolve against.
            session.setStatus(VoteSessionStatus.COMPLETED);
            voteSessionRepository.save(session);
            return;
        }

        Map<UUID, ActivityVoteCount> counts =
                voteActivityLikeRepository.findVoteCountsBySessionId(session.getId()).stream()
                        .collect(Collectors.toMap(ActivityVoteCount::getActivityId, c -> c));

        if (session.getVoteMode() == VoteMode.CART) {
            freezeCartRanking(session, curated, counts);
        } else {
            freezeQuizWinners(session, curated, counts);
        }

        session.setStatus(VoteSessionStatus.COMPLETED);
        voteSessionRepository.save(session);

        if (emailEnabled) {
            List<VoteSessionResultActivity> results =
                    resultActivityRepository.findBySessionIdOrderBySortOrder(session.getId());
            emailService.sendVoteResult(session, results, frontendUrl);
        }
    }

    private void freezeCartRanking(VoteSession session, List<VoteSessionActivity> curated,
                                   Map<UUID, ActivityVoteCount> counts) {
        // Advisory ranking: every ballot activity is kept, ordered by like count;
        // ties resolve to the initiator's original cart order.
        List<VoteSessionActivity> ranked = curated.stream()
                .sorted(Comparator
                        .comparingLong((VoteSessionActivity row) -> likeCountOf(counts, row)).reversed()
                        .thenComparingInt(VoteSessionActivity::getSortOrder))
                .toList();
        int sortOrder = 0;
        for (VoteSessionActivity row : ranked) {
            VoteSessionResultActivity resultRow = new VoteSessionResultActivity();
            resultRow.setSession(session);
            resultRow.setActivity(row.getActivity());
            resultRow.setSortOrder(sortOrder++);
            resultActivityRepository.save(resultRow);
        }
        log.info("Processed cart vote session {} — {} activities ranked", session.getId(), sortOrder);
    }

    private long likeCountOf(Map<UUID, ActivityVoteCount> counts, VoteSessionActivity row) {
        ActivityVoteCount count = counts.get(row.getActivity().getId());
        return count == null ? 0 : count.getLikeCount();
    }

    private void freezeQuizWinners(VoteSession session, List<VoteSessionActivity> curated,
                                   Map<UUID, ActivityVoteCount> counts) {
        record Ranked(VoteSessionActivity row, long score, int featuredWeight) {}

        List<Ranked> ranked = curated.stream()
                .map(row -> {
                    ActivityVoteCount c = counts.get(row.getActivity().getId());
                    long like = c == null ? 0 : c.getLikeCount();
                    long skip = c == null ? 0 : c.getSkipCount();
                    return new Ranked(row, like - skip, row.getActivity().getFeaturedWeight());
                })
                .filter(r -> r.score() > 0)
                .sorted(Comparator
                        .comparingLong(Ranked::score).reversed()
                        .thenComparing(Comparator.comparingInt(Ranked::featuredWeight).reversed())
                        .thenComparing(r -> r.row().getActivity().getId()))
                .toList();

        BigDecimal travelers = BigDecimal.valueOf(session.getNumberOfTravelers());
        BigDecimal budget = session.getBudget();
        BigDecimal running = BigDecimal.ZERO;
        int sortOrder = 0;
        for (Ranked r : ranked) {
            BigDecimal groupCost = r.row().getPrice().multiply(travelers);
            if (budget != null && running.add(groupCost).compareTo(budget) > 0) {
                continue;   // skip-and-continue
            }
            VoteSessionResultActivity resultRow = new VoteSessionResultActivity();
            resultRow.setSession(session);
            resultRow.setActivity(r.row().getActivity());
            resultRow.setSortOrder(sortOrder++);
            resultActivityRepository.save(resultRow);
            running = running.add(groupCost);
        }
        log.info("Processed vote session {} — {} activities selected", session.getId(), sortOrder);
    }
```

(The `log.info` for the quiz path moves with the body; the completion/email block stays shared.)

- [ ] **Step 4: Run tests**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionCartProcessTest' --tests '*VoteSessionProcessSessionTest' --tests '*VoteSessionSchedulerTest'`
Expected: PASS — CART ranking works, QUIZ processing regression-free.

- [ ] **Step 5: Commit**

```bash
git add -A myhive-backend
git commit -m "feat(vote): freeze full advisory ranking for cart sessions on close"
```

---

### Task 5: `getResult` for CART — `voteMode`, `participantCount`, no suggestions

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/dto/VoteResultResponse.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java:370-414` (`getResult`)
- Test: add to `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionCartProcessTest.java`

**Interfaces:**
- Consumes: Task 4.
- Produces: `VoteResultResponse` gains trailing fields `String voteMode` and `long participantCount`; for CART sessions `suggestions` is `[]` and `budget`/`remaining` are null (cart sessions never set a budget).

- [ ] **Step 1: Write the failing test** (append to `VoteSessionCartProcessTest`)

```java
    @Test
    void getResult_cart_exposesVoteModeParticipantCountAndNoSuggestions() {
        long expectedParticipants = 2L;

        Destination prague = newDestination("Prague");
        Activity barCrawl = newActivity(prague, "Bar Crawl");
        VoteSessionResponse created = createCartSession(prague, barCrawl);

        castUpvotes(created.getShareToken(), List.of(barCrawl.getId()));
        castUpvotes(created.getShareToken(), List.of(barCrawl.getId()));

        VoteSession session = voteSessionRepository.findByShareToken(created.getShareToken()).orElseThrow();
        voteSessionService.processSession(session);

        VoteResultResponse result = voteSessionService.getResult(created.getShareToken());

        assertThat(result.getVoteMode()).isEqualTo("CART");
        assertThat(result.getParticipantCount()).isEqualTo(expectedParticipants);
        assertThat(result.getSuggestions()).isEmpty();
        assertThat(result.getBudget()).isNull();
        assertThat(result.getResult()).hasSize(1);
        assertThat(result.getResult().get(0).getLikeCount()).isEqualTo(2);
    }
```

(add `import com.myhive.backend.dto.VoteResultResponse;`)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionCartProcessTest'`
Expected: COMPILE FAILURE — `getVoteMode()` / `getParticipantCount()` missing on `VoteResultResponse`.

- [ ] **Step 3: Implement**

`VoteResultResponse.java` — append fields:

```java
    private LocalDate endDate;
    private String voteMode;
    private long participantCount;
```

`getResult` — replace the suggestions line and the return:

```java
        List<SuggestionDTO> suggestions = session.getVoteMode() == VoteMode.CART
                ? List.of()
                : voteSuggestionsService.buildSuggestions(session);

        long participantCount = voteActivityLikeRepository
                .countDistinctVoterTokensBySessionId(session.getId());

        return new VoteResultResponse(result, suggestions, session.getNumberOfTravelers(),
                totalPrice, budget, remaining,
                session.getDestination().getName(), session.getDestination().getSlug(),
                session.getStartDate(), session.getEndDate(),
                session.getVoteMode().name(), participantCount);
```

Fix any other `new VoteResultResponse(` call sites (search; tests may construct it) by appending `"QUIZ", 0L`.

- [ ] **Step 4: Run tests**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionCartProcessTest' --tests '*VoteSessionGetResultTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A myhive-backend
git commit -m "feat(vote): expose voteMode and participantCount in vote results"
```

---

### Task 6: `GET /vote/sessions/{shareToken}/tally`

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/VoteTallyResponse.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java` (new `getTally`)
- Modify: `myhive-backend/src/main/java/com/myhive/backend/controller/VoteSessionController.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionTallyTest.java`

**Interfaces:**
- Consumes: Tasks 1-3 (`likeCountOf` from Task 4).
- Produces: `VoteTallyResponse getTally(UUID shareToken, UUID voterToken, UUID managerToken)`; HTTP `GET /vote/sessions/{shareToken}/tally?voterToken=&managerToken=` → 200 `{status, expiresAt, participantCount, rows: [{activityId, name, price, likeCount}]}` sorted likes desc / cart order; 403 for strangers; 409 for QUIZ sessions.

- [ ] **Step 1: Write the failing tests**

```java
package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteBatchRequest;
import com.myhive.backend.dto.VoteSessionCartCreateRequest;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.dto.VoteTallyResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteSessionTallyTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void getTally_forbiddenForVoterWhoHasNotVoted_403() {
        Destination prague = newDestination();
        Activity barCrawl = newActivity(prague, "Bar Crawl");
        VoteSessionResponse session = createCartSession(prague, barCrawl);

        UUID strangerToken = UUID.randomUUID();

        assertThatThrownBy(() ->
                voteSessionService.getTally(session.getShareToken(), strangerToken, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void getTally_returnsSortedCountsForVoter() {
        Destination prague = newDestination();
        Activity barCrawl = newActivity(prague, "Bar Crawl");   // 0 votes, cart position 1
        Activity karting = newActivity(prague, "Karting");      // 1 vote, cart position 2
        VoteSessionResponse session = createCartSession(prague, barCrawl, karting);

        UUID voterToken = UUID.randomUUID();
        castUpvote(session.getShareToken(), voterToken, karting.getId());

        VoteTallyResponse tally = voteSessionService.getTally(session.getShareToken(), voterToken, null);

        assertThat(tally.getParticipantCount()).isEqualTo(1);
        assertThat(tally.getStatus()).isEqualTo("ACTIVE");
        assertThat(tally.getRows()).extracting(VoteTallyResponse.TallyRow::getName)
                .containsExactly("Karting", "Bar Crawl");
        assertThat(tally.getRows().get(0).getLikeCount()).isEqualTo(1);
        assertThat(tally.getRows().get(1).getLikeCount()).isZero();
    }

    @Test
    void getTally_allowedWithManagerTokenWithoutVoting() {
        Destination prague = newDestination();
        Activity barCrawl = newActivity(prague, "Bar Crawl");
        VoteSessionResponse session = createCartSession(prague, barCrawl);

        VoteTallyResponse tally = voteSessionService.getTally(
                session.getShareToken(), null, session.getManagerToken());

        assertThat(tally.getRows()).hasSize(1);
    }

    @Test
    void getTally_conflictForQuizSession_409() {
        Destination prague = newDestination();
        Category nightlife = newCategory();
        attachCategory(prague, nightlife);
        Activity barCrawl = newActivity(prague, "Bar Crawl");
        barCrawl.getCategories().add(nightlife);
        activityRepository.saveAndFlush(barCrawl);

        VoteSessionCreateRequest quizRequest = new VoteSessionCreateRequest();
        quizRequest.setDestinationId(prague.getId());
        quizRequest.setInitiatorEmail("organiser@example.com");
        quizRequest.setNumberOfTravelers(2);
        quizRequest.setStartDate(LocalDate.of(2026, 8, 1));
        quizRequest.setEndDate(LocalDate.of(2026, 8, 3));
        quizRequest.setVoterToken(UUID.randomUUID());
        quizRequest.setQuizResponses(List.of());
        quizRequest.setActivityIds(List.of(barCrawl.getId()));
        VoteSessionResponse quizSession = voteSessionService.createSession(quizRequest);

        assertThatThrownBy(() -> voteSessionService.getTally(
                quizSession.getShareToken(), null, quizSession.getManagerToken()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    private void castUpvote(UUID shareToken, UUID voterToken, UUID activityId) {
        VoteBatchRequest batch = new VoteBatchRequest();
        batch.setVoterToken(voterToken);
        VoteBatchRequest.VoteItem item = new VoteBatchRequest.VoteItem();
        item.setActivityId(activityId);
        item.setLiked(true);
        batch.setVotes(List.of(item));
        voteSessionService.castVotes(shareToken, batch);
    }

    private VoteSessionResponse createCartSession(Destination destination, Activity... activities) {
        VoteSessionCartCreateRequest request = new VoteSessionCartCreateRequest();
        request.setDestinationId(destination.getId());
        request.setInitiatorEmail("initiator@example.com");
        request.setNumberOfTravelers(4);
        request.setStartDate(LocalDate.of(2026, 8, 1));
        request.setEndDate(LocalDate.of(2026, 8, 3));
        request.setActivityIds(java.util.stream.Stream.of(activities).map(Activity::getId).toList());
        return voteSessionService.createCartSession(request);
    }

    private Destination newDestination() {
        Destination destination = new Destination();
        destination.setName("Prague");
        return destinationRepository.save(destination);
    }

    private Category newCategory() {
        Category category = new Category();
        category.setName("Nightlife");
        category.setSlug("nightlife");
        return categoryRepository.save(category);
    }

    private void attachCategory(Destination destination, Category category) {
        Set<Category> set = new HashSet<>(destination.getCategories());
        set.add(category);
        destination.setCategories(set);
        destinationRepository.saveAndFlush(destination);
    }

    private Activity newActivity(Destination destination, String name) {
        Activity activity = new Activity();
        activity.setDestination(destination);
        activity.setName(name);
        activity.setPrice(new BigDecimal("45.00"));
        activity.setCategories(new HashSet<>());
        return activityRepository.saveAndFlush(activity);
    }
}
```

Add to `VoteSessionControllerTest`:

```java
    @Test
    void getTally_returns200WithRows() throws Exception {
        UUID shareToken = UUID.randomUUID();
        VoteTallyResponse tally = new VoteTallyResponse("ACTIVE",
                java.time.Instant.now().plus(12, java.time.temporal.ChronoUnit.HOURS), 3L,
                List.of(new VoteTallyResponse.TallyRow(
                        UUID.randomUUID(), "Bar Crawl", new java.math.BigDecimal("45.00"), 2L)));

        when(voteSessionService.getTally(any(), any(), any())).thenReturn(tally);

        mockMvc.perform(get("/vote/sessions/{shareToken}/tally", shareToken)
                        .param("voterToken", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(3))
                .andExpect(jsonPath("$.rows[0].name").value("Bar Crawl"))
                .andExpect(jsonPath("$.rows[0].likeCount").value(2));
    }
```

(add imports for `VoteTallyResponse` and `List` as needed).

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionTallyTest'`
Expected: COMPILE FAILURE — `VoteTallyResponse` / `getTally` missing.

- [ ] **Step 3: Implement**

Create `VoteTallyResponse.java`:

```java
package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VoteTallyResponse {
    private String status;
    private Instant expiresAt;
    private long participantCount;
    private List<TallyRow> rows;

    @Getter
    @AllArgsConstructor
    public static class TallyRow {
        private UUID activityId;
        private String name;
        private BigDecimal price;
        private long likeCount;
    }
}
```

Service method (readOnly, class default):

```java
    public VoteTallyResponse getTally(UUID shareToken, UUID voterToken, UUID managerToken) {
        VoteSession session = findByShareToken(shareToken);
        if (session.getVoteMode() != VoteMode.CART) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Live tally is not available for this session");
        }
        boolean isManager = managerToken != null && managerToken.equals(session.getManagerToken());
        boolean hasVoted = voterToken != null && voteActivityLikeRepository
                .existsBySessionIdAndVoterToken(session.getId(), voterToken);
        if (!isManager && !hasVoted) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cast your vote to see the live tally");
        }

        List<VoteSessionActivity> curated = voteSessionActivityRepository
                .findBySessionIdOrderBySortOrder(session.getId());
        Map<UUID, ActivityVoteCount> counts = voteActivityLikeRepository
                .findVoteCountsBySessionId(session.getId()).stream()
                .collect(Collectors.toMap(ActivityVoteCount::getActivityId, c -> c));

        List<VoteTallyResponse.TallyRow> rows = curated.stream()
                .sorted(Comparator
                        .comparingLong((VoteSessionActivity row) -> likeCountOf(counts, row)).reversed()
                        .thenComparingInt(VoteSessionActivity::getSortOrder))
                .map(row -> new VoteTallyResponse.TallyRow(
                        row.getActivity().getId(),
                        row.getActivityName(),
                        row.getPrice(),
                        likeCountOf(counts, row)))
                .toList();

        long participantCount = voteActivityLikeRepository
                .countDistinctVoterTokensBySessionId(session.getId());

        return new VoteTallyResponse(session.getStatus().name(),
                session.getExpiresAt().toInstant(ZoneOffset.UTC), participantCount, rows);
    }
```

Controller:

```java
    @GetMapping("/{shareToken}/tally")
    public VoteTallyResponse getTally(@PathVariable UUID shareToken,
                                      @RequestParam(required = false) UUID voterToken,
                                      @RequestParam(required = false) UUID managerToken) {
        return voteSessionService.getTally(shareToken, voterToken, managerToken);
    }
```

(import `VoteTallyResponse`).

- [ ] **Step 4: Run the full backend suite**

Run: `cd myhive-backend && ./gradlew test`
Expected: PASS — all new and all pre-existing tests.

- [ ] **Step 5: Commit**

```bash
git add -A myhive-backend
git commit -m "feat(vote): live tally endpoint for cart vote sessions"
```

---

### Task 7: `voteApi.createCartSession` + `voteApi.getTally`

**Files:**
- Modify: `myhive-react-app/src/services/voteApi.js`
- Test: `myhive-react-app/src/services/voteApi.test.js` (extend, follow the file's existing `global.fetch` mock setup)

**Interfaces:**
- Produces: `voteApi.createCartSession({destinationId, initiatorEmail, numberOfTravelers, startDate, endDate, activityIds})` → session JSON (throws backend `message` on !ok); `voteApi.getTally(shareToken, {voterToken, managerToken})` → tally JSON (403 → `Error('Vote first to see the live tally')`).

- [ ] **Step 1: Write the failing tests** (append to `voteApi.test.js`, reusing its fetch-mock helpers; if the file defines a helper like `mockFetch(...)`, use it)

```js
describe('createCartSession', () => {
  it('POSTs the cart payload to /vote/sessions/cart', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ shareToken: 't-1', managerToken: 'm-1', voteMode: 'CART' }),
    });

    const payload = {
      destinationId: 'd-1',
      initiatorEmail: 'a@b.cz',
      numberOfTravelers: 4,
      startDate: '2026-08-01',
      endDate: '2026-08-03',
      activityIds: ['a-1', 'a-2'],
    };
    const session = await voteApi.createCartSession(payload);

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/vote/sessions/cart'),
      expect.objectContaining({ method: 'POST', body: JSON.stringify(payload) }),
    );
    expect(session.managerToken).toBe('m-1');
  });

  it('throws the backend message on failure', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      json: async () => ({ message: 'activityId x does not exist' }),
    });

    await expect(voteApi.createCartSession({ activityIds: [] }))
      .rejects.toThrow('activityId x does not exist');
  });
});

describe('getTally', () => {
  it('passes voterToken and managerToken as query params', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ participantCount: 3, rows: [] }),
    });

    await voteApi.getTally('t-1', { voterToken: 'v-1', managerToken: 'm-1' });

    const url = global.fetch.mock.calls[0][0];
    expect(url).toContain('/vote/sessions/t-1/tally?');
    expect(url).toContain('voterToken=v-1');
    expect(url).toContain('managerToken=m-1');
  });

  it('throws a friendly error on 403', async () => {
    global.fetch = jest.fn().mockResolvedValue({ ok: false, status: 403 });

    await expect(voteApi.getTally('t-1', { voterToken: 'v-1' }))
      .rejects.toThrow('Vote first to see the live tally');
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=voteApi`
Expected: FAIL — `createCartSession is not a function`.

- [ ] **Step 3: Implement** (add to the `voteApi` object in `voteApi.js`)

```js
  // Cart-seeded session creation (no quiz) — the ballot is the initiator's cart.
  async createCartSession({ destinationId, initiatorEmail, numberOfTravelers,
                            startDate, endDate, activityIds }) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/cart`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        destinationId, initiatorEmail, numberOfTravelers, startDate, endDate, activityIds,
      }),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.message || 'Failed to create vote session');
    }
    return response.json();
  },

  // Live tally (CART sessions): server requires that voterToken has voted, or a managerToken.
  async getTally(shareToken, { voterToken, managerToken } = {}) {
    const params = new URLSearchParams();
    if (voterToken) {
      params.set('voterToken', voterToken);
    }
    if (managerToken) {
      params.set('managerToken', managerToken);
    }
    const response = await fetch(
        `${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}/tally?${params}`);
    if (response.status === 403) throw new Error('Vote first to see the live tally');
    if (!response.ok) throw new Error('Failed to fetch tally');
    return response.json();
  },
```

- [ ] **Step 4: Run tests**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=voteApi`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/services/voteApi.js myhive-react-app/src/services/voteApi.test.js
git commit -m "feat(vote): voteApi createCartSession and getTally"
```

---

### Task 8: `VoteTallyCard` component

**Files:**
- Create: `myhive-react-app/src/components/vote/VoteTallyCard.js`
- Create: `myhive-react-app/src/components/vote/VoteTallyCard.css`
- Test: `myhive-react-app/src/components/vote/VoteTallyCard.test.js`

**Interfaces:**
- Produces: `<VoteTallyCard title? participantCount rows showPrices? />` where `rows: [{activityId, name, price, likeCount}]`. Bar fill = `likeCount / max(1, participantCount)`, capped at 100%.

- [ ] **Step 1: Write the failing tests**

```js
import { render, screen } from '@testing-library/react';
import VoteTallyCard from './VoteTallyCard';

const rows = [
  { activityId: 'a1', name: 'Bar Crawl', price: 45, likeCount: 8 },
  { activityId: 'a2', name: 'Karting', price: 60, likeCount: 4 },
];

test('renders the voter count and one row per activity', () => {
  render(<VoteTallyCard participantCount={9} rows={rows} />);
  expect(screen.getByText('9 mates have voted')).toBeInTheDocument();
  expect(screen.getByText('Bar Crawl')).toBeInTheDocument();
  expect(screen.getByText('8')).toBeInTheDocument();
  expect(screen.getByText('Karting')).toBeInTheDocument();
});

test('uses singular copy for one voter', () => {
  render(<VoteTallyCard participantCount={1} rows={rows} />);
  expect(screen.getByText('1 mate has voted')).toBeInTheDocument();
});

test('bar width is likeCount over participantCount', () => {
  const { container } = render(<VoteTallyCard participantCount={8} rows={rows} />);
  const fills = container.querySelectorAll('.vote-tally-fill');
  expect(fills[0].style.width).toBe('100%');
  expect(fills[1].style.width).toBe('50%');
});

test('shows prices only when showPrices is set', () => {
  const { rerender } = render(<VoteTallyCard participantCount={9} rows={rows} />);
  expect(screen.queryByText(/45/)).not.toBeInTheDocument();
  rerender(<VoteTallyCard participantCount={9} rows={rows} showPrices />);
  expect(screen.getByText(/45/)).toBeInTheDocument();
});
```

(If `formatPricePerPerson(45)` renders differently — check `src/utils/format.js` — adjust the last assertion to the exact string.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=VoteTallyCard`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement**

`VoteTallyCard.js`:

```jsx
import { formatPricePerPerson } from '../../utils/format';
import './VoteTallyCard.css';

// Ranked vote tally, visually derived from the homepage hero ".vote-card"
// (name + count + progress bar). Used on the waiting screen (live) and the
// result screen (frozen, with prices).
function VoteTallyCard({ title = 'Vote results', participantCount, rows, showPrices = false }) {
    const denominator = Math.max(1, participantCount);
    return (
        <div className="vote-tally-card">
            <div className="vote-tally-head">
                <span className="vote-tally-title">{title}</span>
                <span className="vote-tally-sub">
                    {participantCount} {participantCount === 1 ? 'mate has' : 'mates have'} voted
                </span>
            </div>
            <ul className="vote-tally-list">
                {rows.map(row => (
                    <li key={row.activityId} className="vote-tally-row">
                        <div className="vote-tally-row-top">
                            <span className="vote-tally-name">{row.name}</span>
                            {showPrices && (
                                <span className="vote-tally-price">{formatPricePerPerson(row.price)}</span>
                            )}
                            <span className="vote-tally-num">{row.likeCount}</span>
                        </div>
                        <span className="vote-tally-bar">
                            <span
                                className="vote-tally-fill"
                                style={{ width: `${Math.min(100, (row.likeCount / denominator) * 100)}%` }}
                            />
                        </span>
                    </li>
                ))}
            </ul>
        </div>
    );
}

export default VoteTallyCard;
```

`VoteTallyCard.css`:

```css
.vote-tally-card {
    max-width: 480px;
    margin: 1rem auto;
    padding: 1rem 1.25rem;
    border: 1px solid rgba(124, 108, 245, 0.25);
    border-radius: 12px;
    background: rgba(124, 108, 245, 0.06);
    text-align: left;
}

.vote-tally-head {
    display: flex;
    flex-direction: column;
    gap: 2px;
    margin-bottom: 0.75rem;
}

.vote-tally-title {
    font-weight: 700;
}

.vote-tally-sub {
    font-size: 0.85rem;
    opacity: 0.7;
}

.vote-tally-list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 0.6rem;
}

.vote-tally-row-top {
    display: flex;
    align-items: baseline;
    gap: 0.5rem;
}

.vote-tally-name {
    flex: 1;
    font-weight: 600;
}

.vote-tally-price {
    font-size: 0.8rem;
    opacity: 0.7;
}

.vote-tally-num {
    font-weight: 700;
}

.vote-tally-bar {
    display: block;
    height: 6px;
    margin-top: 4px;
    border-radius: 3px;
    background: rgba(124, 108, 245, 0.18);
    overflow: hidden;
}

.vote-tally-fill {
    display: block;
    height: 100%;
    border-radius: 3px;
    background: #7c6cf5;
}
```

- [ ] **Step 4: Run tests**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=VoteTallyCard`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/components/vote
git commit -m "feat(vote): VoteTallyCard ranked tally component"
```

---

### Task 9: `StartGroupVoteModal`

**Files:**
- Create: `myhive-react-app/src/components/vote/StartGroupVoteModal.js`
- Create: `myhive-react-app/src/components/vote/StartGroupVoteModal.css`
- Test: `myhive-react-app/src/components/vote/StartGroupVoteModal.test.js`

**Interfaces:**
- Consumes: `voteApi.createCartSession` (Task 7), `AppModal`.
- Produces: `<StartGroupVoteModal isOpen onClose destinationId activityIds numberOfTravelers startDate endDate />`. On success: writes `myhive-manager-{shareToken}`, `myhive-initiator-{shareToken}`, `myhive-trip-vote-session`; navigates to `/vote/{shareToken}/waiting`. Shows date inputs only when `startDate`/`endDate` props are empty.

- [ ] **Step 1: Write the failing tests**

```js
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import StartGroupVoteModal from './StartGroupVoteModal';
import voteApi from '../../services/voteApi';

jest.mock('../../services/voteApi', () => ({
  __esModule: true,
  default: { createCartSession: jest.fn() },
}));

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));

function renderModal(props = {}) {
  return render(
    <MemoryRouter>
      <StartGroupVoteModal
        isOpen
        onClose={jest.fn()}
        destinationId="d-1"
        activityIds={['a-1', 'a-2']}
        numberOfTravelers={4}
        startDate="2026-08-01"
        endDate="2026-08-03"
        {...props}
      />
    </MemoryRouter>,
  );
}

afterEach(() => {
  localStorage.clear();
});

test('rejects an invalid email without calling the API', async () => {
  renderModal();
  await userEvent.type(screen.getByLabelText('Your email'), 'not-an-email');
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));
  expect(screen.getByText('Email is invalid')).toBeInTheDocument();
  expect(voteApi.createCartSession).not.toHaveBeenCalled();
});

test('creates the session, stores tokens and navigates to waiting', async () => {
  voteApi.createCartSession.mockResolvedValue({ shareToken: 't-1', managerToken: 'm-1' });
  renderModal();

  await userEvent.type(screen.getByLabelText('Your email'), 'stag@example.com');
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));

  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/vote/t-1/waiting'));
  expect(voteApi.createCartSession).toHaveBeenCalledWith({
    destinationId: 'd-1',
    initiatorEmail: 'stag@example.com',
    numberOfTravelers: 4,
    startDate: '2026-08-01',
    endDate: '2026-08-03',
    activityIds: ['a-1', 'a-2'],
  });
  expect(localStorage.getItem('myhive-manager-t-1')).toBe('m-1');
  expect(localStorage.getItem('myhive-initiator-t-1')).toBe('true');
  expect(localStorage.getItem('myhive-trip-vote-session')).toBe('t-1');
});

test('shows date inputs when the trip has no dates yet and requires them', async () => {
  renderModal({ startDate: '', endDate: '' });
  await userEvent.type(screen.getByLabelText('Your email'), 'stag@example.com');
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));
  expect(screen.getByText('Trip dates are required')).toBeInTheDocument();
  expect(voteApi.createCartSession).not.toHaveBeenCalled();
});

test('shows the API error message on failure', async () => {
  voteApi.createCartSession.mockRejectedValue(new Error('activityId x does not exist'));
  renderModal();
  await userEvent.type(screen.getByLabelText('Your email'), 'stag@example.com');
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));
  expect(await screen.findByText('activityId x does not exist')).toBeInTheDocument();
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=StartGroupVoteModal`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement**

`StartGroupVoteModal.js`:

```jsx
import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import AppModal from '../AppModal';
import voteApi from '../../services/voteApi';
import './StartGroupVoteModal.css';

const EMAIL_RE = /\S+@\S+\.\S+/;

// One-field mini-modal that turns the current cart into a CART vote session.
// Date inputs appear only when the trip setup never captured dates (the
// vote_sessions table requires them).
function StartGroupVoteModal({isOpen, onClose, destinationId, activityIds,
                              numberOfTravelers, startDate, endDate}) {
    const navigate = useNavigate();
    const [email, setEmail] = useState('');
    const [voteStartDate, setVoteStartDate] = useState(startDate || '');
    const [voteEndDate, setVoteEndDate] = useState(endDate || '');
    const [errors, setErrors] = useState({});
    const [apiError, setApiError] = useState(null);
    const [submitting, setSubmitting] = useState(false);

    const needsDates = !startDate || !endDate;

    const validate = () => {
        const next = {};
        if (!email.trim()) {
            next.email = 'Email is required';
        } else if (!EMAIL_RE.test(email)) {
            next.email = 'Email is invalid';
        }
        if (needsDates) {
            if (!voteStartDate || !voteEndDate) {
                next.dates = 'Trip dates are required';
            } else if (voteEndDate < voteStartDate) {
                next.dates = 'End date must be on or after the start date';
            }
        }
        setErrors(next);
        return Object.keys(next).length === 0;
    };

    const handleCreate = async () => {
        if (submitting || !validate()) {
            return;
        }
        setSubmitting(true);
        setApiError(null);
        try {
            const session = await voteApi.createCartSession({
                destinationId,
                initiatorEmail: email.trim(),
                numberOfTravelers,
                startDate: needsDates ? voteStartDate : startDate,
                endDate: needsDates ? voteEndDate : endDate,
                activityIds,
            });
            localStorage.setItem(`myhive-manager-${session.shareToken}`, session.managerToken);
            localStorage.setItem(`myhive-initiator-${session.shareToken}`, 'true');
            localStorage.setItem('myhive-trip-vote-session', session.shareToken);
            navigate(`/vote/${session.shareToken}/waiting`);
        } catch (e) {
            setApiError(e.message || 'Failed to create the vote. Please try again.');
            setSubmitting(false);
        }
    };

    return (
        <AppModal
            isOpen={isOpen}
            onClose={onClose}
            title="Let your mates vote"
            contentClassName="start-vote-modal"
            footer={(
                <button
                    type="button"
                    className="btn btn--primary btn--full-width"
                    onClick={handleCreate}
                    disabled={submitting}
                >
                    {submitting ? 'Creating…' : 'Create vote'}
                </button>
            )}
        >
            <p className="start-vote-modal-sub">
                We&apos;ll email you a private link to manage the vote.
                Voting closes automatically after 24 hours.
            </p>
            <label htmlFor="start-vote-email">Your email</label>
            <input
                id="start-vote-email"
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                className={errors.email ? 'error' : ''}
            />
            {errors.email && <span className="error-message">{errors.email}</span>}
            {needsDates && (
                <>
                    <label htmlFor="start-vote-start-date">Trip dates</label>
                    <div className="start-vote-modal-dates">
                        <input
                            id="start-vote-start-date"
                            aria-label="Start date"
                            type="date"
                            value={voteStartDate}
                            onChange={e => setVoteStartDate(e.target.value)}
                        />
                        <input
                            id="start-vote-end-date"
                            aria-label="End date"
                            type="date"
                            value={voteEndDate}
                            onChange={e => setVoteEndDate(e.target.value)}
                        />
                    </div>
                    {errors.dates && <span className="error-message">{errors.dates}</span>}
                </>
            )}
            {apiError && <p className="error-message">{apiError}</p>}
        </AppModal>
    );
}

export default StartGroupVoteModal;
```

Note: the email `<label htmlFor>` must match the input id (`start-vote-email`) so `getByLabelText('Your email')` resolves.

`StartGroupVoteModal.css`:

```css
.start-vote-modal label {
    display: block;
    margin-top: 0.75rem;
    margin-bottom: 0.25rem;
    font-weight: 600;
}

.start-vote-modal input {
    width: 100%;
    padding: 0.5rem 0.75rem;
    border: 1px solid #ccc;
    border-radius: 8px;
}

.start-vote-modal input.error {
    border-color: #d33;
}

.start-vote-modal-sub {
    margin: 0 0 0.5rem;
    font-size: 0.9rem;
    opacity: 0.8;
}

.start-vote-modal-dates {
    display: flex;
    gap: 0.5rem;
}

.start-vote-modal .error-message {
    color: #d33;
    font-size: 0.85rem;
}
```

- [ ] **Step 4: Run tests**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=StartGroupVoteModal`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/components/vote
git commit -m "feat(vote): StartGroupVoteModal email mini-modal"
```

---

### Task 10: Trip Builder vote button

**Files:**
- Modify: `myhive-react-app/src/components/TripBuilder.js` (button in `.trip-actions` after the `confirm-btn` at line 433-435; modal at the bottom next to `ContactForm`)
- Modify: `myhive-react-app/src/components/TripBuilder.css` (button styles)
- Modify: `myhive-react-app/src/pages/DestinationPage.js:291` (pass `destinationSlug`)
- Test: `myhive-react-app/src/components/TripBuilder.test.js` (extend)

**Interfaces:**
- Consumes: `StartGroupVoteModal` (Task 9), `groupTripItems` (existing).
- Produces: "Let your mates vote" button — rendered when ≥1 standalone item; disabled with tooltip when standalone items span foreign destinations; opens the modal with `activityIds = standalone ids`.

- [ ] **Step 1: Write the failing tests** (append to `TripBuilder.test.js`; reuse its existing api/voteApi mocks and provider helpers if present, otherwise use this setup)

```js
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { TripContext } from '../context/TripContext';
import TripBuilder from './TripBuilder';

// (Reuse the file's existing jest.mock declarations for ../services/api,
//  ../services/voteApi and ../services/paymentApi.)

function renderTripBuilder(tripItems, { destinationSlug = 'prague' } = {}) {
  const state = {
    tripId: null,
    tripItems,
    tripTravelers: 4,
    tripStartDate: '2026-08-01',
    tripEndDate: '2026-08-03',
    tripBudget: null,
    tripSetupModalOpen: false,
    tripBuilderModalOpen: false,
  };
  return render(
    <MemoryRouter>
      <TripContext.Provider value={{ state, dispatch: jest.fn() }}>
        <TripBuilder destinationId="d-1" destinationSlug={destinationSlug} />
      </TripContext.Provider>
    </MemoryRouter>,
  );
}

describe('Let your mates vote button', () => {
  test('shows an enabled button when the cart has standalone activities', () => {
    renderTripBuilder([{ id: 'a-1', name: 'Bar Crawl', price: 45, destinationSlug: 'prague' }]);
    expect(screen.getByRole('button', { name: 'Let your mates vote' })).toBeEnabled();
  });

  test('hides the button when the cart only contains package items', () => {
    renderTripBuilder([
      { id: 'a-1', name: 'Karting', price: 50, packageId: 'p-1', packageName: 'Mayhem', packageDiscountPct: 10 },
    ]);
    expect(screen.queryByRole('button', { name: 'Let your mates vote' })).not.toBeInTheDocument();
  });

  test('disables the button when standalone items span another destination', () => {
    renderTripBuilder([
      { id: 'a-1', name: 'Bar Crawl', price: 45, destinationSlug: 'prague' },
      { id: 'a-2', name: 'Techno Tour', price: 50, destinationSlug: 'berlin' },
    ]);
    expect(screen.getByRole('button', { name: 'Let your mates vote' })).toBeDisabled();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=TripBuilder`
Expected: new tests FAIL (button absent); pre-existing tests still pass.

- [ ] **Step 3: Implement**

`DestinationPage.js:291`:

```jsx
        {tripBuilderActivated && <TripBuilder destinationId={destination?.id} destinationSlug={destination?.slug} />}
```

`TripBuilder.js`:
- Signature: `function TripBuilder({ destinationId, destinationSlug }) {`
- Import: `import StartGroupVoteModal from './vote/StartGroupVoteModal';`
- State: `const [showVoteModal, setShowVoteModal] = useState(false);`
- After the `const {standalone, groups: groupsArray} = groupTripItems(state.tripItems);` line add:

```js
  const hasForeignStandalone = !!destinationSlug
      && standalone.some(item => item.destinationSlug && item.destinationSlug !== destinationSlug);
  const canStartVote = standalone.length > 0 && !hasForeignStandalone;
```

- Inside `.trip-actions`, directly after the `confirm-btn` button (line 435):

```jsx
              <button
                  type="button"
                  className="btn btn--full-width start-vote-btn"
                  onClick={() => setShowVoteModal(true)}
                  disabled={!canStartVote}
                  title={hasForeignStandalone
                      ? 'Group voting works for one destination at a time — remove activities from other destinations first.'
                      : undefined}
              >
                Let your mates vote
              </button>
```

Wrap it in `{standalone.length > 0 && (...)}` so a packages-only cart shows no button.

- At the bottom, next to `ContactForm`:

```jsx
      <StartGroupVoteModal
          isOpen={showVoteModal}
          onClose={() => setShowVoteModal(false)}
          destinationId={destinationId}
          activityIds={standalone.map(item => item.id)}
          numberOfTravelers={travelers}
          startDate={state.tripStartDate}
          endDate={state.tripEndDate}
      />
```

`TripBuilder.css` — append:

```css
.start-vote-btn {
    margin-top: 0.5rem;
    background: transparent;
    border: 1px solid #7c6cf5;
    color: #7c6cf5;
}

.start-vote-btn:hover:not(:disabled) {
    background: rgba(124, 108, 245, 0.08);
}

.start-vote-btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}
```

- [ ] **Step 4: Run tests**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=TripBuilder`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/components/TripBuilder.js myhive-react-app/src/components/TripBuilder.css myhive-react-app/src/components/TripBuilder.test.js myhive-react-app/src/pages/DestinationPage.js
git commit -m "feat(vote): 'Let your mates vote' button on the itinerary panel"
```

---

### Task 11: `CartVoteList` + `ActivityVotePage` mode branch

**Files:**
- Create: `myhive-react-app/src/components/vote/CartVoteList.js`
- Create: `myhive-react-app/src/components/vote/CartVoteList.css`
- Modify: `myhive-react-app/src/pages/vote/ActivityVotePage.js`
- Test: `myhive-react-app/src/components/vote/CartVoteList.test.js`
- Test: `myhive-react-app/src/pages/vote/ActivityVotePage.test.js` (extend)

**Interfaces:**
- Consumes: `voteApi.castVotes`, `voteApi.getSession` (`voteMode` from Task 1), `ActivityPreviewModal({activity, link, onClose})`.
- Produces: `<CartVoteList shareToken activities voterToken />` — list voting UI, batch-submits `liked: true` only. `ActivityVotePage` renders it when `session.voteMode === 'CART'`, otherwise the existing `SwipeCard`.

- [ ] **Step 1: Write the failing tests**

`CartVoteList.test.js`:

```js
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import CartVoteList from './CartVoteList';
import voteApi from '../../services/voteApi';

jest.mock('../../services/voteApi', () => ({
  __esModule: true,
  default: { castVotes: jest.fn() },
}));

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));

const activities = [
  { id: 'a-1', name: 'Bar Crawl', price: 45, imageUrl: 'x.jpg', description: 'Pub tour of Prague' },
  { id: 'a-2', name: 'Karting', price: 60, imageUrl: 'y.jpg', description: 'Indoor karting' },
];

function renderList() {
  return render(
    <MemoryRouter>
      <CartVoteList shareToken="t-1" activities={activities} voterToken="v-1" />
    </MemoryRouter>,
  );
}

afterEach(() => {
  localStorage.clear();
});

test('submit is disabled until at least one activity is selected', async () => {
  renderList();
  expect(screen.getByRole('button', { name: /Submit vote/ })).toBeDisabled();
  await userEvent.click(screen.getAllByRole('button', { name: '♥ Vote' })[0]);
  expect(screen.getByRole('button', { name: /Submit vote/ })).toBeEnabled();
});

test('a second tap withdraws the vote', async () => {
  renderList();
  await userEvent.click(screen.getAllByRole('button', { name: '♥ Vote' })[0]);
  await userEvent.click(screen.getByRole('button', { name: '♥ Voted' }));
  expect(screen.getByRole('button', { name: /Submit vote/ })).toBeDisabled();
});

test('submits only upvotes, marks voted and navigates to waiting', async () => {
  voteApi.castVotes.mockResolvedValue();
  renderList();

  await userEvent.click(screen.getAllByRole('button', { name: '♥ Vote' })[0]);
  await userEvent.click(screen.getByRole('button', { name: /Submit vote/ }));

  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/vote/t-1/waiting'));
  expect(voteApi.castVotes).toHaveBeenCalledWith('t-1', {
    voterToken: 'v-1',
    votes: [{ activityId: 'a-1', liked: true }],
  });
  expect(localStorage.getItem('myhive-voted-t-1')).toBe('true');
});

test('info button opens the activity preview modal', async () => {
  renderList();
  await userEvent.click(screen.getByRole('button', { name: 'About Bar Crawl' }));
  expect(screen.getByText('Pub tour of Prague')).toBeInTheDocument();
});
```

`ActivityVotePage.test.js` — add (reuse the file's existing mock setup for `voteApi`; extend the mock object with `getSession`):

```js
test('renders the list voting UI for CART sessions', async () => {
  voteApi.getSession.mockResolvedValue({ voteMode: 'CART', status: 'ACTIVE' });
  voteApi.getActivities.mockResolvedValue([
    { id: 'a-1', name: 'Bar Crawl', price: 45 },
  ]);

  renderPage(); // the file's existing render helper for /vote/:shareToken/activities

  expect(await screen.findByRole('button', { name: /Submit vote/ })).toBeInTheDocument();
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern="CartVoteList|ActivityVotePage"`
Expected: FAIL — module not found / no Submit vote button.

- [ ] **Step 3: Implement**

`CartVoteList.js`:

```jsx
import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import voteApi from '../../services/voteApi';
import ActivityPreviewModal from '../ActivityPreviewModal';
import {formatPricePerPerson} from '../../utils/format';
import {pushEvent} from '../../utils/analytics';
import './CartVoteList.css';

// Upvote-only list ballot for CART sessions: tap ♥ on any activities you're up
// for (one vote each), then submit once. Details open in the preview modal —
// participants never leave the voting flow.
function CartVoteList({shareToken, activities, voterToken}) {
    const navigate = useNavigate();
    const [selected, setSelected] = useState(() => new Set());
    const [preview, setPreview] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState(null);

    const toggle = (activityId) => {
        setSelected(prev => {
            const next = new Set(prev);
            if (next.has(activityId)) {
                next.delete(activityId);
            } else {
                next.add(activityId);
            }
            return next;
        });
    };

    const handleSubmit = async () => {
        if (selected.size === 0 || submitting) {
            return;
        }
        setSubmitting(true);
        setError(null);
        try {
            await voteApi.castVotes(shareToken, {
                voterToken,
                votes: [...selected].map(activityId => ({activityId, liked: true})),
            });
            localStorage.setItem(`myhive-voted-${shareToken}`, 'true');
            pushEvent('vote_completed', {trip_id: shareToken, user_role: 'participant'});
            navigate(`/vote/${shareToken}/waiting`);
        } catch (e) {
            if (e.message === 'Session is full') {
                navigate(`/vote/${shareToken}/waiting`);
            } else {
                setError('Failed to submit your vote. Please try again.');
                setSubmitting(false);
            }
        }
    };

    const previewLink = preview && preview.slug && preview.destinationSlug
        ? `/destination/${preview.destinationSlug}/activity/${preview.slug}`
        : null;

    return (
        <div className="cart-vote-page">
            <h1 className="cart-vote-title">Which activities are you up for?</h1>
            <p className="cart-vote-subtitle">Tap ♥ on everything you like — one vote per activity.</p>
            <ul className="cart-vote-list">
                {activities.map(activity => {
                    const isSelected = selected.has(activity.id);
                    return (
                        <li
                            key={activity.id}
                            className={`cart-vote-row ${isSelected ? 'cart-vote-row--selected' : ''}`}
                        >
                            {activity.imageUrl && (
                                <img
                                    src={activity.imageUrl}
                                    alt={activity.name}
                                    className="cart-vote-image"
                                    loading="lazy"
                                />
                            )}
                            <div className="cart-vote-content">
                                <div className="cart-vote-name">{activity.name}</div>
                                <div className="cart-vote-price">{formatPricePerPerson(activity.price)}</div>
                            </div>
                            <button
                                type="button"
                                className="cart-vote-info-btn"
                                aria-label={`About ${activity.name}`}
                                onClick={() => setPreview(activity)}
                            >
                                i
                            </button>
                            <button
                                type="button"
                                className={`cart-vote-toggle ${isSelected ? 'cart-vote-toggle--on' : ''}`}
                                aria-pressed={isSelected}
                                onClick={() => toggle(activity.id)}
                            >
                                {isSelected ? '♥ Voted' : '♥ Vote'}
                            </button>
                        </li>
                    );
                })}
            </ul>
            {error && <p className="cart-vote-error">{error}</p>}
            <button
                type="button"
                className="cart-vote-submit"
                onClick={handleSubmit}
                disabled={selected.size === 0 || submitting}
            >
                {submitting ? 'Submitting…' : `Submit vote (${selected.size})`}
            </button>
            <ActivityPreviewModal activity={preview} link={previewLink} onClose={() => setPreview(null)}/>
        </div>
    );
}

export default CartVoteList;
```

`CartVoteList.css`:

```css
.cart-vote-page {
    max-width: 560px;
    margin: 0 auto;
    padding: 1.5rem 1rem 3rem;
}

.cart-vote-title {
    text-align: center;
    margin-bottom: 0.25rem;
}

.cart-vote-subtitle {
    text-align: center;
    opacity: 0.7;
    margin-bottom: 1.25rem;
}

.cart-vote-list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
}

.cart-vote-row {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.6rem;
    border: 1px solid rgba(0, 0, 0, 0.1);
    border-radius: 12px;
}

.cart-vote-row--selected {
    border-color: #7c6cf5;
    background: rgba(124, 108, 245, 0.06);
}

.cart-vote-image {
    width: 56px;
    height: 56px;
    object-fit: cover;
    border-radius: 8px;
    flex-shrink: 0;
}

.cart-vote-content {
    flex: 1;
    min-width: 0;
}

.cart-vote-name {
    font-weight: 600;
}

.cart-vote-price {
    font-size: 0.85rem;
    opacity: 0.7;
}

.cart-vote-info-btn {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    border: 1px solid rgba(0, 0, 0, 0.2);
    background: transparent;
    font-style: italic;
    font-weight: 700;
    cursor: pointer;
    flex-shrink: 0;
}

.cart-vote-toggle {
    padding: 0.4rem 0.8rem;
    border-radius: 999px;
    border: 1px solid #7c6cf5;
    background: transparent;
    color: #7c6cf5;
    font-weight: 600;
    cursor: pointer;
    white-space: nowrap;
    flex-shrink: 0;
}

.cart-vote-toggle--on {
    background: #7c6cf5;
    color: #fff;
}

.cart-vote-submit {
    display: block;
    width: 100%;
    margin-top: 1.25rem;
    padding: 0.75rem;
    border: none;
    border-radius: 10px;
    background: #7c6cf5;
    color: #fff;
    font-weight: 700;
    cursor: pointer;
}

.cart-vote-submit:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.cart-vote-error {
    color: #d33;
    text-align: center;
    margin-top: 0.75rem;
}
```

`ActivityVotePage.js` — inside `ActivityVoteContent`:

1. Import `CartVoteList`: `import CartVoteList from '../../components/vote/CartVoteList';`
2. Add session state and fetch:

```js
    const [session, setSession] = useState(null);
    const [sessionLoaded, setSessionLoaded] = useState(false);

    useEffect(() => {
        voteApi.getSession(shareToken)
            .then(setSession)
            .catch(() => {
                // The activities fetch surfaces load errors; a session-meta failure
                // just falls back to the default swipe UI.
            })
            .finally(() => setSessionLoaded(true));
    }, [shareToken]);
```

3. Change the loading guard to `if (loading || !sessionLoaded) return (<div className="vote-state">Loading activities...</div>);`
4. Immediately before the `SwipeCard` return:

```js
    if (session?.voteMode === 'CART') {
        return (
            <CartVoteList
                shareToken={shareToken}
                activities={activities}
                voterToken={voterToken}
            />
        );
    }
```

- [ ] **Step 4: Run tests**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern="CartVoteList|ActivityVotePage"`
Expected: PASS (including the existing swipe-flow tests).

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/components/vote myhive-react-app/src/pages/vote/ActivityVotePage.js myhive-react-app/src/pages/vote/ActivityVotePage.test.js
git commit -m "feat(vote): list voting UI for cart sessions"
```

---

### Task 12: `VoteWaitingPage` — live tally + CART navigation

**Files:**
- Modify: `myhive-react-app/src/pages/vote/VoteWaitingPage.js`
- Test: `myhive-react-app/src/pages/vote/VoteWaitingPage.test.js` (extend)

**Interfaces:**
- Consumes: `voteApi.getTally` (Task 7), `VoteTallyCard` (Task 8), `getOrCreateVoterToken`.
- Produces: CART sessions — tally card visible to voters and to the initiator (manager token, no vote needed), refreshed every 30 s; on COMPLETED, CART navigates to `/vote/{shareToken}/result` (never to the Trip Builder hydration URL).

- [ ] **Step 1: Write the failing tests** (extend `VoteWaitingPage.test.js`, reusing its existing `voteApi` mock and render helper; extend the mock object with `getTally`)

```js
test('CART session: shows the live tally to a voter', async () => {
  localStorage.setItem('myhive-voted-t-1', 'true');
  voteApi.getSession.mockResolvedValue({
    voteMode: 'CART', status: 'ACTIVE', destinationName: 'Prague', destinationSlug: 'prague',
    participantCount: 3, numberOfTravelers: 8,
    expiresAt: new Date(Date.now() + 3600_000).toISOString(),
  });
  voteApi.getTally.mockResolvedValue({
    status: 'ACTIVE', participantCount: 3,
    rows: [{ activityId: 'a-1', name: 'Bar Crawl', price: 45, likeCount: 3 }],
  });

  renderPage('t-1'); // the file's existing render helper

  expect(await screen.findByText('Bar Crawl')).toBeInTheDocument();
  expect(screen.getByText('3 mates have voted')).toBeInTheDocument();
});

test('CART session: completed poll navigates to the result page', async () => {
  voteApi.getSession.mockResolvedValue({
    voteMode: 'CART', status: 'COMPLETED', destinationSlug: 'prague',
    participantCount: 5, expiresAt: new Date().toISOString(),
  });

  renderPage('t-1');

  await waitFor(() =>
    expect(mockNavigate).toHaveBeenCalledWith('/vote/t-1/result', { replace: true }));
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=VoteWaitingPage`
Expected: FAIL — no tally rendered; COMPLETED navigates to the trip-builder URL instead.

- [ ] **Step 3: Implement** (in `VoteWaitingPage.js`)

1. Imports:

```js
import { useCallback, useEffect, useMemo, useState } from 'react';
import { getOrCreateVoterToken } from '../../utils/voterToken';
import VoteTallyCard from '../../components/vote/VoteTallyCard';
```

2. State: `const [tally, setTally] = useState(null);` and `const voterToken = useMemo(() => getOrCreateVoterToken(), []);`

3. In the session poll (line 53-60), replace the COMPLETED branch:

```js
                if (s.status === 'COMPLETED') {
                    if (s.voteMode === 'CART') {
                        // Cart votes never hydrate the Trip Builder — everyone sees
                        // the ranked results page instead.
                        navigate(`/vote/${shareToken}/result`, { replace: true });
                    } else if (s.destinationSlug) {
                        navigate(`/destination/${s.destinationSlug}?tab=trip-builder&voteSession=${shareToken}`,
                            { replace: true });
                    } else {
                        navigate(`/vote/${shareToken}/result`, { replace: true });
                    }
                }
```

4. Tally poll effect (after the session poll effect):

```js
    // Live tally (CART only): visible once you've voted, or to the initiator via
    // the manager token — they authored the list, so seeing votes can't bias them.
    const voteMode = session?.voteMode;
    useEffect(() => {
        if (voteMode !== 'CART' || (!hasVoted && !managerToken)) {
            return undefined;
        }
        let cancelled = false;
        const load = () => voteApi.getTally(shareToken, {
            voterToken: hasVoted ? voterToken : null,
            managerToken,
        }).then(t => {
            if (!cancelled) {
                setTally(t);
            }
        }).catch(() => {
            // Transient tally failure — keep the last tally, retry next tick.
        });
        load();
        const id = setInterval(load, 30_000);
        return () => {
            cancelled = true;
            clearInterval(id);
        };
    }, [voteMode, hasVoted, managerToken, shareToken, voterToken]);
```

5. In `handleClose` (line 99-112), branch the redirect the same way and add `session?.voteMode` to the `useCallback` deps:

```js
            .finally(() => {
                if (session?.voteMode === 'CART') {
                    navigate(`/vote/${shareToken}/result`);
                    return;
                }
                const destinationSlug = session?.destinationSlug;
                if (destinationSlug) {
                    navigate(`/destination/${destinationSlug}?tab=trip-builder&voteSession=${shareToken}`);
                } else {
                    navigate(`/vote/${shareToken}/result`);
                }
            });
```

6. Render the tally between the participants card (ends line 182) and the share label:

```jsx
            {tally && tally.rows.length > 0 && (
                <VoteTallyCard
                    title="Live results"
                    participantCount={tally.participantCount}
                    rows={tally.rows}
                />
            )}
```

- [ ] **Step 4: Run tests**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=VoteWaitingPage`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/pages/vote/VoteWaitingPage.js myhive-react-app/src/pages/vote/VoteWaitingPage.test.js
git commit -m "feat(vote): live tally and result-page redirect on the waiting screen for cart sessions"
```

---

### Task 13: `VoteResultPage` — CART branch

**Files:**
- Modify: `myhive-react-app/src/pages/vote/VoteResultPage.js`
- Test: `myhive-react-app/src/pages/vote/VoteResultPage.test.js` (extend)

**Interfaces:**
- Consumes: `VoteTallyCard` (Task 8); `getResult` now returns `voteMode` + `participantCount` (Task 5).
- Produces: CART results render the tally card with prices; no budget block, suggestions, or `PaymentActions`; initiator-only CTA "Back to Trip Builder" → `/destination/{slug}?tab=trip-builder&voteSession={shareToken}`.

- [ ] **Step 1: Write the failing tests** (extend `VoteResultPage.test.js`, reusing its mocks/render helper)

```js
const cartResult = {
  voteMode: 'CART',
  participantCount: 9,
  numberOfTravelers: 8,
  totalPrice: 105,
  budget: null,
  remaining: null,
  destinationName: 'Prague',
  destinationSlug: 'prague',
  suggestions: [],
  result: [
    { activityId: 'a-1', name: 'Bar Crawl', price: 45, likeCount: 8, skipCount: 0 },
    { activityId: 'a-2', name: 'Karting', price: 60, likeCount: 4, skipCount: 0 },
  ],
};

test('CART result renders the ranked tally without budget or payments', async () => {
  voteApi.getResult.mockResolvedValue(cartResult);

  renderPage('t-1');

  expect(await screen.findByText('Bar Crawl')).toBeInTheDocument();
  expect(screen.getByText('9 mates have voted')).toBeInTheDocument();
  expect(screen.queryByText('Budget')).not.toBeInTheDocument();
  expect(screen.queryByText(/prepayment/i)).not.toBeInTheDocument();
});

test('CART result shows Back to Trip Builder only for the initiator', async () => {
  voteApi.getResult.mockResolvedValue(cartResult);
  localStorage.setItem('myhive-manager-t-1', 'm-1');
  localStorage.setItem('myhive-initiator-t-1', 'true');

  renderPage('t-1');

  expect(await screen.findByRole('button', { name: 'Back to Trip Builder' })).toBeInTheDocument();
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=VoteResultPage`
Expected: FAIL — tally card absent, budget block rendered.

- [ ] **Step 3: Implement** (in `VoteResultPage.js`)

1. Import: `import VoteTallyCard from '../../components/vote/VoteTallyCard';`
2. After the `if (!data) { ... }` guard (line 127-129), insert the CART early return:

```jsx
    if (data.voteMode === 'CART') {
        return (
            <div className="result-page">
                <div className="result-page-inner">
                    <h1>The votes are in!</h1>
                    <VoteTallyCard
                        participantCount={data.participantCount}
                        rows={data.result.map(row => ({
                            activityId: row.activityId,
                            name: row.name,
                            price: row.price,
                            likeCount: row.likeCount,
                        }))}
                        showPrices
                    />
                    {isInitiator && data.destinationSlug && (
                        <button
                            type="button"
                            className="result-open-trip-btn"
                            onClick={() => navigate(
                                `/destination/${data.destinationSlug}?tab=trip-builder&voteSession=${shareToken}`)}
                        >
                            Back to Trip Builder
                        </button>
                    )}
                </div>
            </div>
        );
    }
```

The QUIZ rendering below stays untouched.

- [ ] **Step 4: Run tests**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=VoteResultPage`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/pages/vote/VoteResultPage.js myhive-react-app/src/pages/vote/VoteResultPage.test.js
git commit -m "feat(vote): ranked tally result page for cart sessions"
```

---

### Task 14: Trip Builder annotation — badges, vote-descending sort, no-hydration guard

**Files:**
- Modify: `myhive-react-app/src/components/TripBuilder.js` (the `?voteSession=` effect at lines 83-133; the standalone render at lines 382-403; `handleContactSubmit` cleanup at lines 268-274)
- Modify: `myhive-react-app/src/components/TripBuilder.css`
- Test: `myhive-react-app/src/components/TripBuilder.test.js` (extend)

**Interfaces:**
- Consumes: `getResult().voteMode/participantCount/likeCount` (Task 5); `myhive-trip-vote-session` written by `StartGroupVoteModal` (Task 9).
- Produces: for a completed CART session (from `?voteSession=` or `myhive-trip-vote-session`), standalone itinerary items show a `♥ n` badge + mini bar and render sorted by likes desc (ties/unballoted items keep cart order, unballoted last). Cart contents are never mutated by CART sessions. Booking submit clears `myhive-trip-vote-session`.

- [ ] **Step 1: Write the failing tests** (append to `TripBuilder.test.js`; reuse the render helper from Task 10 but with a real `voteApi.getResult` mock)

```js
describe('cart vote annotation', () => {
  const cartResult = {
    voteMode: 'CART',
    participantCount: 9,
    result: [
      { activityId: 'a-low', name: 'Tiki Boat', price: 60, likeCount: 2 },
      { activityId: 'a-high', name: 'Bar Crawl', price: 45, likeCount: 8 },
    ],
  };

  test('badges standalone items and sorts them by votes descending', async () => {
    localStorage.setItem('myhive-trip-vote-session', 't-1');
    voteApi.getResult.mockResolvedValue(cartResult);

    renderTripBuilder([
      { id: 'a-low', name: 'Tiki Boat', price: 60, destinationSlug: 'prague' },
      { id: 'a-high', name: 'Bar Crawl', price: 45, destinationSlug: 'prague' },
    ]);

    expect(await screen.findByText('♥ 8')).toBeInTheDocument();
    const titles = document.querySelectorAll('.itinerary-item .itinerary-item-title');
    expect(titles[0]).toHaveTextContent('Bar Crawl');
    expect(titles[1]).toHaveTextContent('Tiki Boat');
  });

  test('CART result never dispatches ADD_TO_TRIP (no cart hydration)', async () => {
    voteApi.getResult.mockResolvedValue(cartResult);
    const dispatch = jest.fn();
    // Render with ?voteSession=t-1 in the router entry and the dispatch spy:
    renderTripBuilderWithDispatch(
      [{ id: 'a-high', name: 'Bar Crawl', price: 45, destinationSlug: 'prague' }],
      dispatch,
      { route: '/?voteSession=t-1' },
    );

    expect(await screen.findByText('♥ 8')).toBeInTheDocument();
    expect(dispatch).not.toHaveBeenCalledWith(
      expect.objectContaining({ type: 'ADD_TO_TRIP' }),
    );
  });
});
```

Add this helper next to the Task 10 `renderTripBuilder` helper:

```js
function renderTripBuilderWithDispatch(tripItems, dispatch, { route = '/' } = {}) {
  const state = {
    tripId: null,
    tripItems,
    tripTravelers: 4,
    tripStartDate: '2026-08-01',
    tripEndDate: '2026-08-03',
    tripBudget: null,
    tripSetupModalOpen: false,
    tripBuilderModalOpen: false,
  };
  return render(
    <MemoryRouter initialEntries={[route]}>
      <TripContext.Provider value={{ state, dispatch }}>
        <TripBuilder destinationId="d-1" destinationSlug="prague" />
      </TripContext.Provider>
    </MemoryRouter>,
  );
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=TripBuilder`
Expected: FAIL — no badge; second test fails because the effect ignores the stored token and (with the URL param) dispatches `ADD_TO_TRIP`.

- [ ] **Step 3: Implement** (in `TripBuilder.js`)

1. Annotation state + token resolution (near the `voteSession` const, line 81):

```js
  const [storedVoteSession] = useState(() => localStorage.getItem('myhive-trip-vote-session'));
  const annotationToken = voteSession || storedVoteSession;
  const [voteAnnotation, setVoteAnnotation] = useState(null);
```

2. Rework the existing effect (lines 83-133) — same body for QUIZ, new CART branch, keyed on `annotationToken`:

```js
  useEffect(() => {
    if (!annotationToken) return;
    let cancelled = false;
    setVoteError(false);
    voteApi.getResult(annotationToken)
        .then(result => {
            if (cancelled) return;
            if (result.voteMode === 'CART') {
                // Cart votes annotate the initiator's existing cart — they never seed items.
                const counts = {};
                (result.result || []).forEach(row => {
                    counts[row.activityId] = row.likeCount;
                });
                setVoteAnnotation({ counts, participantCount: result.participantCount });
                return;
            }
            if (!voteSession) return; // QUIZ hydration only ever runs from an explicit URL param
            setVoteResult(result);
            // ... keep the existing dispatches (travelers, dates, budget, ADD_TO_TRIP loop) unchanged ...
        })
        .catch(e => {
            if (cancelled) return;
            if (e.message === 'Result not available yet') {
                return; // vote still running — nothing to annotate yet
            }
            if (voteSession) {
                setVoteError(true);
            }
        });
    return () => {
        cancelled = true;
    };
  }, [annotationToken, voteSession, dispatch]);
```

3. Display-sort the standalone list (after the `groupTripItems` destructuring):

```js
  const sortedStandalone = voteAnnotation
      ? [...standalone].sort((a, b) =>
          (voteAnnotation.counts[b.id] ?? -1) - (voteAnnotation.counts[a.id] ?? -1))
      : standalone;
```

Change the render loop at line 382 from `standalone.map` to `sortedStandalone.map`. (Array `.sort` is stable, so ties and unballoted items keep cart order; unballoted items land last via `?? -1`.)

4. Badge markup inside the standalone item, after `.itinerary-item-price` (line 392):

```jsx
                    {voteAnnotation && voteAnnotation.counts[item.id] != null && (
                        <div className="itinerary-item-votes">
                          <span className="itinerary-item-votes-count">♥ {voteAnnotation.counts[item.id]}</span>
                          <span className="itinerary-item-votes-bar">
                            <span
                                className="itinerary-item-votes-fill"
                                style={{width: `${Math.min(100,
                                    (voteAnnotation.counts[item.id]
                                        / Math.max(1, voteAnnotation.participantCount)) * 100)}%`}}
                            />
                          </span>
                        </div>
                    )}
```

5. In `handleContactSubmit`, next to the existing post-success dispatches (line 268):

```js
      localStorage.removeItem('myhive-trip-vote-session');
      setVoteAnnotation(null);
```

6. `TripBuilder.css` — append:

```css
.itinerary-item-votes {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-top: 0.25rem;
}

.itinerary-item-votes-count {
    font-size: 0.8rem;
    font-weight: 600;
    color: #7c6cf5;
    white-space: nowrap;
}

.itinerary-item-votes-bar {
    display: block;
    flex: 1;
    height: 4px;
    border-radius: 2px;
    background: rgba(124, 108, 245, 0.18);
    overflow: hidden;
}

.itinerary-item-votes-fill {
    display: block;
    height: 100%;
    border-radius: 2px;
    background: #7c6cf5;
}
```

- [ ] **Step 4: Run tests**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=TripBuilder`
Expected: PASS (annotation tests + Task 10 tests + pre-existing suite).

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/components/TripBuilder.js myhive-react-app/src/components/TripBuilder.css myhive-react-app/src/components/TripBuilder.test.js
git commit -m "feat(vote): vote badges and ranking annotation in the Trip Builder itinerary"
```

---

### Task 15: Full verification + docs

**Files:**
- Modify: `README.md` (vote endpoints section: add `POST /vote/sessions/cart`, `GET /vote/sessions/{shareToken}/tally`, the CART mode semantics)
- Modify: `docs/superpowers/specs/2026-07-05-cart-vote-flow-design.md` — status line → `Implemented`

**Interfaces:** none — verification and documentation only.

- [ ] **Step 1: Run the complete backend suite**

Run: `cd myhive-backend && ./gradlew test`
Expected: PASS, zero failures.

- [ ] **Step 2: Run the complete frontend suite**

Run: `cd myhive-react-app && npm test -- --watchAll=false`
Expected: PASS, zero failures.

- [ ] **Step 3: Update README**

In the vote/endpoints documentation add rows for:
- `POST /vote/sessions/cart` — create a cart-seeded, upvote-only vote session (no quiz); body `{destinationId, initiatorEmail, numberOfTravelers, startDate, endDate, activityIds}`.
- `GET /vote/sessions/{shareToken}/tally?voterToken=&managerToken=` — live tally for CART sessions; requires having voted or the manager token.
- A sentence on `voteMode` (`QUIZ` default, `CART` advisory ranking: no score cutoff, no budget knapsack, results annotate the Trip Builder itinerary).

- [ ] **Step 4: Verify the flow end-to-end in dev** (manual, using two browsers/profiles)

1. `cd myhive-backend && ./gradlew bootRun --args='--spring.profiles.active=dev'` and `cd myhive-react-app && npm start`.
2. Add 2-3 activities in the Trip Builder → "Let your mates vote" → email → waiting page shows the invite link.
3. Open the invite link in a second (incognito) browser → list ballot → vote → live tally appears.
4. First browser: "End voting early & see results" → result page tally → "Back to Trip Builder" → badges + vote-descending order in the itinerary.

- [ ] **Step 5: Final commit**

```bash
git add README.md docs/superpowers/specs/2026-07-05-cart-vote-flow-design.md
git commit -m "docs: document cart vote flow endpoints and mark spec implemented"
```

Note for the executor: per CLAUDE.md, after the user approves the finished feature also update the memory files (`project_overview.md` — new endpoints/flow) and add a `project_cart_vote_flow.md` memory.
