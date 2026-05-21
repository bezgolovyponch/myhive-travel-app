# Quiz-Driven Voting — Plan 1: Foundation & Quiz Admin

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the three new schema columns and the quiz-definition tables, and let admins create/replace a destination's quiz via the API.

**Architecture:** Three new JPA entities (`QuizQuestion` → `QuizAnswer` → `QuizAnswerWeight`) form a destination-scoped quiz tree with cascade delete. A `QuizService` exposes a read (`getQuiz`) and a transactional bulk replace (`replaceQuiz`); two endpoints on the existing `AdminController` wire it up. Three columns (`categories.votable`, `vote_sessions.budget`, `activities.featured_weight`) are added as plain fields — Hibernate `ddl-auto=update` applies them additively.

**Tech Stack:** Spring Boot 4.0 / Java 25 / Gradle, JPA + Hibernate, Lombok, JUnit 5 + AssertJ + Mockito, H2 (tests).

**Scope notes:**
- This is Plan 1 of 4. Plan 2 adds the pool + session creation; Plan 3 the resolver + result; Plan 4 the frontend.
- The spec lists five quiz-admin endpoints. This plan implements only **GET** and **PUT (bulk replace)** — together they fully serve the admin "Quiz tab" (load whole quiz, edit, save whole quiz). The three granular question endpoints (`POST/PUT/DELETE /admin/quiz/questions`) are **omitted as redundant** with bulk replace; add them later only if a real need appears.
- Managing `featured_weight` **values** via the admin activity form and CSV import is **out of scope here** — Plan 1 only creates the `featured_weight` *column*. Default `0` is fine for Plan 2 (pool ordering ties break on `id`). Value-management is a small follow-up.
- `QuizAnswerWeight` uses a surrogate `UUID` primary key plus a unique constraint on `(answer_id, category_id)` — this matches the codebase's universal UUID-PK convention and is functionally equivalent to the spec's composite PK.

**Reference:** spec at `docs/superpowers/specs/2026-05-11-quiz-driven-voting-design.md`.

---

## Task 1: Three new schema columns

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/entity/Category.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/entity/Activity.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/entity/VoteSession.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/entity/SchemaColumnsTest.java`

- [ ] **Step 1: Write the failing test**

Create `SchemaColumnsTest.java`. It uses `@DataJpaTest` (H2) and the existing `TestDataFactory` to build a `Category` and an `Activity`. Inspect `myhive-backend/src/test/java/com/myhive/backend/util/TestDataFactory.java` (or wherever it lives) for the existing builders for `Category` and `Activity` + `Destination`; use them so required fields are filled. Then set the two new fields and assert the round-trip.

```java
package com.myhive.backend.entity;

import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.util.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SchemaColumnsTest {

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private DestinationRepository destinationRepository;

    @Test
    void category_votable_defaultsTrue_andPersists() {
        Category category = TestDataFactory.category("Transfer");   // adjust to the real factory signature
        boolean defaultBeforeSave = category.isVotable();
        category.setVotable(false);

        Category saved = categoryRepository.saveAndFlush(category);
        Category reloaded = categoryRepository.findById(saved.getId()).orElseThrow();

        assertThat(defaultBeforeSave).isTrue();        // new entities default to votable
        assertThat(reloaded.isVotable()).isFalse();    // persisted override survives
    }

    @Test
    void activity_featuredWeight_persists() {
        int expectedWeight = 7;
        Destination destination = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity activity = TestDataFactory.activity(destination, "Tank Driving");
        activity.setFeaturedWeight(expectedWeight);

        Activity saved = activityRepository.saveAndFlush(activity);
        Activity reloaded = activityRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getFeaturedWeight()).isEqualTo(expectedWeight);
    }
}
```

If `TestDataFactory` has no matching builder, build the entity inline with its required fields (`Category`: `name`; `Destination`: `name`; `Activity`: `destination`, `name`, `price`).

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*SchemaColumnsTest'`
Expected: FAIL — compilation error, `isVotable()` / `setVotable` / `getFeaturedWeight` / `setFeaturedWeight` not defined.

- [ ] **Step 3: Add the three columns**

In `Category.java`, add after the `slug` field:

```java
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean votable = true;
```

In `Activity.java`, add after the `duration` field:

