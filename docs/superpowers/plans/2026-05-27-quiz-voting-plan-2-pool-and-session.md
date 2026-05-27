# Quiz-Driven Voting — Plan 2: Pool & Session Creation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the organizer's pre-session flow real — `snapshot()` reducer, public quiz endpoint, stateless `POST /vote/pool`, and an atomic `POST /vote/sessions` rewrite that takes the curated `activityIds` + organizer quiz answers and writes the curated list (with `name`/`price` snapshots) + organizer's quiz responses in one transaction.

**Architecture:** A pure `QuizService.snapshot()` function reduces quiz responses to top-K category ids (used here for the pool and later for suggestions). A new `VotePoolService` builds a stateless ≤ 20 activity pool. `VoteSessionService.createSession` is rewritten — `likedCategoryIds` is dropped from the write path (tolerated in the DTO for one release), `quizResponses`+`activityIds`+`budget`+`voterToken` become the new fields. Two new entities (`VoteSessionQuizResponse`, `VoteSessionActivity`) hold the per-session quiz log and the snapshotted curated list. A new `VoteController` handles the session-less `/vote/destinations/{id}/quiz` and `/vote/pool` endpoints (kept off `VoteSessionController` because that one is mapped `/vote/sessions`). `getActivities(shareToken)` is updated to read the curated list for new sessions, falling back to the old category-based read for historical sessions.

**Tech Stack:** Spring Boot 4.0 / Java 25 / Gradle, JPA + Hibernate, Lombok, JUnit 5 + AssertJ + Mockito, H2 (tests).

**Reference:** spec at `docs/superpowers/specs/2026-05-11-quiz-driven-voting-design.md`; Plan 1 done state on `feat/quiz-driven-voting` (commits up to `a318e7a`).

**Scope notes:**
- This plan covers ONLY the organizer-side pre-session flow + atomic session creation. Participant quiz endpoints (`GET/POST /vote/sessions/{shareToken}/quiz`), result-resolver rewrite, and suggestions ship in Plan 3.
- The frontend is Plan 4 — no React changes here.
- `featured_weight` column already exists (Plan 1). Plan 2 starts using it for ordering. Admin UI for editing it is out of scope (separate small follow-up per spec line 381).
- `vote_session_liked_categories` is **retained for historical sessions** but never written to by new sessions. The DTO field `likedCategoryIds` is **tolerated and ignored** (spec line 328) — one release cycle of grace before cleanup.
- `/vote/**` is already `permitAll()` in `SecurityConfig.java:63` — no security change needed for the new public endpoints.

---

## File Structure

**New files:**
- `entity/VoteSessionQuizResponse.java` — per-voter quiz answer log
- `entity/VoteSessionActivity.java` — curated voting list, with name/price snapshots
- `repository/VoteSessionQuizResponseRepository.java`
- `repository/VoteSessionActivityRepository.java`
- `repository/QuizAnswerRepository.java` (looking up answers + cross-validating)
- `dto/PublicQuizDTO.java`, `PublicQuizQuestionDTO.java`, `PublicQuizAnswerDTO.java` — quiz shape WITHOUT weights, for participants/organizer
- `dto/QuizResponseDTO.java` — `{ questionId, answerId }` pair, used by pool + session-create
- `dto/VotePoolRequest.java`, `dto/VotePoolResponse.java`, `dto/VotePoolActivityDTO.java`
- `service/VotePoolService.java` — stateless pool builder
- `controller/VoteController.java` — `@RequestMapping("/vote")` for session-less endpoints

**Modified files:**
- `service/QuizService.java` — add `snapshot()` and `getPublicQuiz()`
- `repository/QuizAnswerWeightRepository.java` — add `findAllByAnswerIdIn()`
- `dto/VoteSessionCreateRequest.java` — add `budget`, `voterToken`, `quizResponses`, `activityIds`; make `likedCategoryIds` optional
- `service/VoteSessionService.java` — rewrite `createSession`; update `getActivities`
- `entity/VoteSession.java` — add `@OneToMany` to `VoteSessionActivity` and `VoteSessionQuizResponse` for cascade ergonomics (read-only collections used by `getActivities`)

---

## Task 1: `QuizService.snapshot(responses)` pure function

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/QuizService.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/repository/QuizAnswerWeightRepository.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/QuizServiceTest.java`

The `snapshot()` function from the spec (§Algorithm.1): aggregate signed weights from a set of answers, filter to `score > 0 AND category.votable`, take top-K = 3 by `(score DESC, category_id ASC)`. **No internal fallback** — callers handle the empty case.

- [ ] **Step 1: Add a `findAllByAnswerIdIn` query method to `QuizAnswerWeightRepository`**

In `myhive-backend/src/main/java/com/myhive/backend/repository/QuizAnswerWeightRepository.java`, add:

```java
    List<QuizAnswerWeight> findAllByAnswerIdIn(Collection<UUID> answerIds);
```

Add imports: `java.util.Collection`, `java.util.List`.

- [ ] **Step 2: Write failing tests in `QuizServiceTest`**

Add this `@Nested` block (or four top-level tests) inside the existing `QuizServiceTest` class. The test seeds a small quiz tree via the autowired repos, then calls `quizService.snapshot(answerIds)`.

```java
    @Test
    void snapshot_emptyResponses_returnsEmpty() {
        assertThat(quizService.snapshot(List.of())).isEmpty();
    }

    @Test
    void snapshot_sumsSignedWeightsAndDropsNonPositive() {
        Category nightlife = categoryRepository.save(category("Nightlife", "nightlife"));
        Category chillout = categoryRepository.save(category("Chillout", "chillout"));
        Destination destination = destinationRepository.save(destination("Prague"));

        // Answer A: +2 Nightlife, -1 Chillout (net Nightlife=+2, Chillout=-1 → Chillout dropped)
        QuizQuestion question = saveQuestion(destination, "Q1", 0);
        QuizAnswer answer = saveAnswer(question, "A", 0);
        saveWeight(answer, nightlife, 2);
        saveWeight(answer, chillout, -1);

        List<UUID> result = quizService.snapshot(List.of(answer.getId()));

        assertThat(result).containsExactly(nightlife.getId());
    }

    @Test
    void snapshot_topThreeOrderedByScoreThenId() {
        Destination destination = destinationRepository.save(destination("Prague"));
        Category cat1 = categoryRepository.save(category("Cat1", "cat1"));
        Category cat2 = categoryRepository.save(category("Cat2", "cat2"));
        Category cat3 = categoryRepository.save(category("Cat3", "cat3"));
        Category cat4 = categoryRepository.save(category("Cat4", "cat4"));

        QuizQuestion question = saveQuestion(destination, "Q1", 0);
        QuizAnswer answer = saveAnswer(question, "A", 0);
        saveWeight(answer, cat1, 1);
        saveWeight(answer, cat2, 3);
        saveWeight(answer, cat3, 2);
        saveWeight(answer, cat4, 2);   // ties with cat3 → tie-break by id ASC

        List<UUID> result = quizService.snapshot(List.of(answer.getId()));

        // Top-K = 3. cat2 (3) first; cat3 vs cat4 break by id ASC; cat1 (1) drops.
        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualTo(cat2.getId());
        UUID winnerOfTie = cat3.getId().compareTo(cat4.getId()) < 0 ? cat3.getId() : cat4.getId();
        UUID loserOfTie = cat3.getId().compareTo(cat4.getId()) < 0 ? cat4.getId() : cat3.getId();
        assertThat(result.get(1)).isEqualTo(winnerOfTie);
        assertThat(result.get(2)).isEqualTo(loserOfTie);
    }

    @Test
    void snapshot_excludesNonVotableCategories() {
        Destination destination = destinationRepository.save(destination("Prague"));
        Category votable = categoryRepository.save(category("Nightlife", "nightlife"));
        Category nonVotable = category("Transfer", "transfer");
        nonVotable.setVotable(false);
        nonVotable = categoryRepository.save(nonVotable);

        QuizQuestion question = saveQuestion(destination, "Q1", 0);
        QuizAnswer answer = saveAnswer(question, "A", 0);
        saveWeight(answer, votable, 2);
        saveWeight(answer, nonVotable, 5);   // bigger score but votable=false

        List<UUID> result = quizService.snapshot(List.of(answer.getId()));

        assertThat(result).containsExactly(votable.getId());
    }
