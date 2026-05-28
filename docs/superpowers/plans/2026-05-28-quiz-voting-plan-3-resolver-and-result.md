# Quiz-Driven Voting — Plan 3: Resolver, Suggestions & Result

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the participant-side loop and rebuild the result endpoint. Participants submit quiz answers; the budget-greedy resolver replaces the time-greedy one; the result endpoint returns the new `result + suggestions + budget` shape; admins can no longer delete an activity that sits in a non-`COMPLETED` session's curated list.

**Architecture:** Two new participant-side endpoints on the existing `VoteSessionController` (`GET/POST /vote/sessions/{shareToken}/quiz`) reuse `QuizService.getPublicQuiz` and a new `submitParticipantQuiz` service method. `VoteActivityLikeRepository` gains a new aggregation query producing `(activityId, likeCount, skipCount)` per session. `VoteSessionService.processSession` is rewritten: two-state Like/Skip → `score = likeCount − skipCount`, drop `score ≤ 0`, sort by `(score DESC, featured_weight DESC, id ASC)`, skip-and-continue fill against `session.budget` using `snapshotPrice × travelers`. A new `VoteSuggestionsService` computes `suggestions = quiz-derived → margin-fallback → empty`. `VoteResultResponse` is rewritten to a two-tier shape (`result[]` + `suggestions[]` + `budget`/`remaining`/`numberOfTravelers`/`totalPrice`). `ActivityService.deleteActivity` blocks deletion when an activity is in any non-`COMPLETED` session's curated list (using `VoteSessionActivityRepository.existsByActivityIdAndSession_StatusIn` from Plan 2 Task 2).

**Tech Stack:** Spring Boot 4.0 / Java 25 / Gradle, JPA + Hibernate, Lombok, JUnit 5 + AssertJ + Mockito, H2 (tests).

**Reference:** spec at `docs/superpowers/specs/2026-05-11-quiz-driven-voting-design.md`; Plan 1 + Plan 2 done state on `feat/quiz-driven-voting` (HEAD `8c91d87` at time of writing).