```java
    @Column(name = "featured_weight", nullable = false, columnDefinition = "integer default 0")
    private int featuredWeight = 0;
```

In `VoteSession.java`, add a `budget` field (place it near the other numeric/session fields). Ensure `import java.math.BigDecimal;` is present:

```java
    @Column(name = "budget", precision = 10, scale = 2)
    private BigDecimal budget;
```

(`budget` is nullable — a session may have no budget. Its persistence is exercised by Plan 2's session-creation tests.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests '*SchemaColumnsTest'`
Expected: PASS — both tests green.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/entity/Category.java myhive-backend/src/main/java/com/myhive/backend/entity/Activity.java myhive-backend/src/main/java/com/myhive/backend/entity/VoteSession.java myhive-backend/src/test/java/com/myhive/backend/entity/SchemaColumnsTest.java
git commit -m "feat: add votable, featured_weight, budget columns"
```

---

## Task 2: Quiz entities + repository

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/QuizQuestion.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/QuizAnswer.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/QuizAnswerWeight.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/entity/Destination.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/repository/QuizQuestionRepository.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/repository/QuizSchemaTest.java`

- [ ] **Step 1: Write the failing test**

Create `QuizSchemaTest.java` — a `@DataJpaTest` that persists a full quiz tree and reads it back, confirming cascade and ordering.

```java
package com.myhive.backend.repository;

import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizAnswerWeight;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.util.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class QuizSchemaTest {

    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;

    @Test
    void quizTree_persistsAndCascades() {
        Destination destination = destinationRepository.save(TestDataFactory.destination("Prague"));
        Category category = categoryRepository.save(TestDataFactory.category("Nightlife"));

        QuizQuestion question = new QuizQuestion();
        question.setDestination(destination);
        question.setPrompt("Daytime hero or 4am legend?");
        question.setSortOrder(0);

        QuizAnswer answer = new QuizAnswer();
        answer.setQuestion(question);
        answer.setLabel("4am legend");
        answer.setSortOrder(0);

        QuizAnswerWeight weight = new QuizAnswerWeight();
        weight.setAnswer(answer);
        weight.setCategory(category);
        weight.setWeight(2);

        answer.getWeights().add(weight);
        question.getAnswers().add(answer);
        QuizQuestion saved = quizQuestionRepository.saveAndFlush(question);

        QuizQuestion reloaded = quizQuestionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAnswers()).hasSize(1);
        assertThat(reloaded.getAnswers().get(0).getWeights()).hasSize(1);
        assertThat(reloaded.getAnswers().get(0).getWeights().get(0).getWeight()).isEqualTo(2);

        quizQuestionRepository.delete(reloaded);
        quizQuestionRepository.flush();
        List<QuizQuestion> remaining = quizQuestionRepository.findByDestinationIdOrderBySortOrder(destination.getId());
        assertThat(remaining).isEmpty();   // cascade deleted answers + weights with the question
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*QuizSchemaTest'`
Expected: FAIL — `QuizQuestion`, `QuizAnswer`, `QuizAnswerWeight`, `QuizQuestionRepository` do not exist.

- [ ] **Step 3: Create the three entities, the repository, and the Destination link**

`QuizQuestion.java`:

```java
package com.myhive.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quiz_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"destination", "answers"})
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id", nullable = false)
    private Destination destination;

    @Column(nullable = false, length = 500)
    private String prompt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuizAnswer> answers = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
```

`QuizAnswer.java`:

```java
package com.myhive.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quiz_answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"question", "weights"})
public class QuizAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "answer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuizAnswerWeight> weights = new ArrayList<>();
}
```

`QuizAnswerWeight.java`:

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

import java.util.UUID;

@Entity
@Table(name = "quiz_answer_weights",
        uniqueConstraints = @UniqueConstraint(columnNames = {"answer_id", "category_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"answer", "category"})
public class QuizAnswerWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private QuizAnswer answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private int weight;
}
```

In `Destination.java`, add this field (so deleting a destination cascade-deletes its quiz). Ensure `CascadeType`, `OneToMany`, `ArrayList`, `List` are imported:

```java
    @OneToMany(mappedBy = "destination", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<QuizQuestion> quizQuestions = new ArrayList<>();
```

`QuizQuestionRepository.java`:

```java
package com.myhive.backend.repository;

import com.myhive.backend.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    List<QuizQuestion> findByDestinationIdOrderBySortOrder(UUID destinationId);
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests '*QuizSchemaTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/entity/QuizQuestion.java myhive-backend/src/main/java/com/myhive/backend/entity/QuizAnswer.java myhive-backend/src/main/java/com/myhive/backend/entity/QuizAnswerWeight.java myhive-backend/src/main/java/com/myhive/backend/entity/Destination.java myhive-backend/src/main/java/com/myhive/backend/repository/QuizQuestionRepository.java myhive-backend/src/test/java/com/myhive/backend/repository/QuizSchemaTest.java
git commit -m "feat: add quiz_questions, quiz_answers, quiz_answer_weights tables"
```

---

## Task 3: Quiz DTOs

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/QuizDTO.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/QuizQuestionDTO.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/QuizAnswerDTO.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/QuizAnswerWeightDTO.java`

These are plain data carriers (Lombok `@Data`) — no behavior, so no separate test; they are exercised by Tasks 4–6.

- [ ] **Step 1: Create the four DTO classes**

`QuizAnswerWeightDTO.java`:

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
public class QuizAnswerWeightDTO {

    @NotNull(message = "categoryId is required")
    private UUID categoryId;

    private int weight;
}
```

`QuizAnswerDTO.java`:

```java
package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizAnswerDTO {

    private UUID id;   // null when creating

    @NotBlank(message = "Answer label is required")
    @Size(max = 200, message = "Answer label must be at most 200 characters")
    private String label;

    private int sortOrder;

    @Valid
    private List<QuizAnswerWeightDTO> weights;
}
```

`QuizQuestionDTO.java`:

```java
package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestionDTO {

    private UUID id;   // null when creating

    @NotBlank(message = "Question prompt is required")
    @Size(max = 500, message = "Prompt must be at most 500 characters")
    private String prompt;

    private int sortOrder;

    @Valid
    @NotEmpty(message = "A question must have at least one answer")
    private List<QuizAnswerDTO> answers;
}
```

`QuizDTO.java`:

```java
package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizDTO {

    @Valid
    @NotNull(message = "questions is required (use an empty list to clear the quiz)")
    private List<QuizQuestionDTO> questions;
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/QuizDTO.java myhive-backend/src/main/java/com/myhive/backend/dto/QuizQuestionDTO.java myhive-backend/src/main/java/com/myhive/backend/dto/QuizAnswerDTO.java myhive-backend/src/main/java/com/myhive/backend/dto/QuizAnswerWeightDTO.java
git commit -m "feat: add quiz admin DTOs"
```

---

## Task 4: QuizService.getQuiz

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/QuizService.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/QuizServiceTest.java`

`QuizServiceTest` is a `@DataJpaTest` that constructs `QuizService` directly from the real autowired repositories — `QuizService` is persistence-heavy, so a real H2 database is clearer than mocking.

- [ ] **Step 1: Write the failing test**

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.QuizDTO;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizAnswerWeight;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import com.myhive.backend.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class QuizServiceTest {

    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;

    private QuizService quizService;

    @BeforeEach
    void setUp() {
        quizService = new QuizService(quizQuestionRepository, destinationRepository, categoryRepository);
    }

    @Test
    void getQuiz_unknownDestination_throwsNotFound() {
        assertThatThrownBy(() -> quizService.getQuiz(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getQuiz_returnsQuestionsOrderedWithAnswersAndWeights() {
        Destination destination = destinationRepository.save(TestDataFactory.destination("Prague"));
        Category category = categoryRepository.save(TestDataFactory.category("Nightlife"));

        QuizQuestion question = new QuizQuestion();
        question.setDestination(destination);
        question.setPrompt("Daytime hero or 4am legend?");
        question.setSortOrder(0);
        QuizAnswer answer = new QuizAnswer();
        answer.setQuestion(question);
        answer.setLabel("4am legend");
        answer.setSortOrder(0);
        QuizAnswerWeight weight = new QuizAnswerWeight();
        weight.setAnswer(answer);
        weight.setCategory(category);
        weight.setWeight(2);
        answer.getWeights().add(weight);
        question.getAnswers().add(answer);
        quizQuestionRepository.saveAndFlush(question);

        QuizDTO quiz = quizService.getQuiz(destination.getId());

        assertThat(quiz.getQuestions()).hasSize(1);
        assertThat(quiz.getQuestions().get(0).getPrompt()).isEqualTo("Daytime hero or 4am legend?");
        assertThat(quiz.getQuestions().get(0).getAnswers()).hasSize(1);
        assertThat(quiz.getQuestions().get(0).getAnswers().get(0).getWeights()).hasSize(1);
        assertThat(quiz.getQuestions().get(0).getAnswers().get(0).getWeights().get(0).getCategoryId())
                .isEqualTo(category.getId());
        assertThat(quiz.getQuestions().get(0).getAnswers().get(0).getWeights().get(0).getWeight())
                .isEqualTo(2);
    }

    @Test
    void getQuiz_noQuiz_returnsEmptyQuestions() {
        Destination destination = destinationRepository.save(TestDataFactory.destination("Berlin"));

        QuizDTO quiz = quizService.getQuiz(destination.getId());

        assertThat(quiz.getQuestions()).isEmpty();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*QuizServiceTest'`
Expected: FAIL — `QuizService` does not exist.

- [ ] **Step 3: Create `QuizService` with `getQuiz`**

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.QuizAnswerDTO;
import com.myhive.backend.dto.QuizAnswerWeightDTO;
import com.myhive.backend.dto.QuizDTO;
import com.myhive.backend.dto.QuizQuestionDTO;
import com.myhive.backend.entity.QuizAnswer;
import com.myhive.backend.entity.QuizQuestion;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.QuizQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

    private final QuizQuestionRepository quizQuestionRepository;
    private final DestinationRepository destinationRepository;
    private final CategoryRepository categoryRepository;

    public QuizDTO getQuiz(UUID destinationId) {
        if (!destinationRepository.existsById(destinationId)) {
            throw new ResourceNotFoundException("Destination", destinationId);
        }
        List<QuizQuestionDTO> questions = quizQuestionRepository
                .findByDestinationIdOrderBySortOrder(destinationId)
                .stream()
                .map(this::convertToDTO)
                .toList();
        return new QuizDTO(questions);
    }

    private QuizQuestionDTO convertToDTO(QuizQuestion question) {
        List<QuizAnswerDTO> answers = question.getAnswers().stream()
                .sorted(Comparator.comparingInt(QuizAnswer::getSortOrder))
                .map(answer -> {
                    List<QuizAnswerWeightDTO> weights = answer.getWeights().stream()
                            .map(w -> new QuizAnswerWeightDTO(w.getCategory().getId(), w.getWeight()))
                            .toList();
                    return new QuizAnswerDTO(answer.getId(), answer.getLabel(),
                            answer.getSortOrder(), weights);
                })
                .toList();
        return new QuizQuestionDTO(question.getId(), question.getPrompt(),
                question.getSortOrder(), answers);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests '*QuizServiceTest'`
Expected: PASS — all three tests green.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/QuizService.java myhive-backend/src/test/java/com/myhive/backend/service/QuizServiceTest.java
git commit -m "feat: add QuizService.getQuiz"
```

---

## Task 5: QuizService.replaceQuiz

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/QuizService.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/QuizServiceTest.java`

- [ ] **Step 1: Add the failing tests**

Add these methods to `QuizServiceTest`:

```java
    @Test
    void replaceQuiz_unknownCategoryWeight_throwsBadRequest() {
        Destination destination = destinationRepository.save(TestDataFactory.destination("Prague"));
        QuizAnswerWeightDTO weight = new QuizAnswerWeightDTO(UUID.randomUUID(), 2);
        QuizAnswerDTO answer = new QuizAnswerDTO(null, "4am legend", 0, java.util.List.of(weight));
        QuizQuestionDTO question = new QuizQuestionDTO(null, "Daytime or 4am?", 0, java.util.List.of(answer));
        QuizDTO dto = new QuizDTO(java.util.List.of(question));

        assertThatThrownBy(() -> quizService.replaceQuiz(destination.getId(), dto))
                .isInstanceOf(com.myhive.backend.exception.BadRequestException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    void replaceQuiz_replacesExistingQuiz() {
        Destination destination = destinationRepository.save(TestDataFactory.destination("Prague"));
        Category category = categoryRepository.save(TestDataFactory.category("Nightlife"));

        // existing quiz: one question
        QuizQuestion old = new QuizQuestion();
        old.setDestination(destination);
        old.setPrompt("OLD QUESTION");
        old.setSortOrder(0);
        QuizAnswer oldAnswer = new QuizAnswer();
        oldAnswer.setQuestion(old);
        oldAnswer.setLabel("old");
        oldAnswer.setSortOrder(0);
        old.getAnswers().add(oldAnswer);
        quizQuestionRepository.saveAndFlush(old);

        // replacement: a different single question with a weighted answer
        QuizAnswerWeightDTO weight = new QuizAnswerWeightDTO(category.getId(), 2);
        QuizAnswerDTO answer = new QuizAnswerDTO(null, "4am legend", 0, java.util.List.of(weight));
        QuizQuestionDTO question = new QuizQuestionDTO(null, "NEW QUESTION", 0, java.util.List.of(answer));
        QuizDTO dto = new QuizDTO(java.util.List.of(question));

        QuizDTO result = quizService.replaceQuiz(destination.getId(), dto);

        assertThat(result.getQuestions()).hasSize(1);
        assertThat(result.getQuestions().get(0).getPrompt()).isEqualTo("NEW QUESTION");
        assertThat(quizQuestionRepository.findByDestinationIdOrderBySortOrder(destination.getId()))
                .extracting(QuizQuestion::getPrompt)
                .containsExactly("NEW QUESTION");   // old question gone
    }

    @Test
    void replaceQuiz_emptyQuestions_clearsQuiz() {
        Destination destination = destinationRepository.save(TestDataFactory.destination("Prague"));
        QuizQuestion old = new QuizQuestion();
        old.setDestination(destination);
        old.setPrompt("OLD");
        old.setSortOrder(0);
        QuizAnswer oldAnswer = new QuizAnswer();
        oldAnswer.setQuestion(old);
        oldAnswer.setLabel("old");
        oldAnswer.setSortOrder(0);
        old.getAnswers().add(oldAnswer);
        quizQuestionRepository.saveAndFlush(old);

        QuizDTO result = quizService.replaceQuiz(destination.getId(), new QuizDTO(java.util.List.of()));

        assertThat(result.getQuestions()).isEmpty();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests '*QuizServiceTest'`
Expected: FAIL — `replaceQuiz` does not exist.

- [ ] **Step 3: Add `replaceQuiz` to `QuizService`**

Add these imports to `QuizService`:

```java
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.QuizAnswerWeight;
import com.myhive.backend.exception.BadRequestException;
```

Add this method:

```java
    @Transactional
    public QuizDTO replaceQuiz(UUID destinationId, QuizDTO dto) {
        Destination destination = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination", destinationId));

        // validate every referenced category exists before touching the DB
        for (QuizQuestionDTO questionDto : dto.getQuestions()) {
            for (QuizAnswerDTO answerDto : questionDto.getAnswers()) {
                if (answerDto.getWeights() == null) {
                    continue;
                }
                for (QuizAnswerWeightDTO weightDto : answerDto.getWeights()) {
                    if (!categoryRepository.existsById(weightDto.getCategoryId())) {
                        throw new BadRequestException(
                                "Category not found: " + weightDto.getCategoryId());
                    }
                }
            }
        }

        // delete the existing quiz (cascades answers + weights)
        quizQuestionRepository.deleteAll(
                quizQuestionRepository.findByDestinationIdOrderBySortOrder(destinationId));
        quizQuestionRepository.flush();

        // build and persist the replacement
        for (QuizQuestionDTO questionDto : dto.getQuestions()) {
            QuizQuestion question = new QuizQuestion();
            question.setDestination(destination);
            question.setPrompt(questionDto.getPrompt());
            question.setSortOrder(questionDto.getSortOrder());
            for (QuizAnswerDTO answerDto : questionDto.getAnswers()) {
                QuizAnswer answer = new QuizAnswer();
                answer.setQuestion(question);
                answer.setLabel(answerDto.getLabel());
                answer.setSortOrder(answerDto.getSortOrder());
                if (answerDto.getWeights() != null) {
                    for (QuizAnswerWeightDTO weightDto : answerDto.getWeights()) {
                        Category category = categoryRepository.findById(weightDto.getCategoryId())
                                .orElseThrow();   // pre-validated above
                        QuizAnswerWeight weight = new QuizAnswerWeight();
                        weight.setAnswer(answer);
                        weight.setCategory(category);
                        weight.setWeight(weightDto.getWeight());
                        answer.getWeights().add(weight);
                    }
                }
                question.getAnswers().add(answer);
            }
            quizQuestionRepository.save(question);
        }
        return getQuiz(destinationId);
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests '*QuizServiceTest'`
Expected: PASS — all six tests green.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/QuizService.java myhive-backend/src/test/java/com/myhive/backend/service/QuizServiceTest.java
git commit -m "feat: add QuizService.replaceQuiz"
```

---

## Task 6: Admin quiz endpoints

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/controller/AdminController.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/controller/QuizAdminControllerIntegrationTest.java`

No `SecurityConfig` change — `/admin/destinations/**` falls under the existing `/admin/**` → `hasRole("ADMIN")` rule. (Confirm `SecurityConfig` has no earlier MANAGER carve-out for `/admin/destinations`; the carve-outs are for `/admin/activities`, `/admin/blog`, `/admin/upload`.)

- [ ] **Step 1: Write the failing test**

```java
package com.myhive.backend.controller;

import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.myhive.backend.util.JwtTestHelper.adminJwt;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestSecurityConfig.class)
class QuizAdminControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private CategoryRepository categoryRepository;

    private UUID destinationId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        Destination destination = destinationRepository.save(TestDataFactory.destination("Prague"));
        Category category = categoryRepository.save(TestDataFactory.category("Nightlife"));
        destinationId = destination.getId();
        categoryId = category.getId();
    }

    @Test
    void putThenGetQuiz_withAdminAuth_roundTrips() throws Exception {
        String body = """
                {
                  "questions": [
                    {
                      "prompt": "Daytime hero or 4am legend?",
                      "sortOrder": 0,
                      "answers": [
                        { "label": "4am legend", "sortOrder": 0,
                          "weights": [ { "categoryId": "%s", "weight": 2 } ] }
                      ]
                    }
                  ]
                }
                """.formatted(categoryId);

        mockMvc.perform(put("/admin/destinations/" + destinationId + "/quiz")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].prompt", is("Daytime hero or 4am legend?")));

        mockMvc.perform(get("/admin/destinations/" + destinationId + "/quiz")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].answers[0].weights[0].weight", is(2)));
    }

    @Test
    void getQuiz_withoutAuth_isUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/destinations/" + destinationId + "/quiz"))
                .andExpect(status().isUnauthorized());
    }
}
```

Note: match the imports/auth-helper to a sibling controller integration test (e.g. `AdminControllerIntegrationTest`) — the exact package of `AutoConfigureMockMvc`, the `adminJwt()` helper, and `TestSecurityConfig` should be copied from there if the ones above differ.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*QuizAdminControllerIntegrationTest'`
Expected: FAIL — 404, the endpoints don't exist.

- [ ] **Step 3: Add the two endpoints to `AdminController`**

Add a `private final QuizService quizService;` field (constructor injection is automatic via the class's `@RequiredArgsConstructor`), the import `import com.myhive.backend.service.QuizService;` and `import com.myhive.backend.dto.QuizDTO;`, then these two handlers:

```java
    @GetMapping("/destinations/{destinationId}/quiz")
    public ResponseEntity<QuizDTO> getQuiz(@PathVariable UUID destinationId) {
        return ResponseEntity.ok(quizService.getQuiz(destinationId));
    }

    @PutMapping("/destinations/{destinationId}/quiz")
    public ResponseEntity<QuizDTO> replaceQuiz(@PathVariable UUID destinationId,
                                               @Valid @RequestBody QuizDTO quizDTO) {
        return ResponseEntity.ok(quizService.replaceQuiz(destinationId, quizDTO));
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests '*QuizAdminControllerIntegrationTest'`
Expected: PASS — both tests green.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/controller/AdminController.java myhive-backend/src/test/java/com/myhive/backend/controller/QuizAdminControllerIntegrationTest.java
git commit -m "feat: add admin quiz GET/PUT endpoints"
```

---

## Task 7: Dev seed — Prague quiz in data.sql

**Files:**
- Modify: `myhive-backend/src/main/resources/data.sql`

This seeds a quiz for the dev profile so developers see a working quiz without calling the admin API. Prod gets a separate one-off SQL script (an ops artifact, not part of this plan).

- [ ] **Step 1: Identify dev UUIDs**

Open `data.sql`. Pick one destination — copy its `id` literal from the `INSERT INTO destinations ...` block. Pick three categories — copy their `id` literals from the `INSERT INTO categories ...` block. You will substitute these into the template below.

- [ ] **Step 2: Append the quiz seed**

At the end of `data.sql`, append the block below. Replace `<DEST_ID>` with the destination UUID and `<CAT_1>`, `<CAT_2>`, `<CAT_3>` with three category UUIDs from Step 1. The quiz-row UUIDs (`d0…`) are new and used as-is.

```sql
-- Quiz: 2 sample questions for one destination (dev only)
INSERT INTO quiz_questions (id, destination_id, prompt, sort_order, created_at)
VALUES ('d0000000-0000-0000-0000-000000000001', '<DEST_ID>', 'Daytime hero or 4am legend?', 0, CURRENT_TIMESTAMP),
       ('d0000000-0000-0000-0000-000000000002', '<DEST_ID>', 'Adrenaline rush or zero risk?', 1, CURRENT_TIMESTAMP);

INSERT INTO quiz_answers (id, question_id, label, sort_order)
VALUES ('d1000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', 'Daytime', 0),
       ('d1000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000001', '4am legend', 1),
       ('d1000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000002', 'Adrenaline', 0),
       ('d1000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000002', 'Zero risk', 1);

INSERT INTO quiz_answer_weights (id, answer_id, category_id, weight)
VALUES ('d2000000-0000-0000-0000-000000000001', 'd1000000-0000-0000-0000-000000000001', '<CAT_1>', 2),
       ('d2000000-0000-0000-0000-000000000002', 'd1000000-0000-0000-0000-000000000002', '<CAT_2>', 2),
       ('d2000000-0000-0000-0000-000000000003', 'd1000000-0000-0000-0000-000000000003', '<CAT_3>', 2),
       ('d2000000-0000-0000-0000-000000000004', 'd1000000-0000-0000-0000-000000000004', '<CAT_1>', 2);
```

- [ ] **Step 3: Verify the dev server boots**

Run: `./gradlew bootRun --args='--spring.profiles.active=dev'`
Expected: starts with no SQL error. Stop it (Ctrl+C). Then confirm via the API the quiz loaded:
`GET /admin/destinations/<DEST_ID>/quiz` (with admin auth) returns the two questions.

- [ ] **Step 4: Commit**

```bash
git add myhive-backend/src/main/resources/data.sql
git commit -m "chore: seed a sample quiz for dev"
```

---

## Self-Review

**Spec coverage (Plan 1 portion):**
- `quiz_questions`, `quiz_answers`, `quiz_answer_weights` tables — Task 2. ✓
- `categories.votable`, `vote_sessions.budget`, `activities.featured_weight` columns — Task 1. ✓
- `snapshot()` algorithm — **deferred to Plan 2** (first needed by the pool). ✓ (intentional)
- Quiz admin `GET` / `PUT` — Task 6. ✓
- Granular `POST/PUT/DELETE /admin/quiz/questions` — **omitted** (redundant with bulk replace; documented in Scope notes). ✓
- `featured_weight` in activity admin DTO + CSV — **out of scope**, documented. ✓
- Prague dev seed — Task 7. ✓ Prod seed script — ops artifact, out of plan scope.

**Placeholder scan:** The only substitutions are dev-data UUIDs in Task 7 (Step 1 tells the engineer exactly where to read them) and the `TestDataFactory` builder signatures (the engineer confirms against the real class) — these are environment lookups, not logic placeholders. No TBD/TODO.

**Type consistency:** `QuizDTO.questions` / `QuizQuestionDTO.answers` / `QuizAnswerDTO.weights` / `QuizAnswerWeightDTO.categoryId+weight` are used identically in Tasks 3, 4, 5, 6. `QuizQuestionRepository.findByDestinationIdOrderBySortOrder` is defined in Task 2 and used in Tasks 4–5. `QuizService` constructor `(quizQuestionRepository, destinationRepository, categoryRepository)` matches between Task 4 definition and the Task 4/5 test `new QuizService(...)` calls. Consistent.