```

Add these private helpers at the bottom of the test class (these reduce noise across the new tests):

```java
    private Destination destination(String name) {
        Destination d = new Destination();
        d.setName(name);
        return d;
    }

    private Category category(String name, String slug) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        return c;
    }

    private QuizQuestion saveQuestion(Destination destination, String prompt, int sortOrder) {
        QuizQuestion q = new QuizQuestion();
        q.setDestination(destination);
        q.setPrompt(prompt);
        q.setSortOrder(sortOrder);
        return quizQuestionRepository.saveAndFlush(q);
    }

    private QuizAnswer saveAnswer(QuizQuestion question, String label, int sortOrder) {
        QuizAnswer a = new QuizAnswer();
        a.setQuestion(question);
        a.setLabel(label);
        a.setSortOrder(sortOrder);
        question.getAnswers().add(a);
        quizQuestionRepository.saveAndFlush(question);
        return a;
    }

    private void saveWeight(QuizAnswer answer, Category category, int weight) {
        QuizAnswerWeight w = new QuizAnswerWeight();
        w.setAnswer(answer);
        w.setCategory(category);
        w.setWeight(weight);
        answer.getWeights().add(w);
        // saving the answer's parent question persists the new weight via cascade
        quizQuestionRepository.saveAndFlush(answer.getQuestion());
    }
```

Imports to add to `QuizServiceTest`: `java.util.List` (already imported), `java.util.UUID` (already imported), `com.myhive.backend.repository.QuizAnswerWeightRepository` (only needed if you read weights directly in tests — these helpers don't need it).

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew test --tests '*QuizServiceTest'`
Expected: FAIL — `snapshot` method does not exist on `QuizService`.

- [ ] **Step 4: Implement `snapshot()` in `QuizService`**

Add these imports (alphabetically among existing) to `QuizService.java`:

```java
import com.myhive.backend.entity.QuizAnswerWeight;
import com.myhive.backend.repository.QuizAnswerWeightRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
```

Add a `private final QuizAnswerWeightRepository quizAnswerWeightRepository;` field. `@RequiredArgsConstructor` will pick it up.

Add this constant near the top of the class:

```java
    private static final int TOP_K = 3;
```

Add this method (place it after `getQuiz` / before `convertToDTO`):

```java
    public List<UUID> snapshot(Collection<UUID> answerIds) {
        if (answerIds == null || answerIds.isEmpty()) {
            return List.of();
        }
        List<QuizAnswerWeight> weights = quizAnswerWeightRepository.findAllByAnswerIdIn(answerIds);

        Map<UUID, Integer> scores = new HashMap<>();
        Map<UUID, Boolean> votableByCategory = new HashMap<>();
        for (QuizAnswerWeight weight : weights) {
            UUID categoryId = weight.getCategory().getId();
            scores.merge(categoryId, weight.getWeight(), Integer::sum);
            votableByCategory.putIfAbsent(categoryId, weight.getCategory().isVotable());
        }

        return scores.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .filter(e -> Boolean.TRUE.equals(votableByCategory.get(e.getKey())))
                .sorted(Map.Entry.<UUID, Integer>comparingByValue()
                        .reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_K)
                .map(Map.Entry::getKey)
                .toList();
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew test --tests '*QuizServiceTest'`
Expected: PASS — all four new tests green, the existing six still green.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/repository/QuizAnswerWeightRepository.java myhive-backend/src/main/java/com/myhive/backend/service/QuizService.java myhive-backend/src/test/java/com/myhive/backend/service/QuizServiceTest.java
git commit -m "feat: add QuizService.snapshot() top-K reducer"
```

---

## Task 2: `VoteSessionQuizResponse` and `VoteSessionActivity` entities

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/VoteSessionQuizResponse.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/VoteSessionActivity.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/repository/VoteSessionQuizResponseRepository.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/repository/VoteSessionActivityRepository.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/repository/VoteSessionTablesTest.java`

`VoteSessionQuizResponse` mirrors the spec's `vote_session_quiz_responses` table; `VoteSessionActivity` mirrors `vote_session_activities` (with `activity_name` and `price` snapshots).

- [ ] **Step 1: Write the failing test**

`myhive-backend/src/test/java/com/myhive/backend/repository/VoteSessionTablesTest.java`:

```java
package com.myhive.backend.repository;

import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.entity.VoteSessionQuizResponse;
import com.myhive.backend.model.VoteSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class VoteSessionTablesTest {

    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteSessionActivityRepository voteSessionActivityRepository;
    @Autowired private VoteSessionQuizResponseRepository voteSessionQuizResponseRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;

    @Test
    void voteSessionActivity_snapshotsNameAndPrice_andCascadeDeletesWithSession() {
        VoteSession session = newActiveSession();
        Activity activity = newActivity(session.getDestination(), "Tank Driving", new BigDecimal("150.00"));

        VoteSessionActivity row = new VoteSessionActivity();
        row.setSession(session);
        row.setActivity(activity);
        row.setActivityName("Tank Driving (snapshot)");
        row.setPrice(new BigDecimal("149.99"));
        row.setSortOrder(0);
        voteSessionActivityRepository.saveAndFlush(row);

        List<VoteSessionActivity> rows = voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getActivityName()).isEqualTo("Tank Driving (snapshot)");
        assertThat(rows.get(0).getPrice()).isEqualByComparingTo("149.99");

        voteSessionRepository.delete(session);
        voteSessionRepository.flush();
        assertThat(voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId())).isEmpty();
    }

    @Test
    void voteSessionQuizResponse_uniqueOnSessionVoterQuestion() {
        VoteSession session = newActiveSession();
        QuizQuestion question = newQuestion(session.getDestination());
        QuizAnswer answer = question.getAnswers().get(0);
        UUID voterToken = UUID.randomUUID();

        VoteSessionQuizResponse first = new VoteSessionQuizResponse();
        first.setSession(session);
        first.setVoterToken(voterToken);
        first.setQuestion(question);
        first.setAnswer(answer);
        voteSessionQuizResponseRepository.saveAndFlush(first);

        VoteSessionQuizResponse duplicate = new VoteSessionQuizResponse();
        duplicate.setSession(session);
        duplicate.setVoterToken(voterToken);
        duplicate.setQuestion(question);
        duplicate.setAnswer(answer);

        assertThatThrownBy(() -> voteSessionQuizResponseRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void voteSessionQuizResponse_cascadeDeletesWithSession() {
        VoteSession session = newActiveSession();
        QuizQuestion question = newQuestion(session.getDestination());
        QuizAnswer answer = question.getAnswers().get(0);

        VoteSessionQuizResponse response = new VoteSessionQuizResponse();
        response.setSession(session);
        response.setVoterToken(UUID.randomUUID());
        response.setQuestion(question);
        response.setAnswer(answer);
        voteSessionQuizResponseRepository.saveAndFlush(response);

        voteSessionRepository.delete(session);
        voteSessionRepository.flush();
        assertThat(voteSessionQuizResponseRepository.findBySessionId(session.getId())).isEmpty();
    }

    private VoteSession newActiveSession() {
        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail("test@example.com");
        session.setNumberOfTravelers(2);
        session.setStartDate(LocalDate.of(2026, 8, 1));
        session.setEndDate(LocalDate.of(2026, 8, 10));
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setExpiresAt(LocalDateTime.of(2026, 8, 10, 23, 59));
        return voteSessionRepository.save(session);
    }

    private Activity newActivity(Destination destination, String name, BigDecimal price) {
        Activity activity = new Activity();
        activity.setDestination(destination);
        activity.setName(name);
        activity.setPrice(price);
        return activityRepository.saveAndFlush(activity);
    }

    private QuizQuestion newQuestion(Destination destination) {
        QuizQuestion q = new QuizQuestion();
        q.setDestination(destination);
        q.setPrompt("Q?");
        q.setSortOrder(0);
        QuizAnswer a = new QuizAnswer();
        a.setQuestion(q);
        a.setLabel("A");
        a.setSortOrder(0);
        q.getAnswers().add(a);
        return quizQuestionRepository.saveAndFlush(q);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*VoteSessionTablesTest'`