**Scope notes:**
- This plan does NOT touch the frontend — Plan 4 ships React.
- `VoteSessionResultActivity` entity is kept (it's the persisted record of which curated activities survived budget+vote filtering and their sort order). The result-endpoint payload reads names/prices from `vote_session_activities` via JOIN, so `VoteSessionResultActivity` does NOT need a snapshot of its own. This avoids a second migration.
- The legacy `processSession` referenced an `ACTIVITY_BUDGET_MINUTES_PER_DAY` time-budget. **Remove it.** Spec §4: "Time is not a planning constraint."
- The legacy `findLikedActivitiesWithCounts` query in `VoteActivityLikeRepository` returns only `liked = true` counts. It's used by the old resolver only — replace its caller; the method can stay for backwards-compat (no caller after Plan 3) or be removed. **Keep it for now** to avoid an unrelated repository churn; delete it as a Plan 3 cleanup task at the end.
- Activity-deletion guard uses an `ActivityInUseInSessionException` (new exception, NOT reuse of `ActivityInUseException` which is package-shaped per spec line 425).
- Email confirmation in `processSession` (existing `emailService.sendVoteResult`) keeps working but now reads the result through `VoteSessionResultActivity` rows + snapshot lookup. The `EmailService.sendVoteResult` signature accepts `List<VoteSessionResultActivity>` today; **leave its signature untouched** in Plan 3 — the template can stay name-only for now and a separate small follow-up can pretty it. If the existing template renders `result.getActivity().getName()` (live name), that still works; the price snapshot only matters for the result API payload.

---

## File Structure

**New files:**
- `service/VoteSuggestionsService.java` — quiz-derived suggestions + margin fallback
- `dto/ResultActivityDTO.java` — one row of `result[]` (with likeCount/skipCount, snapshot name+price)
- `dto/SuggestionDTO.java` — one row of `suggestions[]` (live name, live price, categories)
- `dto/ParticipantQuizSubmissionRequest.java` — `{ voterToken, responses }`
- `exception/ActivityInUseInSessionException.java` — 409 conflict, new exception
- `repository/VoteActivityLikeRepository.java` ← new query added (already exists, modify)
- `repository/ActivityRepository.java` ← new query added (already exists, modify)

**Modified files:**
- `dto/VoteResultResponse.java` — rewritten shape
- `service/VoteSessionService.java` — `submitParticipantQuiz`, `processSession` rewrite, `getResult` rewrite
- `service/ActivityService.java` — deletion guard
- `service/EmailService.java` — leave signature alone (see Scope notes)
- `controller/VoteSessionController.java` — two new participant-quiz endpoints
- `exception/GlobalExceptionHandler.java` — map `ActivityInUseInSessionException` → 409

---

## Task 1: Participant quiz endpoints (service)

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/ParticipantQuizSubmissionRequest.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/ParticipantQuizSubmissionTest.java`

- [ ] **Step 1: Create `ParticipantQuizSubmissionRequest`**

```java
package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantQuizSubmissionRequest {

    @NotNull(message = "voterToken is required")
    private UUID voterToken;

    @Valid
    @NotNull(message = "responses is required (use an empty list when there is no quiz)")
    private List<QuizResponseDTO> responses;
}
```

- [ ] **Step 2: Write failing test `ParticipantQuizSubmissionTest`**

`@SpringBootTest` integration test (`@DataJpaTest` would hit the H2 enum-CHECK quirk that Plan 2 Task 2 already encountered).

```java
package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.ParticipantQuizSubmissionRequest;
import com.myhive.backend.dto.PublicQuizDTO;
import com.myhive.backend.dto.QuizResponseDTO;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizAnswerWeight;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionQuizResponse;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import com.myhive.backend.repository.VoteSessionQuizResponseRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
class ParticipantQuizSubmissionTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteSessionQuizResponseRepository voteSessionQuizResponseRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;

    @Test
    void getParticipantQuiz_returnsSameShapeAsPublicQuiz() {
        Fixture f = setupActiveSession(/*withQuiz=*/ true);

        PublicQuizDTO quiz = voteSessionService.getParticipantQuiz(f.session.getShareToken());

        assertThat(quiz.getQuestions()).hasSize(1);
        assertThat(quiz.getQuestions().get(0).getAnswers()).hasSize(1);
    }

    @Test
    void getParticipantQuiz_unknownShareToken_throwsNotFound() {
        assertThatThrownBy(() -> voteSessionService.getParticipantQuiz(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitParticipantQuiz_persistsResponses() {
        Fixture f = setupActiveSession(true);
        UUID expectedVoter = UUID.randomUUID();

        voteSessionService.submitParticipantQuiz(
                f.session.getShareToken(),
                new ParticipantQuizSubmissionRequest(expectedVoter,
                        List.of(new QuizResponseDTO(f.question.getId(), f.answer.getId()))));

        List<VoteSessionQuizResponse> rows =
                voteSessionQuizResponseRepository.findBySessionId(f.session.getId());
        // setup persists 1 organizer response; participant adds a second.
        assertThat(rows).extracting(VoteSessionQuizResponse::getVoterToken)
                .contains(expectedVoter);
    }

    @Test
    void submitParticipantQuiz_secondTime_throwsConflict() {
        Fixture f = setupActiveSession(true);
        UUID voter = UUID.randomUUID();
        QuizResponseDTO good = new QuizResponseDTO(f.question.getId(), f.answer.getId());

        voteSessionService.submitParticipantQuiz(f.session.getShareToken(),
                new ParticipantQuizSubmissionRequest(voter, List.of(good)));

        assertThatThrownBy(() -> voteSessionService.submitParticipantQuiz(
                f.session.getShareToken(),
                new ParticipantQuizSubmissionRequest(voter, List.of(good))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void submitParticipantQuiz_sessionCompleted_throwsConflict() {
        Fixture f = setupActiveSession(true);
        f.session.setStatus(VoteSessionStatus.COMPLETED);
        voteSessionRepository.saveAndFlush(f.session);

        assertThatThrownBy(() -> voteSessionService.submitParticipantQuiz(
                f.session.getShareToken(),
                new ParticipantQuizSubmissionRequest(UUID.randomUUID(),
                        List.of(new QuizResponseDTO(f.question.getId(), f.answer.getId())))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void submitParticipantQuiz_unknownQuestion_throwsBadRequest() {
        Fixture f = setupActiveSession(true);

        assertThatThrownBy(() -> voteSessionService.submitParticipantQuiz(
                f.session.getShareToken(),
                new ParticipantQuizSubmissionRequest(UUID.randomUUID(),
                        List.of(new QuizResponseDTO(UUID.randomUUID(), f.answer.getId())))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void submitParticipantQuiz_noQuiz_emptyResponsesAccepted() {
        Fixture f = setupActiveSession(/*withQuiz=*/ false);

        voteSessionService.submitParticipantQuiz(f.session.getShareToken(),
                new ParticipantQuizSubmissionRequest(UUID.randomUUID(), List.of()));

        // No new responses persisted (we passed an empty list); no exception thrown.
        assertThat(voteSessionQuizResponseRepository.findBySessionId(f.session.getId())).isEmpty();
    }

    // ---------------- fixture ----------------

    private record Fixture(VoteSession session, QuizQuestion question, QuizAnswer answer) {}

    private Fixture setupActiveSession(boolean withQuiz) {
        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        Category nightlife = new Category();
        nightlife.setName("Nightlife");
        nightlife.setSlug("nightlife");
        nightlife = categoryRepository.save(nightlife);

        Set<Category> destCats = new HashSet<>();
        destCats.add(nightlife);
        destination.setCategories(destCats);
        destinationRepository.saveAndFlush(destination);

        Activity activity = new Activity();
        activity.setDestination(destination);
        activity.setName("Club");
        activity.setPrice(new BigDecimal("100"));
        activity.setCategories(new HashSet<>(List.of(nightlife)));
        activity = activityRepository.saveAndFlush(activity);

        QuizQuestion question = null;
        QuizAnswer answer = null;
        QuizResponseDTO organizerResponse = null;
        if (withQuiz) {
            QuizQuestion q = new QuizQuestion();
            q.setDestination(destination);
            q.setPrompt("Vibe?");
            q.setSortOrder(0);
            QuizAnswer a = new QuizAnswer();
            a.setQuestion(q);
            a.setLabel("Wild");
            a.setSortOrder(0);
            QuizAnswerWeight w = new QuizAnswerWeight();
            w.setAnswer(a);
            w.setCategory(nightlife);
            w.setWeight(2);
            a.getWeights().add(w);
            q.getAnswers().add(a);
            QuizQuestion savedQ = quizQuestionRepository.saveAndFlush(q);
            question = savedQ;
            answer = savedQ.getAnswers().get(savedQ.getAnswers().size() - 1);
            organizerResponse = new QuizResponseDTO(question.getId(), answer.getId());
        }

        VoteSessionCreateRequest req = new VoteSessionCreateRequest();
        req.setDestinationId(destination.getId());
        req.setInitiatorEmail("organizer+" + UUID.randomUUID() + "@example.com");
        req.setNumberOfTravelers(2);
        req.setStartDate(LocalDate.of(2026, 8, 1));
        req.setEndDate(LocalDate.of(2026, 8, 10));
        req.setBudget(new BigDecimal("3000"));
        req.setVoterToken(UUID.randomUUID());
        req.setQuizResponses(organizerResponse == null ? List.of() : List.of(organizerResponse));
        req.setActivityIds(List.of(activity.getId()));

        VoteSessionResponse response = voteSessionService.createSession(req);
        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        return new Fixture(session, question, answer);
    }
}
```

- [ ] **Step 3: Run red**

`cd myhive-backend && ./gradlew test --tests '*ParticipantQuizSubmissionTest'`
Expected: FAIL — methods missing.

- [ ] **Step 4: Implement service methods**

Open `VoteSessionService.java`. Add imports (alphabetical):

```java
import com.myhive.backend.dto.ParticipantQuizSubmissionRequest;
import com.myhive.backend.dto.PublicQuizDTO;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
```

Add these two public methods (place near the existing `castVote`/`castVotes` since they're participant-side):

```java
    public PublicQuizDTO getParticipantQuiz(UUID shareToken) {
        VoteSession session = findByShareToken(shareToken);
        return quizService.getPublicQuiz(session.getDestination().getId());
    }

    @Transactional
    public void submitParticipantQuiz(UUID shareToken, ParticipantQuizSubmissionRequest request) {
        VoteSession session = findByShareToken(shareToken);
        if (session.getStatus() != VoteSessionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is no longer active");
        }

        List<QuizResponseDTO> responses = request.getResponses() == null ? List.of() : request.getResponses();
        validateQuizResponses(session.getDestination(), responses);

        boolean alreadySubmitted = !voteSessionQuizResponseRepository
                .findBySessionId(session.getId()).stream()
                .filter(r -> r.getVoterToken().equals(request.getVoterToken()))
                .toList()
                .isEmpty();
        if (alreadySubmitted) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz already submitted for this voter");
        }

        for (QuizResponseDTO response : responses) {
            QuizQuestion question = quizQuestionRepository.findById(response.getQuestionId()).orElseThrow();
            QuizAnswer answer = quizAnswerRepository.findById(response.getAnswerId()).orElseThrow();
            VoteSessionQuizResponse row = new VoteSessionQuizResponse();
            row.setSession(session);
            row.setVoterToken(request.getVoterToken());
            row.setQuestion(question);
            row.setAnswer(answer);
            voteSessionQuizResponseRepository.save(row);
        }
    }
```

`validateQuizResponses(destination, responses)` already exists from Plan 2 Task 9 — reused as-is.

- [ ] **Step 5: Run green**

`./gradlew test --tests '*ParticipantQuizSubmissionTest'` — 7 tests pass.
Then `./gradlew test` — full suite green.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/ParticipantQuizSubmissionRequest.java myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java myhive-backend/src/test/java/com/myhive/backend/service/ParticipantQuizSubmissionTest.java
git commit -m "feat: add participant quiz submission service methods"
```

---

## Task 2: Participant quiz endpoints (controller)

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/controller/VoteSessionController.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/controller/VoteSessionControllerTest.java` (extend the existing class)

- [ ] **Step 1: Add failing controller tests**

`VoteSessionControllerTest` is `@SpringBootTest @AutoConfigureMockMvc` with a mocked `VoteSessionService`. Add these tests (after the existing ones):

```java
    @Test
    void getParticipantQuiz_returns200WithQuestions() throws Exception {
        UUID shareToken = UUID.randomUUID();
        com.myhive.backend.dto.PublicQuizAnswerDTO answer = new com.myhive.backend.dto.PublicQuizAnswerDTO(
                UUID.randomUUID(), "Daytime");
        com.myhive.backend.dto.PublicQuizQuestionDTO question = new com.myhive.backend.dto.PublicQuizQuestionDTO(
                UUID.randomUUID(), "Daytime or night?", List.of(answer));
        com.myhive.backend.dto.PublicQuizDTO quiz = new com.myhive.backend.dto.PublicQuizDTO(List.of(question));
        when(voteSessionService.getParticipantQuiz(shareToken)).thenReturn(quiz);

        mockMvc.perform(get("/vote/sessions/" + shareToken + "/quiz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].prompt", is("Daytime or night?")))
                .andExpect(jsonPath("$.questions[0].answers[0].weights").doesNotExist());
    }

    @Test
    void postParticipantQuiz_returns204_onSuccess() throws Exception {
        UUID shareToken = UUID.randomUUID();
        String body = """
                { "voterToken": "%s", "responses": [] }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/vote/sessions/" + shareToken + "/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    @Test
    void postParticipantQuiz_propagatesConflict_returns409() throws Exception {
        UUID shareToken = UUID.randomUUID();
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Quiz already submitted"))
                .when(voteSessionService).submitParticipantQuiz(any(), any());

        String body = """
                { "voterToken": "%s", "responses": [] }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/vote/sessions/" + shareToken + "/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }
```

Add imports if missing: `import java.util.List;` (likely present), `import org.springframework.http.MediaType;` (likely present). If `hasSize`/`is` aren't already statically imported, add `import static org.hamcrest.Matchers.is;`.

- [ ] **Step 2: Run red**

`./gradlew test --tests '*VoteSessionControllerTest'` — 3 failures (404 on the new endpoints).

- [ ] **Step 3: Add endpoints to `VoteSessionController`**

Add imports (alphabetical):
```java
import com.myhive.backend.dto.ParticipantQuizSubmissionRequest;
import com.myhive.backend.dto.PublicQuizDTO;
```

Add two handlers:

```java
    @GetMapping("/{shareToken}/quiz")
    public PublicQuizDTO getParticipantQuiz(@PathVariable UUID shareToken) {
        return voteSessionService.getParticipantQuiz(shareToken);
    }

    @PostMapping("/{shareToken}/quiz")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submitParticipantQuiz(@PathVariable UUID shareToken,
                                      @Valid @RequestBody ParticipantQuizSubmissionRequest request) {
        voteSessionService.submitParticipantQuiz(shareToken, request);
    }
```

`@Valid`, `@PathVariable`, `@RequestBody`, `@PostMapping`, `@GetMapping`, `@ResponseStatus`, `HttpStatus`, `UUID` are already imported.

- [ ] **Step 4: Run green**

`./gradlew test --tests '*VoteSessionControllerTest'` — all green.
Then `./gradlew test` — full suite green.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/controller/VoteSessionController.java myhive-backend/src/test/java/com/myhive/backend/controller/VoteSessionControllerTest.java
git commit -m "feat: wire participant quiz endpoints"
```

---

## Task 3: Vote aggregation query — `(activityId, likeCount, skipCount)`

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/repository/VoteActivityLikeRepository.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/repository/ActivityVoteCount.java` (projection interface)
- Test: `myhive-backend/src/test/java/com/myhive/backend/repository/VoteActivityLikeRepositoryTest.java` (likely exists — extend, otherwise create)

- [ ] **Step 1: Create the projection interface**

`myhive-backend/src/main/java/com/myhive/backend/repository/ActivityVoteCount.java`:

```java
package com.myhive.backend.repository;

import java.util.UUID;

public interface ActivityVoteCount {

    UUID getActivityId();

    long getLikeCount();

    long getSkipCount();
}
```

- [ ] **Step 2: Add failing test**

Locate existing tests for `VoteActivityLikeRepository`. If a test class exists, add a method; otherwise create one. `@DataJpaTest` works here because we don't persist `VoteSession` for the aggregation itself — but we DO need a `VoteSession` to anchor the likes. Use `@SpringBootTest @Transactional @Import(TestSecurityConfig.class)` to dodge the H2 quirk.

Add (or create file): `myhive-backend/src/test/java/com/myhive/backend/repository/VoteActivityLikeRepositoryTest.java`:

```java
package com.myhive.backend.repository;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteActivityLike;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.model.VoteSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteActivityLikeRepositoryTest {

    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteActivityLikeRepository voteActivityLikeRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void findVoteCountsBySessionId_returnsPerActivityLikeAndSkipCounts() {
        Destination destination = saveDestination();
        Activity a1 = saveActivity(destination, "A1");
        Activity a2 = saveActivity(destination, "A2");
        VoteSession session = saveActiveSession(destination);

        // a1: 3 likes, 1 skip; a2: 0 likes, 2 skips
        saveLike(session, a1, true);
        saveLike(session, a1, true);
        saveLike(session, a1, true);
        saveLike(session, a1, false);
        saveLike(session, a2, false);
        saveLike(session, a2, false);

        List<ActivityVoteCount> counts = voteActivityLikeRepository.findVoteCountsBySessionId(session.getId());
        Map<UUID, ActivityVoteCount> byActivity = counts.stream()
                .collect(Collectors.toMap(ActivityVoteCount::getActivityId, c -> c));

        assertThat(byActivity.get(a1.getId()).getLikeCount()).isEqualTo(3);
        assertThat(byActivity.get(a1.getId()).getSkipCount()).isEqualTo(1);
        assertThat(byActivity.get(a2.getId()).getLikeCount()).isEqualTo(0);
        assertThat(byActivity.get(a2.getId()).getSkipCount()).isEqualTo(2);
    }

    private Destination saveDestination() {
        Destination d = new Destination();
        d.setName("Prague");
        return destinationRepository.save(d);
    }

    private Activity saveActivity(Destination destination, String name) {
        Activity a = new Activity();
        a.setDestination(destination);
        a.setName(name);
        a.setPrice(new BigDecimal("100"));
        return activityRepository.saveAndFlush(a);
    }

    private VoteSession saveActiveSession(Destination destination) {
        VoteSession s = new VoteSession();
        s.setShareToken(UUID.randomUUID());
        s.setManagerToken(UUID.randomUUID());
        s.setDestination(destination);
        s.setInitiatorEmail("o@example.com");
        s.setNumberOfTravelers(2);
        s.setStartDate(LocalDate.of(2026, 8, 1));
        s.setEndDate(LocalDate.of(2026, 8, 10));
        s.setStatus(VoteSessionStatus.ACTIVE);
        s.setExpiresAt(LocalDateTime.of(2026, 8, 10, 23, 59));
        return voteSessionRepository.save(s);
    }

    private void saveLike(VoteSession session, Activity activity, boolean liked) {
        VoteActivityLike like = new VoteActivityLike();
        like.setSession(session);
        like.setVoterToken(UUID.randomUUID());
        like.setActivity(activity);
        like.setLiked(liked);
        voteActivityLikeRepository.save(like);
    }
}
```

- [ ] **Step 3: Run red**

`./gradlew test --tests '*VoteActivityLikeRepositoryTest'` — FAIL (method missing).

- [ ] **Step 4: Add the query**

In `VoteActivityLikeRepository.java`, add this method (keep the existing methods including `findLikedActivitiesWithCounts` — Task 8 deletes it):

```java
    @Query("""
            SELECT l.activity.id AS activityId,
                   SUM(CASE WHEN l.liked = true THEN 1 ELSE 0 END) AS likeCount,
                   SUM(CASE WHEN l.liked = false THEN 1 ELSE 0 END) AS skipCount
            FROM VoteActivityLike l
            WHERE l.session.id = :sessionId
            GROUP BY l.activity.id
            """)
    List<ActivityVoteCount> findVoteCountsBySessionId(@Param("sessionId") UUID sessionId);
```

- [ ] **Step 5: Run green**

`./gradlew test --tests '*VoteActivityLikeRepositoryTest'`
Then full suite.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/repository/ActivityVoteCount.java myhive-backend/src/main/java/com/myhive/backend/repository/VoteActivityLikeRepository.java myhive-backend/src/test/java/com/myhive/backend/repository/VoteActivityLikeRepositoryTest.java
git commit -m "feat: add per-activity (likeCount, skipCount) aggregation"
```

---

## Task 4: Budget-greedy `processSession`

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionProcessSessionTest.java`

Rewrite `processSession`. Per spec §4:
- Use the curated list (`vote_session_activities`) joined with vote counts.
- `score = likeCount - skipCount`; drop `score ≤ 0` (including zero-vote).
- Sort by `(score DESC, featured_weight DESC, activity_id ASC)`.
- Skip-and-continue fill: `groupCost = snapshotPrice × travelers`; budget=null → take all positives.
- Persist surviving activities into `vote_session_result_activities` with `sort_order` = insertion index.
- Set `status = COMPLETED`.

`ACTIVITY_BUDGET_MINUTES_PER_DAY` constant + related time-budget code is REMOVED.

- [ ] **Step 1: Write the failing test**

```java
package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteActivityLike;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.repository.VoteSessionResultActivityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteSessionProcessSessionTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteSessionResultActivityRepository resultActivityRepository;
    @Autowired private VoteActivityLikeRepository voteActivityLikeRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void processSession_dropsActivitiesWithNonPositiveScore() {
        Fixture f = baseFixture(/*budget=*/ null);

        // a1: 2 likes / 0 skips (score 2 — KEEP)
        // a2: 1 like / 1 skip (score 0 — DROP)
        // a3: 0 votes (score 0 — DROP)
        // a4: 0 likes / 2 skips (score -2 — DROP)
        like(f.session, f.a1, true); like(f.session, f.a1, true);
        like(f.session, f.a2, true); like(f.session, f.a2, false);
        like(f.session, f.a4, false); like(f.session, f.a4, false);

        voteSessionService.processSession(f.session);

        List<VoteSessionResultActivity> result =
                resultActivityRepository.findBySessionIdOrderBySortOrder(f.session.getId());
        assertThat(result).extracting(r -> r.getActivity().getId())
                .containsExactly(f.a1.getId());
    }

    @Test
    void processSession_skipAndContinue_budgetGreedyFill() {
        // Budget = 200. Travelers = 2 → per-activity group cost is 2 × snapshotPrice.
        // a_high (snapshot 80, group=160, score 5): fits → take, running=160
        // a_big (snapshot 150, group=300, score 4): doesn't fit → SKIP
        // a_low (snapshot 20,  group=40,  score 3): fits → take, running=200
        // No score-2 activity fits the remaining 0 → loop exits naturally.
        Fixture f = budgetFixture(new BigDecimal("200"));
        likeMany(f.session, f.aHigh, 5, 0);
        likeMany(f.session, f.aBig, 4, 0);
        likeMany(f.session, f.aLow, 3, 0);

        voteSessionService.processSession(f.session);

        List<VoteSessionResultActivity> result =
                resultActivityRepository.findBySessionIdOrderBySortOrder(f.session.getId());
        assertThat(result).extracting(r -> r.getActivity().getId())
                .containsExactly(f.aHigh.getId(), f.aLow.getId());
    }

    @Test
    void processSession_tieBreakByFeaturedWeightThenId() {
        // Two activities, equal score; the one with higher featured_weight wins.
        Fixture f = baseFixture(null);
        f.a1.setFeaturedWeight(10);
        f.a2.setFeaturedWeight(5);
        activityRepository.saveAndFlush(f.a1);
        activityRepository.saveAndFlush(f.a2);
        likeMany(f.session, f.a1, 2, 0);
        likeMany(f.session, f.a2, 2, 0);

        voteSessionService.processSession(f.session);

        List<VoteSessionResultActivity> result =
                resultActivityRepository.findBySessionIdOrderBySortOrder(f.session.getId());
        assertThat(result).extracting(r -> r.getActivity().getId())
                .containsExactly(f.a1.getId(), f.a2.getId());
    }

    @Test
    void processSession_nullBudget_takesAllPositives() {
        Fixture f = baseFixture(null);
        likeMany(f.session, f.a1, 2, 0);
        likeMany(f.session, f.a2, 1, 0);
        likeMany(f.session, f.a4, 5, 0);

        voteSessionService.processSession(f.session);

        List<VoteSessionResultActivity> result =
                resultActivityRepository.findBySessionIdOrderBySortOrder(f.session.getId());
        assertThat(result).hasSize(3);
    }

    @Test
    void processSession_setsStatusCompleted() {
        Fixture f = baseFixture(null);
        likeMany(f.session, f.a1, 1, 0);

        voteSessionService.processSession(f.session);

        VoteSession reloaded = voteSessionRepository.findById(f.session.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(VoteSessionStatus.COMPLETED);
    }

    @Test
    void processSession_resolverUsesSnapshotPriceNotLiveActivityPrice() {
        // Snapshot at curation: a1.price=100, travelers=2, budget=200. group=200 → fits.
        // Admin re-prices a1 to 10000 mid-session → resolver should still treat group cost as 200.
        Fixture f = budgetFixture(new BigDecimal("200"));
        likeMany(f.session, f.aLow, 5, 0);   // snapshotted at 20 → group 40, score 5 — clearly fits.
        Activity priceBomb = f.aLow;
        priceBomb.setPrice(new BigDecimal("99999"));
        activityRepository.saveAndFlush(priceBomb);

        voteSessionService.processSession(f.session);

        List<VoteSessionResultActivity> result =
                resultActivityRepository.findBySessionIdOrderBySortOrder(f.session.getId());
        assertThat(result).anySatisfy(r -> assertThat(r.getActivity().getId()).isEqualTo(f.aLow.getId()));
    }

    // ---------------- fixtures ----------------

    private record Fixture(VoteSession session,
                           Activity a1, Activity a2, Activity a3, Activity a4,
                           Activity aHigh, Activity aBig, Activity aLow) {}

    private Fixture baseFixture(BigDecimal budget) {
        Destination destination = saveDest();
        Category nightlife = saveCat("Nightlife", "nightlife");
        attachCat(destination, nightlife);

        Activity a1 = saveAct(destination, "a1", new BigDecimal("10"), Set.of(nightlife));
        Activity a2 = saveAct(destination, "a2", new BigDecimal("10"), Set.of(nightlife));
        Activity a3 = saveAct(destination, "a3", new BigDecimal("10"), Set.of(nightlife));
        Activity a4 = saveAct(destination, "a4", new BigDecimal("10"), Set.of(nightlife));

        VoteSession session = createSession(destination, budget,
                List.of(a1.getId(), a2.getId(), a3.getId(), a4.getId()));
        return new Fixture(session, a1, a2, a3, a4, null, null, null);
    }

    private Fixture budgetFixture(BigDecimal budget) {
        Destination destination = saveDest();
        Category nightlife = saveCat("Nightlife", "nightlife");
        attachCat(destination, nightlife);

        Activity aHigh = saveAct(destination, "aHigh", new BigDecimal("80"), Set.of(nightlife));
        Activity aBig = saveAct(destination, "aBig", new BigDecimal("150"), Set.of(nightlife));
        Activity aLow = saveAct(destination, "aLow", new BigDecimal("20"), Set.of(nightlife));

        VoteSession session = createSession(destination, budget,
                List.of(aHigh.getId(), aBig.getId(), aLow.getId()));
        return new Fixture(session, null, null, null, null, aHigh, aBig, aLow);
    }

    private VoteSession createSession(Destination destination, BigDecimal budget, List<UUID> activityIds) {
        VoteSessionCreateRequest req = new VoteSessionCreateRequest();
        req.setDestinationId(destination.getId());
        req.setInitiatorEmail("o+" + UUID.randomUUID() + "@example.com");
        req.setNumberOfTravelers(2);
        req.setStartDate(LocalDate.of(2026, 8, 1));
        req.setEndDate(LocalDate.of(2026, 8, 10));
        req.setBudget(budget);
        req.setVoterToken(UUID.randomUUID());
        req.setQuizResponses(List.of());
        req.setActivityIds(activityIds);

        VoteSessionResponse response = voteSessionService.createSession(req);
        return voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
    }

    private Destination saveDest() {
        Destination d = new Destination();
        d.setName("Prague");
        return destinationRepository.save(d);
    }

    private Category saveCat(String name, String slug) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        return categoryRepository.save(c);
    }

    private void attachCat(Destination destination, Category... cats) {
        Set<Category> set = new HashSet<>(destination.getCategories());
        for (Category c : cats) {
            set.add(c);
        }
        destination.setCategories(set);
        destinationRepository.saveAndFlush(destination);
    }

    private Activity saveAct(Destination destination, String name, BigDecimal price, Set<Category> cats) {
        Activity a = new Activity();
        a.setDestination(destination);
        a.setName(name);
        a.setPrice(price);
        a.setCategories(new HashSet<>(cats));
        return activityRepository.saveAndFlush(a);
    }

    private void like(VoteSession session, Activity activity, boolean liked) {
        VoteActivityLike l = new VoteActivityLike();
        l.setSession(session);
        l.setVoterToken(UUID.randomUUID());
        l.setActivity(activity);
        l.setLiked(liked);
        voteActivityLikeRepository.save(l);
    }

    private void likeMany(VoteSession session, Activity activity, int likes, int skips) {
        for (int i = 0; i < likes; i++) {
            like(session, activity, true);
        }
        for (int i = 0; i < skips; i++) {
            like(session, activity, false);
        }
    }
}
```

- [ ] **Step 2: Run red**

`./gradlew test --tests '*VoteSessionProcessSessionTest'` — most tests fail (old time-greedy resolver is wrong).

- [ ] **Step 3: Rewrite `processSession`**

Open `VoteSessionService.java`.

**Remove** the `ACTIVITY_BUDGET_MINUTES_PER_DAY` constant (top of the class).
**Remove** the imports of `ChronoUnit` and `ActivityLikeCount` if they're no longer used after the rewrite (verify by compile).

Add imports as needed:
```java
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.repository.ActivityVoteCount;
import java.util.Comparator;
import java.util.HashMap;
```
(Skip ones already imported.)

Replace the entire `processSession` body with:

```java
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSession(VoteSession session) {
        session = voteSessionRepository.findById(session.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Vote session not found"));

        List<VoteSessionActivity> curated =
                voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId());
        if (curated.isEmpty()) {
            // Legacy session pre-Plan-2: fall back to the old time-greedy path is no longer supported.
            // Such a session can only resolve to an empty result.
            session.setStatus(VoteSessionStatus.COMPLETED);
            voteSessionRepository.save(session);
            return;
        }

        Map<UUID, ActivityVoteCount> counts =
                voteActivityLikeRepository.findVoteCountsBySessionId(session.getId()).stream()
                        .collect(Collectors.toMap(ActivityVoteCount::getActivityId, c -> c));

        // For each curated row compute score and pull the live Activity for featured_weight ordering.
        Map<UUID, Activity> activitiesById = curated.stream()
                .collect(Collectors.toMap(c -> c.getActivity().getId(), VoteSessionActivity::getActivity));

        record Ranked(VoteSessionActivity row, long score) {}
        List<Ranked> ranked = curated.stream()
                .map(row -> {
                    ActivityVoteCount c = counts.get(row.getActivity().getId());
                    long like = c == null ? 0 : c.getLikeCount();
                    long skip = c == null ? 0 : c.getSkipCount();
                    return new Ranked(row, like - skip);
                })
                .filter(r -> r.score() > 0)
                .sorted(Comparator
                        .comparingLong(Ranked::score).reversed()
                        .thenComparing((Ranked r) -> activitiesById.get(r.row().getActivity().getId()).getFeaturedWeight(),
                                Comparator.reverseOrder())
                        .thenComparing(r -> r.row().getActivity().getId()))
                .toList();

        BigDecimal travelers = BigDecimal.valueOf(session.getNumberOfTravelers());
        BigDecimal budget = session.getBudget();
        BigDecimal running = BigDecimal.ZERO;
        int sortOrder = 0;
        for (Ranked r : ranked) {
            BigDecimal groupCost = r.row().getPrice().multiply(travelers);
            if (budget != null && running.add(groupCost).compareTo(budget) > 0) {
                continue;   // skip-and-continue: a cheaper lower-ranked activity may still fit
            }
            VoteSessionResultActivity resultRow = new VoteSessionResultActivity();
            resultRow.setSession(session);
            resultRow.setActivity(r.row().getActivity());
            resultRow.setSortOrder(sortOrder++);
            resultActivityRepository.save(resultRow);
            running = running.add(groupCost);
        }

        session.setStatus(VoteSessionStatus.COMPLETED);
        voteSessionRepository.save(session);
        log.info("Processed vote session {} — {} activities selected", session.getId(), sortOrder);

        if (emailEnabled) {
            List<VoteSessionResultActivity> results =
                    resultActivityRepository.findBySessionIdOrderBySortOrder(session.getId());
            emailService.sendVoteResult(session, results, siteUrl);
        }
    }
```

The local `record Ranked` is method-scoped — Java 25 supports this. If your toolchain complains, hoist to a `private record` inside `VoteSessionService`.

- [ ] **Step 4: Run green**

`./gradlew test --tests '*VoteSessionProcessSessionTest'` — 6 tests pass.
Then `./gradlew test` — full suite green.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionProcessSessionTest.java
git commit -m "feat: budget-greedy resolver rewrites processSession"
```

---

## Task 5: Suggestions service

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSuggestionsService.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/repository/ActivityRepository.java` (add a query)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSuggestionsServiceTest.java`

Per spec §5:
- `groupCats = snapshot(ALL session quiz responses)`.
- If empty → `quizSuggestions = []` (skip the query).
- Else → activities in `groupCats`, NOT in curated list, ORDER BY `featured_weight DESC, id ASC`, LIMIT 10.
- If `quizSuggestions.isEmpty()` → fallback: all votable destination activities NOT in curated list, same order/limit.
- Both queries: votable-only (`groupCats` is already filtered to votable by `snapshot`; the fallback adds the `c.votable = true` predicate).

- [ ] **Step 1: Add repo query**

Modify `ActivityRepository.java`. Add this method (next to the existing `findPoolCandidates`):

```java
    @Query("""
            SELECT DISTINCT a FROM Activity a
            JOIN a.categories c
            WHERE a.destination.id = :destinationId
              AND c.votable = true
              AND (:categoryIds IS NULL OR c.id IN :categoryIds)
              AND a.id NOT IN :excludedActivityIds
            ORDER BY a.featuredWeight DESC, a.id ASC
            """)
    List<Activity> findSuggestionCandidates(@Param("destinationId") UUID destinationId,
                                            @Param("categoryIds") Collection<UUID> categoryIds,
                                            @Param("excludedActivityIds") Collection<UUID> excludedActivityIds,
                                            Pageable pageable);
```

Note: `:categoryIds IS NULL` is the empty-categories fallback. When `null` is passed, the predicate is skipped — i.e. "match any votable category." When a non-null set is passed but empty, JPA would `... IN ()` which is invalid; callers must pass `null` (not an empty collection) to trigger the fallback. The service does exactly that.

`excludedActivityIds` is always passed as a non-empty collection in practice (the curated list); for safety, the service passes `List.of(UUID.randomUUID())` when the curated list is somehow empty (defensive — should never happen, since `activityIds` in session-create is `@NotEmpty`).

- [ ] **Step 2: Write the failing service test**

```java
package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.SuggestionDTO;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizAnswerWeight;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionQuizResponse;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.myhive.backend.dto.QuizResponseDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteSuggestionsServiceTest {

    @Autowired private VoteSuggestionsService voteSuggestionsService;
    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;

    @Test
    void suggestions_quizDerived_excludesCuratedAndOrdersByFeaturedWeight() {
        Destination destination = newDestination();
        Category nightlife = newCategory("Nightlife", "nightlife", true);
        attachCategory(destination, nightlife);

        Activity curated = newActivity(destination, "curated", 5, Set.of(nightlife));
        Activity hi = newActivity(destination, "hi", 10, Set.of(nightlife));
        Activity lo = newActivity(destination, "lo", 1, Set.of(nightlife));

        QuizQuestion question = newQuestion(destination, "Vibe?");
        QuizAnswer answer = newAnswer(question, "Wild", nightlife, 2);

        VoteSession session = createSession(destination,
                List.of(curated.getId()),
                List.of(new QuizResponseDTO(question.getId(), answer.getId())));

        List<SuggestionDTO> suggestions = voteSuggestionsService.buildSuggestions(session);

        assertThat(suggestions).extracting("activityId")
                .containsExactly(hi.getId(), lo.getId());   // curated excluded; ordered by featuredWeight DESC
    }

    @Test
    void suggestions_emptyGroupCats_fallsBackToVotableExcludingCurated() {
        Destination destination = newDestination();
        Category nightlife = newCategory("Nightlife", "nightlife", true);
        Category transfer = newCategory("Transfer", "transfer", false);
        attachCategory(destination, nightlife, transfer);

        Activity curated = newActivity(destination, "curated", 5, Set.of(nightlife));
        Activity nightOther = newActivity(destination, "nightOther", 7, Set.of(nightlife));
        Activity inTransferOnly = newActivity(destination, "transferOnly", 9, Set.of(transfer));

        VoteSession session = createSession(destination, List.of(curated.getId()), List.of());

        List<SuggestionDTO> suggestions = voteSuggestionsService.buildSuggestions(session);

        // No quiz responses → snapshot empty → fallback to all-votable-not-curated.
        // Transfer is non-votable, excluded.
        assertThat(suggestions).extracting("activityId").containsExactly(nightOther.getId());
    }

    @Test
    void suggestions_cappedAtTen() {
        Destination destination = newDestination();
        Category nightlife = newCategory("Nightlife", "nightlife", true);
        attachCategory(destination, nightlife);

        Activity curated = newActivity(destination, "curated", 100, Set.of(nightlife));
        for (int i = 0; i < 12; i++) {
            newActivity(destination, "a" + i, 50 - i, Set.of(nightlife));
        }

        VoteSession session = createSession(destination, List.of(curated.getId()), List.of());

        List<SuggestionDTO> suggestions = voteSuggestionsService.buildSuggestions(session);

        assertThat(suggestions).hasSize(10);
    }

    // ---- helpers (same shape as Plan 2 tests) ----

    private VoteSession createSession(Destination destination, List<UUID> activityIds,
                                      List<QuizResponseDTO> quizResponses) {
        VoteSessionCreateRequest req = new VoteSessionCreateRequest();
        req.setDestinationId(destination.getId());
        req.setInitiatorEmail("o+" + UUID.randomUUID() + "@example.com");
        req.setNumberOfTravelers(2);
        req.setStartDate(LocalDate.of(2026, 8, 1));
        req.setEndDate(LocalDate.of(2026, 8, 10));
        req.setVoterToken(UUID.randomUUID());
        req.setQuizResponses(quizResponses);
        req.setActivityIds(activityIds);
        VoteSessionResponse response = voteSessionService.createSession(req);
        return voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
    }

    private Destination newDestination() {
        Destination d = new Destination();
        d.setName("Prague");
        return destinationRepository.save(d);
    }

    private Category newCategory(String name, String slug, boolean votable) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setVotable(votable);
        return categoryRepository.save(c);
    }

    private void attachCategory(Destination destination, Category... cats) {
        Set<Category> set = new HashSet<>(destination.getCategories());
        for (Category c : cats) {
            set.add(c);
        }
        destination.setCategories(set);
        destinationRepository.saveAndFlush(destination);
    }

    private Activity newActivity(Destination destination, String name, int featuredWeight, Set<Category> cats) {
        Activity a = new Activity();
        a.setDestination(destination);
        a.setName(name);
        a.setPrice(new BigDecimal("100"));
        a.setFeaturedWeight(featuredWeight);
        a.setCategories(new HashSet<>(cats));
        return activityRepository.saveAndFlush(a);
    }

    private QuizQuestion newQuestion(Destination destination, String prompt) {
        QuizQuestion q = new QuizQuestion();
        q.setDestination(destination);
        q.setPrompt(prompt);
        q.setSortOrder(0);
        return quizQuestionRepository.saveAndFlush(q);
    }

    private QuizAnswer newAnswer(QuizQuestion question, String label, Category category, int weight) {
        QuizAnswer a = new QuizAnswer();
        a.setQuestion(question);
        a.setLabel(label);
        a.setSortOrder(0);
        QuizAnswerWeight w = new QuizAnswerWeight();
        w.setAnswer(a);
        w.setCategory(category);
        w.setWeight(weight);
        a.getWeights().add(w);
        question.getAnswers().add(a);
        QuizQuestion saved = quizQuestionRepository.saveAndFlush(question);
        return saved.getAnswers().get(saved.getAnswers().size() - 1);
    }
}
```

`SuggestionDTO` is created in Task 6 — these tests will fail to compile until Task 6 lands. **Reorder if you prefer**: do Task 6 first (DTOs), then Task 5 (service). Either order is fine; the plan presents them in dependency-graph order (suggestion service depends on the DTO at compile time, so do the DTO first in practice).

**Practical order for the implementer:** create `SuggestionDTO` (from Task 6) BEFORE writing this test. Or stub the service method to return `List<SuggestionDTO>` after Task 6's DTOs are in place. The plan-level ordering is loose here — group tasks 5+6 if you want.

- [ ] **Step 3: Run red**

`./gradlew test --tests '*VoteSuggestionsServiceTest'` — FAIL.

- [ ] **Step 4: Implement `VoteSuggestionsService`**

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.QuizResponseDTO;
import com.myhive.backend.dto.SuggestionDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.entity.VoteSessionQuizResponse;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.VoteSessionActivityRepository;
import com.myhive.backend.repository.VoteSessionQuizResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoteSuggestionsService {

    static final int SUGGESTION_CAP = 10;

    private final QuizService quizService;
    private final ActivityRepository activityRepository;
    private final VoteSessionActivityRepository voteSessionActivityRepository;
    private final VoteSessionQuizResponseRepository voteSessionQuizResponseRepository;

    public List<SuggestionDTO> buildSuggestions(VoteSession session) {
        List<UUID> curatedActivityIds = voteSessionActivityRepository
                .findBySessionIdOrderBySortOrder(session.getId()).stream()
                .map(row -> row.getActivity().getId())
                .toList();
        Collection<UUID> exclusion = curatedActivityIds.isEmpty()
                ? List.of(UUID.randomUUID())   // defensive: never `NOT IN ()`
                : curatedActivityIds;

        List<UUID> answerIds = voteSessionQuizResponseRepository.findBySessionId(session.getId()).stream()
                .map(r -> r.getAnswer().getId())
                .toList();
        List<UUID> groupCats = quizService.snapshot(answerIds);

        List<Activity> quizSuggestions = groupCats.isEmpty()
                ? List.of()
                : activityRepository.findSuggestionCandidates(
                        session.getDestination().getId(), groupCats, exclusion,
                        PageRequest.of(0, SUGGESTION_CAP));

        List<Activity> chosen = quizSuggestions;
        if (chosen.isEmpty()) {
            chosen = activityRepository.findSuggestionCandidates(
                    session.getDestination().getId(), null, exclusion,
                    PageRequest.of(0, SUGGESTION_CAP));
        }

        return chosen.stream().map(this::toDTO).toList();
    }

    private SuggestionDTO toDTO(Activity activity) {
        List<String> categories = activity.getCategories().stream()
                .map(Category::getName)
                .sorted()
                .toList();
        return new SuggestionDTO(activity.getId(), activity.getName(),
                activity.getPrice(), activity.getImageUrl(), categories);
    }
}
```

- [ ] **Step 5: Run green** (after Task 6 lands the DTO — if doing Task 5 first, expect compile errors until then).

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/VoteSuggestionsService.java myhive-backend/src/main/java/com/myhive/backend/repository/ActivityRepository.java myhive-backend/src/test/java/com/myhive/backend/service/VoteSuggestionsServiceTest.java
git commit -m "feat: add VoteSuggestionsService with quiz-derived + margin fallback"
```

---

## Task 6: Result-tier DTOs

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/ResultActivityDTO.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/SuggestionDTO.java`

- [ ] **Step 1: Create `ResultActivityDTO`**

```java
package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultActivityDTO {

    private UUID activityId;
    private String name;          // snapshot
    private BigDecimal price;     // snapshot, per-person
    private long likeCount;
    private long skipCount;
}
```

- [ ] **Step 2: Create `SuggestionDTO`**

```java
package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionDTO {

    private UUID activityId;
    private String name;          // live
    private BigDecimal price;     // live, per-person
    private String imageUrl;
    private List<String> categories;
}
```

- [ ] **Step 3: Verify compile**

`./gradlew compileJava` — green.

- [ ] **Step 4: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/ResultActivityDTO.java myhive-backend/src/main/java/com/myhive/backend/dto/SuggestionDTO.java
git commit -m "feat: add ResultActivityDTO and SuggestionDTO"
```

---

## Task 7: `VoteResultResponse` rewrite + `getResult` rewrite

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/dto/VoteResultResponse.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionGetResultTest.java`

Per spec line 344–362.

- [ ] **Step 1: Rewrite `VoteResultResponse`**

Replace the file's contents with:

```java
package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoteResultResponse {

    private List<ResultActivityDTO> result;
    private List<SuggestionDTO> suggestions;
    private Integer numberOfTravelers;
    private BigDecimal totalPrice;     // group total of result, using snapshot prices
    private BigDecimal budget;         // nullable
    private BigDecimal remaining;      // budget - totalPrice; null when budget is null
}
```

This is a breaking change. The frontend (Plan 4) will read the new shape. There are no other callers in the backend except `VoteSessionService.getResult` and `VoteSessionController.getResult`.

- [ ] **Step 2: Write the failing test**

```java
package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.QuizResponseDTO;
import com.myhive.backend.dto.ResultActivityDTO;
import com.myhive.backend.dto.SuggestionDTO;
import com.myhive.backend.dto.VoteResultResponse;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizAnswerWeight;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.entity.VoteActivityLike;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.exception.ResultNotReadyException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class VoteSessionGetResultTest {

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteActivityLikeRepository voteActivityLikeRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;

    @Test
    void getResult_beforeProcessing_throwsResultNotReady() {
        VoteSession session = createMinimalActiveSession();
        assertThatThrownBy(() -> voteSessionService.getResult(session.getShareToken()))
                .isInstanceOf(ResultNotReadyException.class);
    }

    @Test
    void getResult_returnsResultWithSnapshotPricesAndCounts() {
        Destination destination = saveDest();
        Category nightlife = saveCat("Nightlife", "nightlife", true);
        attachCat(destination, nightlife);
        Activity activity = saveAct(destination, "Tank Driving", new BigDecimal("150.00"), 5, Set.of(nightlife));

        VoteSession session = createAndPopulate(destination, new BigDecimal("3000"),
                List.of(activity.getId()), List.of(), 2);
        like(session, activity, true);
        like(session, activity, true);
        like(session, activity, false);

        voteSessionService.processSession(session);
        VoteResultResponse response = voteSessionService.getResult(session.getShareToken());

        assertThat(response.getResult()).hasSize(1);
        ResultActivityDTO row = response.getResult().get(0);
        assertThat(row.getActivityId()).isEqualTo(activity.getId());
        assertThat(row.getName()).isEqualTo("Tank Driving");
        assertThat(row.getPrice()).isEqualByComparingTo("150.00");
        assertThat(row.getLikeCount()).isEqualTo(2);
        assertThat(row.getSkipCount()).isEqualTo(1);

        // totalPrice = snapshot 150 * 2 travelers = 300
        assertThat(response.getTotalPrice()).isEqualByComparingTo("300.00");
        assertThat(response.getBudget()).isEqualByComparingTo("3000");
        assertThat(response.getRemaining()).isEqualByComparingTo("2700.00");
        assertThat(response.getNumberOfTravelers()).isEqualTo(2);
    }

    @Test
    void getResult_nullBudget_remainingIsNull() {
        Destination destination = saveDest();
        Category nightlife = saveCat("Nightlife", "nightlife", true);
        attachCat(destination, nightlife);
        Activity activity = saveAct(destination, "A", new BigDecimal("100"), 5, Set.of(nightlife));

        VoteSession session = createAndPopulate(destination, /*budget=*/ null,
                List.of(activity.getId()), List.of(), 2);
        like(session, activity, true);

        voteSessionService.processSession(session);
        VoteResultResponse response = voteSessionService.getResult(session.getShareToken());

        assertThat(response.getBudget()).isNull();
        assertThat(response.getRemaining()).isNull();
    }

    @Test
    void getResult_includesSuggestionsExcludingCuratedList() {
        Destination destination = saveDest();
        Category nightlife = saveCat("Nightlife", "nightlife", true);
        attachCat(destination, nightlife);
        Activity curated = saveAct(destination, "curated", new BigDecimal("100"), 5, Set.of(nightlife));
        Activity suggested = saveAct(destination, "suggested", new BigDecimal("200"), 7, Set.of(nightlife));

        VoteSession session = createAndPopulate(destination, new BigDecimal("1000"),
                List.of(curated.getId()), List.of(), 2);
        like(session, curated, true);
        voteSessionService.processSession(session);

        VoteResultResponse response = voteSessionService.getResult(session.getShareToken());

        assertThat(response.getSuggestions()).extracting(SuggestionDTO::getActivityId)
                .containsExactly(suggested.getId());
    }

    // ---- helpers (truncated for the plan; mirror the Plan 2 helpers) ----

    private VoteSession createMinimalActiveSession() {
        Destination destination = saveDest();
        Category nightlife = saveCat("Nightlife", "nightlife", true);
        attachCat(destination, nightlife);
        Activity activity = saveAct(destination, "A", new BigDecimal("100"), 5, Set.of(nightlife));
        return createAndPopulate(destination, null, List.of(activity.getId()), List.of(), 2);
    }

    private VoteSession createAndPopulate(Destination destination, BigDecimal budget,
                                          List<UUID> activityIds,
                                          List<QuizResponseDTO> quizResponses,
                                          int travelers) {
        VoteSessionCreateRequest req = new VoteSessionCreateRequest();
        req.setDestinationId(destination.getId());
        req.setInitiatorEmail("o+" + UUID.randomUUID() + "@example.com");
        req.setNumberOfTravelers(travelers);
        req.setStartDate(LocalDate.of(2026, 8, 1));
        req.setEndDate(LocalDate.of(2026, 8, 10));
        req.setBudget(budget);
        req.setVoterToken(UUID.randomUUID());
        req.setQuizResponses(quizResponses);
        req.setActivityIds(activityIds);
        VoteSessionResponse response = voteSessionService.createSession(req);
        return voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
    }

    private Destination saveDest() {
        Destination d = new Destination();
        d.setName("Prague");
        return destinationRepository.save(d);
    }

    private Category saveCat(String name, String slug, boolean votable) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setVotable(votable);
        return categoryRepository.save(c);
    }

    private void attachCat(Destination destination, Category... cats) {
        Set<Category> set = new HashSet<>(destination.getCategories());
        for (Category c : cats) {
            set.add(c);
        }
        destination.setCategories(set);
        destinationRepository.saveAndFlush(destination);
    }

    private Activity saveAct(Destination destination, String name, BigDecimal price,
                              int featuredWeight, Set<Category> cats) {
        Activity a = new Activity();
        a.setDestination(destination);
        a.setName(name);
        a.setPrice(price);
        a.setFeaturedWeight(featuredWeight);
        a.setCategories(new HashSet<>(cats));
        return activityRepository.saveAndFlush(a);
    }

    private void like(VoteSession session, Activity activity, boolean liked) {
        VoteActivityLike l = new VoteActivityLike();
        l.setSession(session);
        l.setVoterToken(UUID.randomUUID());
        l.setActivity(activity);
        l.setLiked(liked);
        voteActivityLikeRepository.save(l);
    }
}
```

- [ ] **Step 3: Run red**

`./gradlew test --tests '*VoteSessionGetResultTest'` — FAIL.

- [ ] **Step 4: Rewrite `getResult` in `VoteSessionService`**

Inject `VoteSuggestionsService` into `VoteSessionService` (add `private final VoteSuggestionsService voteSuggestionsService;`).

Add imports (alphabetical): `com.myhive.backend.dto.ResultActivityDTO`, `com.myhive.backend.dto.SuggestionDTO`, `com.myhive.backend.dto.VoteResultResponse` (likely already imported), `com.myhive.backend.repository.ActivityVoteCount`. Remove the now-unused `LocalDate` import if no other method needs it.

Replace `getResult` body:

```java
    public VoteResultResponse getResult(UUID shareToken) {
        VoteSession session = findByShareToken(shareToken);
        if (session.getStatus() != VoteSessionStatus.COMPLETED) {
            throw new ResultNotReadyException("Result not available yet");
        }

        List<VoteSessionResultActivity> resultRows = resultActivityRepository
                .findBySessionIdOrderBySortOrder(session.getId());
        Map<UUID, VoteSessionActivity> curatedByActivity = voteSessionActivityRepository
                .findBySessionIdOrderBySortOrder(session.getId()).stream()
                .collect(Collectors.toMap(row -> row.getActivity().getId(), row -> row));
        Map<UUID, ActivityVoteCount> countsByActivity = voteActivityLikeRepository
                .findVoteCountsBySessionId(session.getId()).stream()
                .collect(Collectors.toMap(ActivityVoteCount::getActivityId, c -> c));

        List<ResultActivityDTO> result = resultRows.stream().map(r -> {
            UUID activityId = r.getActivity().getId();
            VoteSessionActivity curated = curatedByActivity.get(activityId);
            ActivityVoteCount counts = countsByActivity.get(activityId);
            long like = counts == null ? 0 : counts.getLikeCount();
            long skip = counts == null ? 0 : counts.getSkipCount();
            return new ResultActivityDTO(activityId, curated.getActivityName(),
                    curated.getPrice(), like, skip);
        }).toList();

        BigDecimal travelers = BigDecimal.valueOf(session.getNumberOfTravelers());
        BigDecimal totalPrice = result.stream()
                .map(r -> r.getPrice().multiply(travelers))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal budget = session.getBudget();
        BigDecimal remaining = budget == null ? null : budget.subtract(totalPrice);

        List<SuggestionDTO> suggestions = voteSuggestionsService.buildSuggestions(session);

        return new VoteResultResponse(result, suggestions, session.getNumberOfTravelers(),
                totalPrice, budget, remaining);
    }
```

- [ ] **Step 5: Run green**

`./gradlew test --tests '*VoteSessionGetResultTest'`
Then full suite.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/VoteResultResponse.java myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionGetResultTest.java
git commit -m "feat: result endpoint returns two-tier result+suggestions+budget"
```

---

## Task 8: Activity deletion guard (block deletion while in non-COMPLETED session)

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/exception/ActivityInUseInSessionException.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/ActivityService.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/exception/GlobalExceptionHandler.java` (map to 409)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/ActivityDeletionGuardTest.java`

Spec line 425: an admin deleting an activity in a non-`COMPLETED` session's curated list → 409. New exception (not reusing `ActivityInUseException`, which is package-shaped).

- [ ] **Step 1: Create the exception**

```java
package com.myhive.backend.exception;

import java.util.List;

public class ActivityInUseInSessionException extends RuntimeException {

    private final List<String> sessionShareTokens;

    public ActivityInUseInSessionException(String message, List<String> sessionShareTokens) {
        super(message);
        this.sessionShareTokens = sessionShareTokens;
    }

    public List<String> getSessionShareTokens() {
        return sessionShareTokens;
    }
}
```

- [ ] **Step 2: Write failing test**

```java
package com.myhive.backend.service;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.exception.ActivityInUseInSessionException;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class ActivityDeletionGuardTest {

    @Autowired private ActivityService activityService;
    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void deleteActivity_inActiveSessionCuratedList_throwsConflict() {
        Activity activity = setupActivityInSession(VoteSessionStatus.ACTIVE);
        assertThatThrownBy(() -> activityService.deleteActivity(activity.getId()))
                .isInstanceOf(ActivityInUseInSessionException.class);
    }

    @Test
    void deleteActivity_inCompletedSession_succeeds() {
        Activity activity = setupActivityInSession(VoteSessionStatus.COMPLETED);
        assertThatCode(() -> activityService.deleteActivity(activity.getId())).doesNotThrowAnyException();
    }

    private Activity setupActivityInSession(VoteSessionStatus status) {
        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        Category nightlife = new Category();
        nightlife.setName("Nightlife");
        nightlife.setSlug("nightlife");
        nightlife = categoryRepository.save(nightlife);
        Set<Category> cats = new HashSet<>(); cats.add(nightlife);
        destination.setCategories(cats);
        destinationRepository.saveAndFlush(destination);

        Activity activity = new Activity();
        activity.setDestination(destination);
        activity.setName("Club");
        activity.setPrice(new BigDecimal("100"));
        activity.setCategories(new HashSet<>(List.of(nightlife)));
        activity = activityRepository.saveAndFlush(activity);

        VoteSessionCreateRequest req = new VoteSessionCreateRequest();
        req.setDestinationId(destination.getId());
        req.setInitiatorEmail("o+" + UUID.randomUUID() + "@example.com");
        req.setNumberOfTravelers(2);
        req.setStartDate(LocalDate.of(2026, 8, 1));
        req.setEndDate(LocalDate.of(2026, 8, 10));
        req.setVoterToken(UUID.randomUUID());
        req.setQuizResponses(List.of());
        req.setActivityIds(List.of(activity.getId()));

        var response = voteSessionService.createSession(req);
        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        if (status == VoteSessionStatus.COMPLETED) {
            session.setStatus(VoteSessionStatus.COMPLETED);
            voteSessionRepository.saveAndFlush(session);
        }
        return activity;
    }
}
```

- [ ] **Step 3: Run red**

`./gradlew test --tests '*ActivityDeletionGuardTest'` — FAIL.

- [ ] **Step 4: Add the guard to `ActivityService.deleteActivity`**

Open `ActivityService.java`. Add imports (alphabetical):

```java
import com.myhive.backend.exception.ActivityInUseInSessionException;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.VoteSessionActivityRepository;
import java.util.EnumSet;
```

Add `private final VoteSessionActivityRepository voteSessionActivityRepository;` to the injected fields.

In `deleteActivity(UUID id)`, **before** the existing deletion logic, add:

```java
        if (voteSessionActivityRepository.existsByActivityIdAndSession_StatusIn(
                id, EnumSet.of(VoteSessionStatus.ACTIVE))) {
            throw new ActivityInUseInSessionException(
                    "Activity is in a non-completed vote session's curated list and cannot be deleted",
                    List.of());
        }
```

The repo method `existsByActivityIdAndSession_StatusIn` was already added in Plan 2 Task 2.

- [ ] **Step 5: Map the exception to 409 in `GlobalExceptionHandler`**

Open `GlobalExceptionHandler.java`. Find the existing handler for `ActivityInUseException` (package-conflict) — there's likely a 409 returner there; copy that shape. Add:

```java
    @ExceptionHandler(ActivityInUseInSessionException.class)
    public ResponseEntity<Map<String, Object>> handleActivityInUseInSession(
            ActivityInUseInSessionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", ex.getMessage(),
                "shareTokens", ex.getSessionShareTokens()));
    }
```

Add imports as needed.

- [ ] **Step 6: Run green**

`./gradlew test --tests '*ActivityDeletionGuardTest'`
Then full suite.

- [ ] **Step 7: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/exception/ActivityInUseInSessionException.java myhive-backend/src/main/java/com/myhive/backend/service/ActivityService.java myhive-backend/src/main/java/com/myhive/backend/exception/GlobalExceptionHandler.java myhive-backend/src/test/java/com/myhive/backend/service/ActivityDeletionGuardTest.java
git commit -m "feat: block deleting an activity in a non-completed session's curated list"
```

---

## Task 9: Cleanup — remove unused legacy query

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/repository/VoteActivityLikeRepository.java`
- Possibly: delete `myhive-backend/src/main/java/com/myhive/backend/repository/ActivityLikeCount.java` if it has no other callers

- [ ] **Step 1: Confirm `findLikedActivitiesWithCounts` has no callers**

`grep -rn "findLikedActivitiesWithCounts\|ActivityLikeCount" myhive-backend/src/main`

If all hits are within the repository (or its projection interface) only, the query and the projection can be deleted.

- [ ] **Step 2: Delete the query method from `VoteActivityLikeRepository`** and (if unused) delete the `ActivityLikeCount` projection file. Also remove the `import com.myhive.backend.repository.ActivityLikeCount;` from `VoteSessionService` if it was imported.

- [ ] **Step 3: Run full suite** — should remain green.

- [ ] **Step 4: Commit**

```bash
git add -A    # (after verifying with git status)
git commit -m "refactor: remove unused findLikedActivitiesWithCounts legacy query"
```

---

## Self-Review

**Spec coverage (Plan 3 portion):**
- Participant `GET /vote/sessions/{shareToken}/quiz` — Task 1 + 2 ✓
- Participant `POST /vote/sessions/{shareToken}/quiz` (409 on dup, 409 on completed, 400 on bad data) — Task 1 + 2 ✓
- Two-state vote aggregation — Task 3 ✓
- Budget-greedy resolver (skip-and-continue, snapshot price × travelers, tie-break) — Task 4 ✓
- Suggestions (quiz-derived → margin fallback → empty) — Tasks 5 + 6 ✓
- Result endpoint two-tier shape — Task 7 ✓
- Activity-deletion guard with own exception — Task 8 ✓
- Cleanup of obsolete query — Task 9 ✓
- **Deferred to Plan 4:** frontend changes (CategoryVotePage removal, ActivityVotePage 2-state, Quiz pages, Curate page, Result page rewrite, `featured_weight` admin field).
- **Out of Plan 3:** `vote_session_liked_categories` drop (still retained for historical sessions per spec line 435 — drop is in a follow-up release).

**Placeholder scan:** all steps contain complete code. The only ordering choice the implementer makes is Task 5 vs Task 6 (DTO needed before service test compiles) — Step 2 of Task 5 explicitly notes this.

**Type consistency:**
- `ActivityVoteCount` (Task 3) projection signature used identically in Tasks 4 (`processSession`) and 7 (`getResult`).
- `SuggestionDTO` (Task 6) constructor `(activityId, name, price, imageUrl, categories)` used in Tasks 5 and 7.
- `ResultActivityDTO` (Task 6) constructor `(activityId, name, price, likeCount, skipCount)` used in Task 7.
- `VoteResultResponse` (Task 7) constructor `(result, suggestions, numberOfTravelers, totalPrice, budget, remaining)` used in the rewritten `getResult`.
- `VoteSuggestionsService.buildSuggestions(VoteSession)` defined in Task 5, called in Task 7.
- `ActivityInUseInSessionException(String, List<String>)` constructor defined in Task 8, called in Task 8 (`ActivityService.deleteActivity`) and handled in `GlobalExceptionHandler`.
- `ActivityRepository.findSuggestionCandidates(destinationId, categoryIds-or-null, excludedActivityIds, Pageable)` defined Task 5, called in `VoteSuggestionsService` with both `groupCats` and `null`.

All consistent.