Expected: FAIL — entities and repositories do not exist.

- [ ] **Step 3: Create `VoteSessionActivity.java`**

```java
package com.myhive.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "vote_session_activities",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "activity_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"session", "activity"})
public class VoteSessionActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private VoteSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @Column(name = "activity_name", nullable = false, length = 255)
    private String activityName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
```

- [ ] **Step 4: Create `VoteSessionQuizResponse.java`**

```java
package com.myhive.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vote_session_quiz_responses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "voter_token", "question_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"session", "question", "answer"})
public class VoteSessionQuizResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private VoteSession session;

    @Column(name = "voter_token", nullable = false)
    private UUID voterToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private QuizAnswer answer;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;
}
```

- [ ] **Step 5: Add cascade fields on `VoteSession`**

Open `myhive-backend/src/main/java/com/myhive/backend/entity/VoteSession.java`. Add these imports (alphabetically): `jakarta.persistence.CascadeType`, `jakarta.persistence.OneToMany` (verify if not already present).

Add `import java.util.ArrayList;` and `import java.util.List;` if not present.

After the `likedCategories` field, add:

```java
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<VoteSessionActivity> curatedActivities = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<VoteSessionQuizResponse> quizResponses = new ArrayList<>();
```

Update the `@ToString(exclude = ...)` to also exclude these collections:

```java
@ToString(exclude = {"destination", "likedCategories", "curatedActivities", "quizResponses"})
```

- [ ] **Step 6: Create the two repositories**

`VoteSessionActivityRepository.java`:

```java
package com.myhive.backend.repository;

import com.myhive.backend.entity.VoteSessionActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VoteSessionActivityRepository extends JpaRepository<VoteSessionActivity, UUID> {

    List<VoteSessionActivity> findBySessionIdOrderBySortOrder(UUID sessionId);

    boolean existsByActivityIdAndSession_StatusIn(UUID activityId, java.util.Collection<com.myhive.backend.model.VoteSessionStatus> statuses);
}
```

Note: `existsByActivityIdAndSession_StatusIn` is staged for Plan 3's activity-deletion guard; including it now avoids a Plan 3 repo touch.

`VoteSessionQuizResponseRepository.java`:

```java
package com.myhive.backend.repository;

import com.myhive.backend.entity.VoteSessionQuizResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VoteSessionQuizResponseRepository extends JpaRepository<VoteSessionQuizResponse, UUID> {

    List<VoteSessionQuizResponse> findBySessionId(UUID sessionId);
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew test --tests '*VoteSessionTablesTest'`
Expected: PASS — all three tests green.

- [ ] **Step 8: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/entity/VoteSessionActivity.java myhive-backend/src/main/java/com/myhive/backend/entity/VoteSessionQuizResponse.java myhive-backend/src/main/java/com/myhive/backend/entity/VoteSession.java myhive-backend/src/main/java/com/myhive/backend/repository/VoteSessionActivityRepository.java myhive-backend/src/main/java/com/myhive/backend/repository/VoteSessionQuizResponseRepository.java myhive-backend/src/test/java/com/myhive/backend/repository/VoteSessionTablesTest.java
git commit -m "feat: add vote_session_activities and vote_session_quiz_responses tables"
```

---

## Task 3: Public quiz DTOs + `QuizService.getPublicQuiz`

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/PublicQuizDTO.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/PublicQuizQuestionDTO.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/PublicQuizAnswerDTO.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/QuizService.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/QuizServiceTest.java`

Per spec line 298: *"Weights never exposed."* The public quiz shape strips the `weights` field.

- [ ] **Step 1: Create the three DTOs**

`PublicQuizAnswerDTO.java`:

```java
package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicQuizAnswerDTO {

    private UUID id;
    private String label;
}
```

`PublicQuizQuestionDTO.java`:

```java
package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicQuizQuestionDTO {

    private UUID id;
    private String prompt;
    private List<PublicQuizAnswerDTO> answers;
}
```

`PublicQuizDTO.java`:

```java
package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicQuizDTO {

    private List<PublicQuizQuestionDTO> questions;
}
```

- [ ] **Step 2: Write the failing test**

Add this test method to `QuizServiceTest`:

```java
    @Test
    void getPublicQuiz_returnsQuestionsWithoutWeights() {
        Destination destination = destinationRepository.save(destination("Prague"));
        Category category = categoryRepository.save(category("Nightlife", "nightlife"));
        QuizQuestion question = saveQuestion(destination, "Prompt?", 0);
        QuizAnswer answer = saveAnswer(question, "Label", 0);
        saveWeight(answer, category, 2);

        PublicQuizDTO publicQuiz = quizService.getPublicQuiz(destination.getId());

        assertThat(publicQuiz.getQuestions()).hasSize(1);
        assertThat(publicQuiz.getQuestions().get(0).getPrompt()).isEqualTo("Prompt?");
        assertThat(publicQuiz.getQuestions().get(0).getAnswers()).hasSize(1);
        assertThat(publicQuiz.getQuestions().get(0).getAnswers().get(0).getLabel()).isEqualTo("Label");
        // No weights field on PublicQuizAnswerDTO — type-system enforced.
    }

    @Test
    void getPublicQuiz_unknownDestination_throwsNotFound() {
        assertThatThrownBy(() -> quizService.getPublicQuiz(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
```

Imports to add (alphabetical): `com.myhive.backend.dto.PublicQuizDTO`.

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew test --tests '*QuizServiceTest'`
Expected: FAIL — `getPublicQuiz` not defined.

- [ ] **Step 4: Implement `getPublicQuiz` on `QuizService`**

Add imports (alphabetical): `com.myhive.backend.dto.PublicQuizAnswerDTO`, `com.myhive.backend.dto.PublicQuizDTO`, `com.myhive.backend.dto.PublicQuizQuestionDTO`.

Add this method (place after `getQuiz`):

```java
    public PublicQuizDTO getPublicQuiz(UUID destinationId) {
        if (!destinationRepository.existsById(destinationId)) {
            throw new ResourceNotFoundException("Destination", destinationId);
        }
        List<PublicQuizQuestionDTO> questions = quizQuestionRepository
                .findByDestinationIdOrderBySortOrder(destinationId)
                .stream()
                .map(this::toPublicQuestion)
                .toList();
        return new PublicQuizDTO(questions);
    }

    private PublicQuizQuestionDTO toPublicQuestion(QuizQuestion question) {
        List<PublicQuizAnswerDTO> answers = question.getAnswers().stream()
                .sorted(Comparator.comparingInt(QuizAnswer::getSortOrder))
                .map(a -> new PublicQuizAnswerDTO(a.getId(), a.getLabel()))
                .toList();
        return new PublicQuizQuestionDTO(question.getId(), question.getPrompt(), answers);
    }
```

- [ ] **Step 5: Run the test — green**

Run: `./gradlew test --tests '*QuizServiceTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/PublicQuizDTO.java myhive-backend/src/main/java/com/myhive/backend/dto/PublicQuizQuestionDTO.java myhive-backend/src/main/java/com/myhive/backend/dto/PublicQuizAnswerDTO.java myhive-backend/src/main/java/com/myhive/backend/service/QuizService.java myhive-backend/src/test/java/com/myhive/backend/service/QuizServiceTest.java
git commit -m "feat: add public quiz read (weights stripped)"
```

---

## Task 4: Public quiz endpoint — `GET /vote/destinations/{destinationId}/quiz`

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/controller/VoteController.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/controller/VoteControllerIntegrationTest.java`

`VoteController` will host both this endpoint and `POST /vote/pool` (Task 7). It's mapped `/vote` (not `/vote/sessions`) per spec line 313.

- [ ] **Step 1: Write the failing integration test**

```java
package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizAnswerWeight;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestSecurityConfig.class)
class VoteControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;

    @Test
    void getPublicQuiz_returnsQuestionsAndAnswers_withNoWeights() throws Exception {
        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);

        Category category = new Category();
        category.setName("Nightlife");
        category.setSlug("nightlife");
        category = categoryRepository.save(category);

        QuizQuestion question = new QuizQuestion();
        question.setDestination(destination);
        question.setPrompt("Daytime or 4am?");
        question.setSortOrder(0);
        QuizAnswer answer = new QuizAnswer();
        answer.setQuestion(question);
        answer.setLabel("4am");
        answer.setSortOrder(0);
        QuizAnswerWeight weight = new QuizAnswerWeight();
        weight.setAnswer(answer);
        weight.setCategory(category);
        weight.setWeight(2);
        answer.getWeights().add(weight);
        question.getAnswers().add(answer);
        quizQuestionRepository.saveAndFlush(question);

        mockMvc.perform(get("/vote/destinations/" + destination.getId() + "/quiz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions", hasSize(1)))
                .andExpect(jsonPath("$.questions[0].prompt", is("Daytime or 4am?")))
                .andExpect(jsonPath("$.questions[0].answers[0].label", is("4am")))
                // Critical: weights array must not leak into the public response.
                .andExpect(jsonPath("$.questions[0].answers[0].weights").doesNotExist());
    }

    @Test
    void getPublicQuiz_unknownDestination_returns404() throws Exception {
        mockMvc.perform(get("/vote/destinations/" + UUID.randomUUID() + "/quiz"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests '*VoteControllerIntegrationTest'`
Expected: FAIL — 404 (controller does not exist).

- [ ] **Step 3: Create `VoteController`**

```java
package com.myhive.backend.controller;

import com.myhive.backend.dto.PublicQuizDTO;
import com.myhive.backend.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/vote")
@RequiredArgsConstructor
public class VoteController {

    private final QuizService quizService;

    @GetMapping("/destinations/{destinationId}/quiz")
    public PublicQuizDTO getPublicQuiz(@PathVariable UUID destinationId) {
        return quizService.getPublicQuiz(destinationId);
    }
}
```

- [ ] **Step 4: Run the test — green**

Run: `./gradlew test --tests '*VoteControllerIntegrationTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/controller/VoteController.java myhive-backend/src/test/java/com/myhive/backend/controller/VoteControllerIntegrationTest.java
git commit -m "feat: add public GET /vote/destinations/{id}/quiz endpoint"
```

---

## Task 5: Pool DTOs

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/QuizResponseDTO.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/VotePoolRequest.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/VotePoolActivityDTO.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/VotePoolResponse.java`

Data carriers — no separate test; exercised by Tasks 6–7.

- [ ] **Step 1: Create the DTOs**

`QuizResponseDTO.java` (shared between pool and session-create):

```java
package com.myhive.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizResponseDTO {

    @NotNull(message = "questionId is required")
    private UUID questionId;

    @NotNull(message = "answerId is required")
    private UUID answerId;
}
```

`VotePoolRequest.java`:

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
public class VotePoolRequest {

    @NotNull(message = "destinationId is required")
    private UUID destinationId;

    @Valid
    @NotNull(message = "responses is required (use an empty list when there is no quiz)")
    private List<QuizResponseDTO> responses;
}
```

`VotePoolActivityDTO.java`:

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
public class VotePoolActivityDTO {

    private UUID activityId;
    private String name;
    private BigDecimal price;
    private String imageUrl;
    private List<String> categories;
}
```

`VotePoolResponse.java`:

```java
package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VotePoolResponse {

    private List<VotePoolActivityDTO> pool;
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/QuizResponseDTO.java myhive-backend/src/main/java/com/myhive/backend/dto/VotePoolRequest.java myhive-backend/src/main/java/com/myhive/backend/dto/VotePoolActivityDTO.java myhive-backend/src/main/java/com/myhive/backend/dto/VotePoolResponse.java
git commit -m "feat: add vote-pool DTOs"
```

---

## Task 6: `VotePoolService.buildPool`

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/VotePoolService.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VotePoolServiceTest.java`
- Modify (add a query method): `myhive-backend/src/main/java/com/myhive/backend/repository/ActivityRepository.java`
- Modify (add a query method): `myhive-backend/src/main/java/com/myhive/backend/repository/CategoryRepository.java`

`buildPool` is stateless. Returns up to `POOL_CAP = 20` activities for the destination, restricted to categories in `snapshot(responses)` — falling back to all votable destination categories when the snapshot is empty.

- [ ] **Step 1: Add the repository query**

`ActivityRepository.java` — confirm the method below isn't already there; if a similarly-named one exists, reuse it. Otherwise add:

```java
    @org.springframework.data.jpa.repository.Query("""
            SELECT DISTINCT a FROM Activity a
            JOIN a.categories c
            WHERE a.destination.id = :destinationId
              AND c.id IN :categoryIds
            ORDER BY a.featuredWeight DESC, a.id ASC
            """)
    List<Activity> findPoolCandidates(@org.springframework.data.repository.query.Param("destinationId") UUID destinationId,
                                      @org.springframework.data.repository.query.Param("categoryIds") java.util.Collection<UUID> categoryIds,
                                      org.springframework.data.domain.Pageable pageable);
```

(The plan author prefers fully-qualified annotations on this single method to avoid sprinkling new imports into `ActivityRepository`; if the implementer prefers, they may instead add `import org.springframework.data.jpa.repository.Query;` and `import org.springframework.data.repository.query.Param;` and `import org.springframework.data.domain.Pageable;` to the file and write the method conventionally — either is fine.)

`CategoryRepository.java` — add:

```java
    List<Category> findByVotableTrue();
```

The pool's no-quiz fallback ("all votable destination categories") uses a destination's `categories` set — but `Destination.categories` is the destination↔category join. To restrict to *votable* destination categories, the service will fetch the destination, intersect its categories with `votable=true`. The repo method above is the safety net when a destination has no explicit category set (then we fall back to "every votable category in the system" — narrow that down to per-destination in the service if needed; in practice destinations have their categories assigned).

- [ ] **Step 2: Write the failing service test**

`VotePoolServiceTest.java` — `@DataJpaTest` constructing the service from real repos.

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.QuizResponseDTO;
import com.myhive.backend.dto.VotePoolRequest;
import com.myhive.backend.dto.VotePoolResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizAnswerWeight;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.QuizAnswerWeightRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class VotePoolServiceTest {

    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private QuizAnswerWeightRepository quizAnswerWeightRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ActivityRepository activityRepository;

    private QuizService quizService;
    private VotePoolService votePoolService;

    @BeforeEach
    void setUp() {
        quizService = new QuizService(quizQuestionRepository, destinationRepository,
                categoryRepository, quizAnswerWeightRepository);
        votePoolService = new VotePoolService(quizService, destinationRepository, activityRepository);
    }

    @Test
    void buildPool_unknownDestination_throwsNotFound() {
        VotePoolRequest request = new VotePoolRequest(UUID.randomUUID(), List.of());
        assertThatThrownBy(() -> votePoolService.buildPool(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void buildPool_withQuizResponses_filtersByTopKCategoriesAndOrdersByFeaturedWeight() {
        Destination destination = saveDestination("Prague");
        Category nightlife = saveCategory("Nightlife", "nightlife", true);
        Category chillout = saveCategory("Chillout", "chillout", true);
        Category transfer = saveCategory("Transfer", "transfer", false);   // non-votable

        attachCategoryToDestination(destination, nightlife, chillout, transfer);

        Activity a1 = saveActivity(destination, "Club Crawl", new BigDecimal("100"), 5, Set.of(nightlife));
        Activity a2 = saveActivity(destination, "Spa Day",    new BigDecimal("80"),  3, Set.of(chillout));
        Activity a3 = saveActivity(destination, "Bus Tour",   new BigDecimal("40"),  10, Set.of(transfer));   // non-votable
        Activity a4 = saveActivity(destination, "Pub Trail",  new BigDecimal("60"),  4, Set.of(nightlife));

        QuizQuestion question = saveQuestion(destination, "Vibe?");
        QuizAnswer answer = saveAnswer(question, "Wild", nightlife, 2);   // pushes Nightlife

        VotePoolRequest request = new VotePoolRequest(destination.getId(),
                List.of(new QuizResponseDTO(question.getId(), answer.getId())));

        VotePoolResponse response = votePoolService.buildPool(request);

        // Only nightlife activities survive: a1 (weight 5) then a4 (weight 4).
        assertThat(response.getPool()).extracting("activityId")
                .containsExactly(a1.getId(), a4.getId());
        assertThat(response.getPool()).extracting("activityId")
                .doesNotContain(a3.getId());   // Transfer (non-votable) excluded
    }

    @Test
    void buildPool_emptyResponses_fallsBackToAllVotableDestinationCategories() {
        Destination destination = saveDestination("Prague");
        Category nightlife = saveCategory("Nightlife", "nightlife", true);
        Category transfer = saveCategory("Transfer", "transfer", false);

        attachCategoryToDestination(destination, nightlife, transfer);

        Activity a1 = saveActivity(destination, "Club", new BigDecimal("100"), 5, Set.of(nightlife));
        Activity a2 = saveActivity(destination, "Bus", new BigDecimal("40"), 10, Set.of(transfer));

        VotePoolResponse response = votePoolService.buildPool(
                new VotePoolRequest(destination.getId(), List.of()));

        assertThat(response.getPool()).extracting("activityId")
                .containsExactly(a1.getId());
    }

    @Test
    void buildPool_cappedAtTwenty() {
        Destination destination = saveDestination("Prague");
        Category nightlife = saveCategory("Nightlife", "nightlife", true);
        attachCategoryToDestination(destination, nightlife);

        for (int i = 0; i < 25; i++) {
            saveActivity(destination, "A" + i, new BigDecimal("10"), 100 - i, Set.of(nightlife));
        }

        VotePoolResponse response = votePoolService.buildPool(
                new VotePoolRequest(destination.getId(), List.of()));

        assertThat(response.getPool()).hasSize(20);
    }

    // ---- helpers ----
    private Destination saveDestination(String name) {
        Destination d = new Destination();
        d.setName(name);
        return destinationRepository.save(d);
    }

    private Category saveCategory(String name, String slug, boolean votable) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setVotable(votable);
        return categoryRepository.save(c);
    }

    private void attachCategoryToDestination(Destination destination, Category... categories) {
        Set<Category> set = new HashSet<>(destination.getCategories());
        for (Category c : categories) {
            set.add(c);
        }
        destination.setCategories(set);
        destinationRepository.saveAndFlush(destination);
    }

    private Activity saveActivity(Destination destination, String name, BigDecimal price,
                                  int featuredWeight, Set<Category> categories) {
        Activity a = new Activity();
        a.setDestination(destination);
        a.setName(name);
        a.setPrice(price);
        a.setFeaturedWeight(featuredWeight);
        a.setCategories(new HashSet<>(categories));
        return activityRepository.saveAndFlush(a);
    }

    private QuizQuestion saveQuestion(Destination destination, String prompt) {
        QuizQuestion q = new QuizQuestion();
        q.setDestination(destination);
        q.setPrompt(prompt);
        q.setSortOrder(0);
        return quizQuestionRepository.saveAndFlush(q);
    }

    private QuizAnswer saveAnswer(QuizQuestion question, String label, Category category, int weight) {
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
        quizQuestionRepository.saveAndFlush(question);
        return a;
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew test --tests '*VotePoolServiceTest'`
Expected: FAIL — `VotePoolService` does not exist.

- [ ] **Step 4: Implement `VotePoolService`**

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.QuizResponseDTO;
import com.myhive.backend.dto.VotePoolActivityDTO;
import com.myhive.backend.dto.VotePoolRequest;
import com.myhive.backend.dto.VotePoolResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VotePoolService {

    static final int POOL_CAP = 20;

    private final QuizService quizService;
    private final DestinationRepository destinationRepository;
    private final ActivityRepository activityRepository;

    public VotePoolResponse buildPool(VotePoolRequest request) {
        Destination destination = destinationRepository.findById(request.getDestinationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination", request.getDestinationId()));

        List<UUID> answerIds = request.getResponses().stream()
                .map(QuizResponseDTO::getAnswerId)
                .toList();
        List<UUID> organizerCats = quizService.snapshot(answerIds);

        if (organizerCats.isEmpty()) {
            organizerCats = destination.getCategories().stream()
                    .filter(Category::isVotable)
                    .map(Category::getId)
                    .toList();
        }

        if (organizerCats.isEmpty()) {
            return new VotePoolResponse(List.of());
        }

        List<Activity> activities = activityRepository.findPoolCandidates(
                destination.getId(), organizerCats, PageRequest.of(0, POOL_CAP));

        List<VotePoolActivityDTO> pool = activities.stream()
                .map(this::toDTO)
                .toList();
        return new VotePoolResponse(pool);
    }

    private VotePoolActivityDTO toDTO(Activity activity) {
        List<String> categories = activity.getCategories().stream()
                .map(Category::getName)
                .sorted()
                .collect(Collectors.toList());
        return new VotePoolActivityDTO(activity.getId(), activity.getName(),
                activity.getPrice(), activity.getImageUrl(), categories);
    }
}
```

**Note for the implementer:** the new `quizAnswerWeightRepository` parameter on `QuizService`'s constructor (Task 1) means the test in this file constructs `quizService` with 4 args. Adjust if your earlier task placed the parameter elsewhere — the test above already uses the 4-arg form.

- [ ] **Step 5: Run the test — green**

Run: `./gradlew test --tests '*VotePoolServiceTest'`
Expected: PASS — all four tests green. Then `./gradlew test` (full suite) — green.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/repository/ActivityRepository.java myhive-backend/src/main/java/com/myhive/backend/repository/CategoryRepository.java myhive-backend/src/main/java/com/myhive/backend/service/VotePoolService.java myhive-backend/src/test/java/com/myhive/backend/service/VotePoolServiceTest.java
git commit -m "feat: add VotePoolService.buildPool"
```

---

## Task 7: `POST /vote/pool` endpoint

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/controller/VoteController.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/controller/VoteControllerIntegrationTest.java`

- [ ] **Step 1: Add the failing test**

Add to `VoteControllerIntegrationTest`:

```java
    @Test
    void postPool_returnsFilteredActivitiesForDestination() throws Exception {
        Destination destination = new Destination();
        destination.setName("Prague");
        destination = destinationRepository.save(destination);
        UUID destinationId = destination.getId();

        Category nightlife = new Category();
        nightlife.setName("Nightlife");
        nightlife.setSlug("nightlife");
        nightlife = categoryRepository.save(nightlife);

        destination.setCategories(java.util.Set.of(nightlife));
        destinationRepository.saveAndFlush(destination);

        com.myhive.backend.entity.Activity activity = new com.myhive.backend.entity.Activity();
        activity.setDestination(destination);
        activity.setName("Club Crawl");
        activity.setPrice(new java.math.BigDecimal("100"));
        activity.setFeaturedWeight(5);
        activity.setCategories(new java.util.HashSet<>(java.util.List.of(nightlife)));
        activityRepository.saveAndFlush(activity);

        String body = """
                { "destinationId": "%s", "responses": [] }
                """.formatted(destinationId);

        mockMvc.perform(post("/vote/pool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pool", hasSize(1)))
                .andExpect(jsonPath("$.pool[0].name", is("Club Crawl")));
    }

    @Test
    void postPool_unknownDestination_returns404() throws Exception {
        String body = """
                { "destinationId": "%s", "responses": [] }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/vote/pool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }
```

Add imports (alphabetical): `org.springframework.http.MediaType`, `org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post`, `com.myhive.backend.repository.ActivityRepository`. Wire `ActivityRepository` as an `@Autowired` field.

- [ ] **Step 2: Run red**

Run: `./gradlew test --tests '*VoteControllerIntegrationTest'`
Expected: FAIL — 404 on `/vote/pool`.

- [ ] **Step 3: Add the endpoint to `VoteController`**

Add imports: `com.myhive.backend.dto.VotePoolRequest`, `com.myhive.backend.dto.VotePoolResponse`, `com.myhive.backend.service.VotePoolService`, `jakarta.validation.Valid`, `org.springframework.web.bind.annotation.PostMapping`, `org.springframework.web.bind.annotation.RequestBody`.

Add field `private final VotePoolService votePoolService;`.

Add handler:

```java
    @PostMapping("/pool")
    public VotePoolResponse buildPool(@Valid @RequestBody VotePoolRequest request) {
        return votePoolService.buildPool(request);
    }
```

- [ ] **Step 4: Run green**

Run: `./gradlew test --tests '*VoteControllerIntegrationTest'`
Expected: PASS — all 4 tests in this file.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/controller/VoteController.java myhive-backend/src/test/java/com/myhive/backend/controller/VoteControllerIntegrationTest.java
git commit -m "feat: add public POST /vote/pool endpoint"
```

---

## Task 8: `VoteSessionCreateRequest` — new fields, deprecate `likedCategoryIds`

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/dto/VoteSessionCreateRequest.java`

Spec line 316–328: add `budget`, `voterToken`, `quizResponses`, `activityIds`. `likedCategoryIds` becomes optional (`@NotEmpty` removed) so old clients still validate; the next task will simply ignore it.

- [ ] **Step 1: Modify the DTO**

Replace the current file contents with:

```java
package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class VoteSessionCreateRequest {
    @NotNull private UUID destinationId;
    @NotNull @Email private String initiatorEmail;
    @NotNull @Min(1) @Max(50) private Integer numberOfTravelers;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;

    @Positive(message = "budget must be > 0 when present")
    private BigDecimal budget;   // nullable

    @NotNull private UUID voterToken;

    @Valid
    private List<QuizResponseDTO> quizResponses;   // may be empty (no-quiz destination)

    @NotEmpty(message = "activityIds must not be empty")
    @Size(max = 50, message = "activityIds may not exceed 50")
    private List<UUID> activityIds;

    // Retained for one release cycle so old clients still validate; ignored by the service.
    @Size(max = 20)
    private List<UUID> likedCategoryIds;
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. (Existing `VoteSessionService.createSession` still compiles because `likedCategoryIds` getter still exists; behavior change comes in Task 9.)

- [ ] **Step 3: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/VoteSessionCreateRequest.java
git commit -m "feat: extend VoteSessionCreateRequest with budget, quizResponses, activityIds"
```

---

## Task 9: `VoteSessionService.createSession` rewrite — atomic curated-list + quiz persistence

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionServiceTest.java`

This is the centerpiece of Plan 2. The new flow per spec §3:

1. validate `endDate >= startDate`, `budget` null or `> 0`, `activityIds` non-empty;
2. validate quiz responses are well-formed (every `questionId` belongs to this destination's quiz; every `answerId` belongs to its `questionId`; no two answers for the same question; if the destination has a quiz, all questions must be answered);
3. compute `organizerCats = quizService.snapshot(answerIds)`; if empty, fall back to all votable destination categories;
4. validate each curated `activityId`: exists, belongs to the destination, has at least one category in `organizerCats`;
5. persist `VoteSession` (`ACTIVE`), one `VoteSessionActivity` per curated id (snapshotting `name` + `price`, `sortOrder` = position in `activityIds`), one `VoteSessionQuizResponse` per `QuizResponseDTO`;
6. return `shareToken` + `managerToken`.

- [ ] **Step 1: Add the failing tests**

This test class likely already exists; we extend it. Add these methods (and helper repos to wire as autowired fields if not already). All tests use `@SpringBootTest` (the existing class likely does too — confirm and copy the same annotations). If `VoteSessionServiceTest` is a Mockito-style unit test, write a new `VoteSessionCreateSessionTest` (`@SpringBootTest`) instead — but reuse name conventions.

Required new autowired repos in the test class: `QuizQuestionRepository`, `VoteSessionActivityRepository`, `VoteSessionQuizResponseRepository`, `CategoryRepository`, `ActivityRepository`, `DestinationRepository`.

```java
    @Test
    void createSession_persistsCuratedListWithNameAndPriceSnapshots() {
        Destination destination = newDestination();
        Category nightlife = newCategory("Nightlife", "nightlife");
        attachCategory(destination, nightlife);
        Activity activity = newActivity(destination, "Tank Driving", new BigDecimal("150.00"), Set.of(nightlife));

        VoteSessionCreateRequest request = baseRequest(destination.getId());
        request.setActivityIds(List.of(activity.getId()));

        VoteSessionResponse response = voteSessionService.createSession(request);

        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        List<VoteSessionActivity> curated = voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId());

        assertThat(curated).hasSize(1);
        assertThat(curated.get(0).getActivityName()).isEqualTo("Tank Driving");
        assertThat(curated.get(0).getPrice()).isEqualByComparingTo("150.00");
        assertThat(session.getStatus()).isEqualTo(VoteSessionStatus.ACTIVE);
    }

    @Test
    void createSession_snapshotPriceFrozenAgainstLaterRePrice() {
        Destination destination = newDestination();
        Category nightlife = newCategory("Nightlife", "nightlife");
        attachCategory(destination, nightlife);
        Activity activity = newActivity(destination, "Tank Driving", new BigDecimal("150.00"), Set.of(nightlife));

        VoteSessionCreateRequest request = baseRequest(destination.getId());
        request.setActivityIds(List.of(activity.getId()));

        VoteSessionResponse response = voteSessionService.createSession(request);

        // Admin re-prices the activity
        activity.setPrice(new BigDecimal("999.00"));
        activityRepository.saveAndFlush(activity);

        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        List<VoteSessionActivity> curated = voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId());

        // Snapshot is unchanged.
        assertThat(curated.get(0).getPrice()).isEqualByComparingTo("150.00");
    }

    @Test
    void createSession_persistsOrganizerQuizResponses() {
        Destination destination = newDestination();
        Category nightlife = newCategory("Nightlife", "nightlife");
        attachCategory(destination, nightlife);
        Activity activity = newActivity(destination, "Tank", new BigDecimal("100"), Set.of(nightlife));

        QuizQuestion question = newQuestion(destination, "Vibe?");
        QuizAnswer answer = newAnswer(question, "Wild", nightlife, 2);

        VoteSessionCreateRequest request = baseRequest(destination.getId());
        request.setActivityIds(List.of(activity.getId()));
        request.setQuizResponses(List.of(new QuizResponseDTO(question.getId(), answer.getId())));

        VoteSessionResponse response = voteSessionService.createSession(request);

        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        List<VoteSessionQuizResponse> rows = voteSessionQuizResponseRepository.findBySessionId(session.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getVoterToken()).isEqualTo(request.getVoterToken());
        assertThat(rows.get(0).getAnswer().getId()).isEqualTo(answer.getId());
    }

    @Test
    void createSession_rejectsActivityFromOtherDestination_400() {
        Destination prague = newDestination();
        Destination berlin = newDestination();
        Category nightlife = newCategory("Nightlife", "nightlife");
        attachCategory(prague, nightlife);
        attachCategory(berlin, nightlife);
        Activity inBerlin = newActivity(berlin, "Berlin Club", new BigDecimal("80"), Set.of(nightlife));

        VoteSessionCreateRequest request = baseRequest(prague.getId());
        request.setActivityIds(List.of(inBerlin.getId()));

        assertThatThrownBy(() -> voteSessionService.createSession(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(inBerlin.getId().toString());
    }

    @Test
    void createSession_rejectsActivityNotInOrganizerCategories_400() {
        Destination destination = newDestination();
        Category nightlife = newCategory("Nightlife", "nightlife");
        Category chillout = newCategory("Chillout", "chillout");
        attachCategory(destination, nightlife, chillout);
        Activity inChillout = newActivity(destination, "Spa", new BigDecimal("60"), Set.of(chillout));

        QuizQuestion question = newQuestion(destination, "Vibe?");
        QuizAnswer answer = newAnswer(question, "Wild", nightlife, 2);   // pushes Nightlife

        VoteSessionCreateRequest request = baseRequest(destination.getId());
        request.setActivityIds(List.of(inChillout.getId()));
        request.setQuizResponses(List.of(new QuizResponseDTO(question.getId(), answer.getId())));

        assertThatThrownBy(() -> voteSessionService.createSession(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createSession_rejectsCrossDestinationQuestion_400() {
        Destination prague = newDestination();
        Destination berlin = newDestination();
        Category nightlife = newCategory("Nightlife", "nightlife");
        attachCategory(prague, nightlife);
        Activity activity = newActivity(prague, "Club", new BigDecimal("100"), Set.of(nightlife));

        QuizQuestion berlinQuestion = newQuestion(berlin, "Vibe?");
        QuizAnswer berlinAnswer = newAnswer(berlinQuestion, "Wild", nightlife, 2);

        VoteSessionCreateRequest request = baseRequest(prague.getId());
        request.setActivityIds(List.of(activity.getId()));
        request.setQuizResponses(List.of(new QuizResponseDTO(berlinQuestion.getId(), berlinAnswer.getId())));

        assertThatThrownBy(() -> voteSessionService.createSession(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createSession_ignoresLikedCategoryIds() {
        Destination destination = newDestination();
        Category nightlife = newCategory("Nightlife", "nightlife");
        attachCategory(destination, nightlife);
        Activity activity = newActivity(destination, "Club", new BigDecimal("100"), Set.of(nightlife));

        VoteSessionCreateRequest request = baseRequest(destination.getId());
        request.setActivityIds(List.of(activity.getId()));
        request.setLikedCategoryIds(List.of(UUID.randomUUID()));   // garbage — must not error

        VoteSessionResponse response = voteSessionService.createSession(request);
        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        assertThat(session.getLikedCategories()).isEmpty();
    }

    // baseRequest builds a valid VoteSessionCreateRequest WITHOUT activityIds / quizResponses set
    // so individual tests fill those in. Use distinct emails to avoid collisions.
    private VoteSessionCreateRequest baseRequest(UUID destinationId) {
        VoteSessionCreateRequest req = new VoteSessionCreateRequest();
        req.setDestinationId(destinationId);
        req.setInitiatorEmail("test+" + UUID.randomUUID() + "@example.com");
        req.setNumberOfTravelers(2);
        req.setStartDate(LocalDate.of(2026, 8, 1));
        req.setEndDate(LocalDate.of(2026, 8, 10));
        req.setBudget(new BigDecimal("3000.00"));
        req.setVoterToken(UUID.randomUUID());
        req.setQuizResponses(List.of());
        return req;
    }
```

For the helper methods (`newDestination`, `newCategory`, `attachCategory`, `newActivity`, `newQuestion`, `newAnswer`), reuse the ones from `VotePoolServiceTest` if a shared test helper class makes sense — otherwise duplicate. Don't extract a `TestDataFactory` in this plan; do it as a separate cleanup if both tests grow large.

- [ ] **Step 2: Run red**

Run: `./gradlew test --tests '*VoteSessionServiceTest'`
Expected: FAIL — current `createSession` doesn't use `activityIds` (it uses the now-deprecated `likedCategoryIds` path), so the new tests fail.

- [ ] **Step 3: Rewrite `createSession`**

In `VoteSessionService.java`:

1. **Add imports** (alphabetical): `com.myhive.backend.dto.QuizResponseDTO`, `com.myhive.backend.entity.Activity` (probably already), `com.myhive.backend.entity.QuizAnswer`, `com.myhive.backend.entity.QuizQuestion`, `com.myhive.backend.entity.VoteSessionActivity`, `com.myhive.backend.entity.VoteSessionQuizResponse`, `com.myhive.backend.repository.QuizAnswerRepository` (new — see step 4), `com.myhive.backend.repository.QuizQuestionRepository`, `com.myhive.backend.repository.VoteSessionActivityRepository`, `com.myhive.backend.repository.VoteSessionQuizResponseRepository`, `com.myhive.backend.service.QuizService`, `java.util.HashSet`, `java.util.LinkedHashSet`.

2. **Add new `private final` repos / services** to the constructor-injected fields:
   - `private final QuizService quizService;`
   - `private final QuizQuestionRepository quizQuestionRepository;`
   - `private final QuizAnswerRepository quizAnswerRepository;` *(create the repo in step 4)*
   - `private final VoteSessionActivityRepository voteSessionActivityRepository;`
   - `private final VoteSessionQuizResponseRepository voteSessionQuizResponseRepository;`

3. **Replace `createSession` body** with:

```java
    @Transactional
    public VoteSessionResponse createSession(VoteSessionCreateRequest request) {
        Destination destination = destinationRepository.findById(request.getDestinationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found"));

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }

        List<QuizResponseDTO> quizResponses = request.getQuizResponses() == null
                ? List.of() : request.getQuizResponses();
        validateQuizResponses(destination, quizResponses);

        List<UUID> organizerCats = computeOrganizerCategories(destination, quizResponses);

        Map<UUID, Activity> activitiesById = loadAndValidateCuratedActivities(
                destination, organizerCats, request.getActivityIds());

        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail(request.getInitiatorEmail());
        session.setNumberOfTravelers(request.getNumberOfTravelers());
        session.setStartDate(request.getStartDate());
        session.setEndDate(request.getEndDate());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(24));
        session.setBudget(request.getBudget());
        session = voteSessionRepository.save(session);

        int sortOrder = 0;
        for (UUID activityId : request.getActivityIds()) {
            Activity activity = activitiesById.get(activityId);
            VoteSessionActivity row = new VoteSessionActivity();
            row.setSession(session);
            row.setActivity(activity);
            row.setActivityName(activity.getName());
            row.setPrice(activity.getPrice());
            row.setSortOrder(sortOrder++);
            voteSessionActivityRepository.save(row);
        }

        for (QuizResponseDTO response : quizResponses) {
            QuizQuestion question = quizQuestionRepository.findById(response.getQuestionId()).orElseThrow();
            QuizAnswer answer = quizAnswerRepository.findById(response.getAnswerId()).orElseThrow();
            VoteSessionQuizResponse row = new VoteSessionQuizResponse();
            row.setSession(session);
            row.setVoterToken(request.getVoterToken());
            row.setQuestion(question);
            row.setAnswer(answer);
            voteSessionQuizResponseRepository.save(row);
        }

        long participantCount = voteActivityLikeRepository
                .countDistinctVoterTokensBySessionId(session.getId());
        return toResponse(session, participantCount, session.getManagerToken());
    }

    private void validateQuizResponses(Destination destination, List<QuizResponseDTO> responses) {
        if (responses.isEmpty()) {
            return;
        }
        List<QuizQuestion> destinationQuiz =
                quizQuestionRepository.findByDestinationIdOrderBySortOrder(destination.getId());
        Set<UUID> destinationQuestionIds = destinationQuiz.stream()
                .map(QuizQuestion::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<UUID> seenQuestions = new HashSet<>();
        for (QuizResponseDTO response : responses) {
            if (!destinationQuestionIds.contains(response.getQuestionId())) {
                throw new BadRequestException(
                        "questionId " + response.getQuestionId() + " is not part of this destination's quiz");
            }
            if (!seenQuestions.add(response.getQuestionId())) {
                throw new BadRequestException(
                        "two responses provided for questionId " + response.getQuestionId());
            }
            QuizAnswer answer = quizAnswerRepository.findById(response.getAnswerId())
                    .orElseThrow(() -> new BadRequestException(
                            "answerId " + response.getAnswerId() + " does not exist"));
            if (!answer.getQuestion().getId().equals(response.getQuestionId())) {
                throw new BadRequestException(
                        "answerId " + response.getAnswerId() + " does not belong to questionId " + response.getQuestionId());
            }
        }
        if (!destinationQuiz.isEmpty() && seenQuestions.size() != destinationQuestionIds.size()) {
            throw new BadRequestException("quizResponses is incomplete — every destination question must be answered");
        }
    }

    private List<UUID> computeOrganizerCategories(Destination destination, List<QuizResponseDTO> responses) {
        List<UUID> answerIds = responses.stream().map(QuizResponseDTO::getAnswerId).toList();
        List<UUID> snapshot = quizService.snapshot(answerIds);
        if (!snapshot.isEmpty()) {
            return snapshot;
        }
        return destination.getCategories().stream()
                .filter(Category::isVotable)
                .map(Category::getId)
                .toList();
    }

    private Map<UUID, Activity> loadAndValidateCuratedActivities(Destination destination,
                                                                 List<UUID> organizerCats,
                                                                 List<UUID> activityIds) {
        Set<UUID> organizerCatSet = new HashSet<>(organizerCats);
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
            boolean hasEligibleCategory = activity.getCategories().stream()
                    .map(Category::getId)
                    .anyMatch(organizerCatSet::contains);
            if (!hasEligibleCategory) {
                throw new BadRequestException(
                        "activityId " + id + " is not in any of the organizer's quiz categories");
            }
        }
        return byId;
    }
```

The old `resolveDestinationCategoryIds` helper is now used only by `getActivities` (Task 10). Leave it.

The old `likedCategoryIds` path is removed from `createSession` — the field is read from the request and **silently ignored**. (Don't delete the field from the DTO; we keep it one release per spec.)

4. **Create `QuizAnswerRepository.java`** (new):

```java
package com.myhive.backend.repository;

import com.myhive.backend.entity.QuizAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, UUID> {
}
```

- [ ] **Step 4: Run the test — green**

Run: `./gradlew test --tests '*VoteSessionServiceTest'`
Expected: PASS — every new test green; existing tests in this class may need small adjustments because `createSession` no longer reads `likedCategoryIds`. Adjust by either populating `activityIds` (preferred — drive the existing test through the new path) or by deleting the test if it covered something only meaningful in the old flow.

Then `./gradlew test` — full suite green.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java myhive-backend/src/main/java/com/myhive/backend/repository/QuizAnswerRepository.java myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionServiceTest.java
git commit -m "feat: atomic POST /vote/sessions with curated list and quiz snapshots"
```

---

## Task 10: `getActivities` reads the curated list

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java`
- Modify: existing `VoteSessionControllerTest` (or `VoteSessionServiceTest`) to assert curated-list reads

For sessions written by the new flow, `getActivities(shareToken)` must return the curated list (in `sort_order`). Historical sessions (which only have `vote_session_liked_categories`) fall back to the old category-based path so the legacy endpoint still serves them.

- [ ] **Step 1: Write the failing test**

Add to `VoteSessionServiceTest`:

```java
    @Test
    void getActivities_newSession_returnsCuratedListInOrder() {
        Destination destination = newDestination();
        Category nightlife = newCategory("Nightlife", "nightlife");
        attachCategory(destination, nightlife);
        Activity a1 = newActivity(destination, "First", new BigDecimal("100"), Set.of(nightlife));
        Activity a2 = newActivity(destination, "Second", new BigDecimal("200"), Set.of(nightlife));

        VoteSessionCreateRequest request = baseRequest(destination.getId());
        request.setActivityIds(List.of(a2.getId(), a1.getId()));   // intentional order

        VoteSessionResponse response = voteSessionService.createSession(request);

        List<VoteActivityResponse> activities = voteSessionService.getActivities(response.getShareToken());

        assertThat(activities).extracting(VoteActivityResponse::getId)
                .containsExactly(a2.getId(), a1.getId());
    }
```

- [ ] **Step 2: Run red**

Run: `./gradlew test --tests '*VoteSessionServiceTest'`
Expected: FAIL — current `getActivities` reads liked categories, not the curated list.

- [ ] **Step 3: Update `getActivities`**

Replace the existing method body:

```java
    public List<VoteActivityResponse> getActivities(UUID shareToken) {
        VoteSession session = findByShareToken(shareToken);
        String destinationSlug = session.getDestination().getSlug();

        List<VoteSessionActivity> curated = voteSessionActivityRepository
                .findBySessionIdOrderBySortOrder(session.getId());
        if (!curated.isEmpty()) {
            return curated.stream()
                    .map(row -> toActivityResponse(row.getActivity(), destinationSlug))
                    .toList();
        }

        // Legacy fallback for historical sessions written under the old category-swipe flow.
        Set<UUID> categoryIds = session.getLikedCategories().stream()
                .map(Category::getId)
                .collect(Collectors.toSet());
        List<Activity> activities = activityRepository.findByDestinationIdAndCategoriesIdIn(
                session.getDestination().getId(), categoryIds);
        return activities.stream()
                .map(activity -> toActivityResponse(activity, destinationSlug))
                .toList();
    }
```

`VoteSessionActivity` import is already added in Task 9.

- [ ] **Step 4: Run green**

Run: `./gradlew test --tests '*VoteSessionServiceTest'`
Expected: PASS.

Then `./gradlew test` — full suite green.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionServiceTest.java
git commit -m "feat: serve curated list from new sessions, legacy category fallback"
```

---

## Self-Review

**Spec coverage (Plan 2 portion):**
- `snapshot()` reducer — Task 1 ✓
- New tables `vote_session_quiz_responses`, `vote_session_activities` — Task 2 ✓
- Public quiz read (weights stripped) — Tasks 3 + 4 ✓
- Pool DTOs + stateless `POST /vote/pool` — Tasks 5 + 6 + 7 ✓
- Atomic `POST /vote/sessions` rewrite (eligibility validation, snapshots, organizer quiz persistence) — Task 9 ✓
- Curated-list read for new sessions — Task 10 ✓
- `likedCategoryIds` tolerated and ignored — Task 8 (DTO) + Task 9 (service) ✓
- **Deferred to Plan 3:** participant quiz `GET/POST /vote/sessions/{shareToken}/quiz`, result resolver rewrite, suggestions, activity-deletion guard, frontend.

**Placeholder scan:** Each step has full code; no TBD/TODO. The test-helper duplication note in Task 9 is intentional — extracting a shared `TestDataFactory` is deferred (a TestDataFactory does not currently exist in this codebase).

**Type consistency check:**
- `QuizService.snapshot(Collection<UUID>) → List<UUID>` defined Task 1, called identically in Tasks 6 (`VotePoolService`) and 9 (`VoteSessionService`).
- `QuizService` constructor goes from 3 args (Plan 1) to 4 args (Task 1 adds `quizAnswerWeightRepository`). Tasks 6 and 9 test-construct it with 4 args.
- `VotePoolRequest{destinationId, responses}` defined Task 5, consumed Task 6 + Task 7.
- `QuizResponseDTO{questionId, answerId}` defined Task 5, reused in Task 8 (`VoteSessionCreateRequest.quizResponses`) and Task 9 (validation + persistence).
- `VoteSessionActivity{session, activity, activityName, price, sortOrder}` defined Task 2, written in Task 9, read in Task 10.
- `VoteSessionQuizResponse{session, voterToken, question, answer, submittedAt}` defined Task 2, written in Task 9.
- `VoteSessionActivityRepository.findBySessionIdOrderBySortOrder(UUID)` defined Task 2, used Tasks 9 and 10.
- `VoteSessionQuizResponseRepository.findBySessionId(UUID)` defined Task 2, used in Task 9's test assertions.

Consistent across all tasks.
