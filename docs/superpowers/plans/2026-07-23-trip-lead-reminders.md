# Trip Lead Reminders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Capture emails from the quiz vote flow and completed votes server-side, and send an abandoned-trip reminder email series with a cross-device restore link.

**Architecture:** New `TripLead` entity (+ item snapshot table + suppression table) captured via public `/leads` endpoints and from `VoteSessionService.processSession`; a `@Scheduled` job advances a per-source cadence (QUIZ 1h/24h/72h, VOTE 24h/72h) through the existing `EmailService`/Resend; the email CTA `/destination/{slug}?tab=trip-builder&restore={token}` rehydrates localStorage from the server snapshot.

**Tech Stack:** Spring Boot 4.0 / Java 25 / JPA / Thymeleaf / H2+Postgres; React 19 (CRA), Jest + RTL.

**Spec:** `docs/superpowers/specs/2026-07-23-trip-lead-reminders-design.md` — read it first.

## Global Constraints

- No wildcard imports; explicit import per symbol.
- Google Java Style: `@Override` always, one variable per declaration, braces always, K&R braces.
- Test style: `expected`-prefixed variables shared by arrange+assert; DTOs built inline when field values matter; `TestDataFactory` for entities where values don't matter.
- Emails are English; sender is the existing `app.email.from`.
- Client-sent prices are NEVER trusted — snapshots always come from the catalog.
- All new `/leads/**` endpoints are public (token-authorized), behind the existing global `RateLimitFilter`.
- Backend commands run from `myhive-backend/`, frontend from `myhive-react-app/` (Bash tool: `cd myhive-backend && ./gradlew test`).
- Frontend Jest: CRA `resetMocks: true` — mock implementations must be (re)defined in `beforeEach` or inside the test, never only at module scope.
- Consent copy (exact): `We'll email you a link to your trip and a couple of reminders. Unsubscribe anytime.`

---

### Task 1: Enums, entities, repositories

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/model/TripLeadStatus.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/model/TripLeadSource.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/TripLead.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/TripLeadActivity.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/EmailSuppression.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/repository/TripLeadRepository.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/repository/TripLeadActivityRepository.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/repository/EmailSuppressionRepository.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/repository/TripLeadTablesTest.java`

**Interfaces:**
- Produces: `TripLead` (getters/setters for every field below), `TripLeadActivity`, `EmailSuppression`, and the three repositories. Later tasks call: `TripLeadRepository.findFirstByEmailAndStatus(String, TripLeadStatus)`, `findAllByEmailAndStatus(String, TripLeadStatus)`, `findByRestoreToken(UUID)`, `findByUnsubscribeToken(UUID)`, `findByStatus(TripLeadStatus)`, `deleteByUpdatedAtBefore(LocalDateTime)`; `TripLeadActivityRepository.findByLeadIdOrderBySortOrder(UUID)`, `deleteByLeadId(UUID)`; `EmailSuppressionRepository.existsByEmail(String)`.

- [ ] **Step 1: Write the failing test**

`myhive-backend/src/test/java/com/myhive/backend/repository/TripLeadTablesTest.java` (model: `VoteSessionTablesTest` — `@SpringBootTest @Transactional @Import(TestSecurityConfig.class)`):

```java
package com.myhive.backend.repository;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.EmailSuppression;
import com.myhive.backend.entity.TripLead;
import com.myhive.backend.entity.TripLeadActivity;
import com.myhive.backend.model.TripLeadSource;
import com.myhive.backend.model.TripLeadStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class TripLeadTablesTest {

    @Autowired private TripLeadRepository tripLeadRepository;
    @Autowired private TripLeadActivityRepository tripLeadActivityRepository;
    @Autowired private EmailSuppressionRepository emailSuppressionRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    private TripLead newLead(String email) {
        TripLead lead = new TripLead();
        lead.setEmail(email);
        lead.setSource(TripLeadSource.QUIZ);
        lead.setRestoreToken(UUID.randomUUID());
        lead.setUnsubscribeToken(UUID.randomUUID());
        lead.setStatus(TripLeadStatus.ACTIVE);
        lead.setReminderStage(0);
        lead.setLastActivityAt(LocalDateTime.now(ZoneOffset.UTC));
        return tripLeadRepository.saveAndFlush(lead);
    }

    @Test
    void tripLeadActivity_persistsSnapshotFields() {
        String expectedName = "Karting (snapshot)";
        BigDecimal expectedPrice = new BigDecimal("49.99");
        BigDecimal expectedMinPrice = new BigDecimal("200.00");

        Destination destination = destinationRepository.saveAndFlush(
                com.myhive.backend.TestDataFactory.destination("Prague"));
        Activity activity = activityRepository.saveAndFlush(
                com.myhive.backend.TestDataFactory.activity(destination, "Karting", new BigDecimal("50.00")));
        TripLead lead = newLead("lead@test.com");

        TripLeadActivity row = new TripLeadActivity();
        row.setLead(lead);
        row.setActivity(activity);
        row.setActivityName(expectedName);
        row.setPrice(expectedPrice);
        row.setMinPrice(expectedMinPrice);
        row.setSortOrder(0);
        tripLeadActivityRepository.saveAndFlush(row);

        List<TripLeadActivity> found = tripLeadActivityRepository.findByLeadIdOrderBySortOrder(lead.getId());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getActivityName()).isEqualTo(expectedName);
        assertThat(found.get(0).getPrice()).isEqualByComparingTo(expectedPrice);
        assertThat(found.get(0).getMinPrice()).isEqualByComparingTo(expectedMinPrice);
    }

    @Test
    void findFirstByEmailAndStatus_findsOnlyActiveLead() {
        TripLead active = newLead("dup@test.com");
        TripLead converted = newLead("dup2@test.com");
        converted.setEmail("dup@test.com");
        converted.setStatus(TripLeadStatus.CONVERTED);
        tripLeadRepository.saveAndFlush(converted);

        assertThat(tripLeadRepository.findFirstByEmailAndStatus("dup@test.com", TripLeadStatus.ACTIVE))
                .hasValueSatisfying(l -> assertThat(l.getId()).isEqualTo(active.getId()));
    }

    @Test
    void deleteByUpdatedAtBefore_removesOldLeads() {
        newLead("old@test.com");
        tripLeadRepository.flush();

        int deletedFuture = tripLeadRepository.deleteByUpdatedAtBefore(
                LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        int deletedPast = tripLeadRepository.deleteByUpdatedAtBefore(
                LocalDateTime.now(ZoneOffset.UTC).minusDays(30));

        assertThat(deletedFuture).isEqualTo(1);
        assertThat(deletedPast).isZero();
    }

    @Test
    void emailSuppression_existsByEmail() {
        EmailSuppression suppression = new EmailSuppression();
        suppression.setEmail("gone@test.com");
        emailSuppressionRepository.saveAndFlush(suppression);

        assertThat(emailSuppressionRepository.existsByEmail("gone@test.com")).isTrue();
        assertThat(emailSuppressionRepository.existsByEmail("here@test.com")).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd myhive-backend && ./gradlew test --tests '*TripLeadTablesTest'`
Expected: COMPILE FAILURE (entities/repositories not defined).

- [ ] **Step 3: Write the enums**

`model/TripLeadStatus.java`:

```java
package com.myhive.backend.model;

public enum TripLeadStatus {
    ACTIVE,
    CONVERTED,
    COMPLETED,
    UNSUBSCRIBED
}
```

`model/TripLeadSource.java`:

```java
package com.myhive.backend.model;

public enum TripLeadSource {
    QUIZ,
    VOTE
}
```

- [ ] **Step 4: Write the entities**

`entity/TripLead.java`:

```java
package com.myhive.backend.entity;

import com.myhive.backend.model.TripLeadSource;
import com.myhive.backend.model.TripLeadStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trip_leads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "destination")
public class TripLead {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Normalized (trimmed, lowercase) — see TripLeadService.normalizeEmail. */
    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TripLeadSource source;

    @Column(name = "restore_token", unique = true, nullable = false, updatable = false)
    private UUID restoreToken;

    @Column(name = "unsubscribe_token", unique = true, nullable = false, updatable = false)
    private UUID unsubscribeToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id")
    private Destination destination;

    @Column(name = "number_of_travelers")
    private Integer numberOfTravelers;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal budget;

    @Column(name = "quiz_responses_json", columnDefinition = "text")
    private String quizResponsesJson;

    @Column(name = "vote_session_id")
    private UUID voteSessionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TripLeadStatus status;

    @Column(name = "reminder_stage", nullable = false)
    private int reminderStage;

    @Column(name = "last_reminder_at")
    private LocalDateTime lastReminderAt;

    /** Series anchor — refreshed on every capture/sync; reminder N+1 is due at lastActivityAt + cadence[N]. */
    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

`entity/TripLeadActivity.java`:

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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "trip_lead_activities",
        uniqueConstraints = @UniqueConstraint(columnNames = {"lead_id", "activity_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"lead", "activity"})
public class TripLeadActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TripLead lead;

    // CASCADE on the activity too (unlike VoteSessionActivity): deleting a catalog activity must
    // silently drop it from lead snapshots, never block admin catalog operations on a lead row.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Activity activity;

    @Column(name = "activity_name", nullable = false, length = 255)
    private String activityName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "min_price", precision = 10, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
```

`entity/EmailSuppression.java`:

```java
package com.myhive.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_suppressions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class EmailSuppression {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Normalized (trimmed, lowercase). Rows are never deleted — the opt-out record must persist. */
    @Column(unique = true, nullable = false)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: Write the repositories**

`repository/TripLeadRepository.java`:

```java
package com.myhive.backend.repository;

import com.myhive.backend.entity.TripLead;
import com.myhive.backend.model.TripLeadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripLeadRepository extends JpaRepository<TripLead, UUID> {

    Optional<TripLead> findFirstByEmailAndStatus(String email, TripLeadStatus status);

    List<TripLead> findAllByEmailAndStatus(String email, TripLeadStatus status);

    Optional<TripLead> findByRestoreToken(UUID restoreToken);

    Optional<TripLead> findByUnsubscribeToken(UUID unsubscribeToken);

    List<TripLead> findByStatus(TripLeadStatus status);

    @Modifying
    @Transactional
    @Query("DELETE FROM TripLead l WHERE l.updatedAt < :cutoff")
    int deleteByUpdatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
```

`repository/TripLeadActivityRepository.java`:

```java
package com.myhive.backend.repository;

import com.myhive.backend.entity.TripLeadActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TripLeadActivityRepository extends JpaRepository<TripLeadActivity, UUID> {

    List<TripLeadActivity> findByLeadIdOrderBySortOrder(UUID leadId);

    @Modifying
    @Query("DELETE FROM TripLeadActivity a WHERE a.lead.id = :leadId")
    void deleteByLeadId(@Param("leadId") UUID leadId);
}
```

`repository/EmailSuppressionRepository.java`:

```java
package com.myhive.backend.repository;

import com.myhive.backend.entity.EmailSuppression;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmailSuppressionRepository extends JpaRepository<EmailSuppression, UUID> {

    boolean existsByEmail(String email);
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd myhive-backend && ./gradlew test --tests '*TripLeadTablesTest'`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/model/TripLeadStatus.java myhive-backend/src/main/java/com/myhive/backend/model/TripLeadSource.java myhive-backend/src/main/java/com/myhive/backend/entity/TripLead.java myhive-backend/src/main/java/com/myhive/backend/entity/TripLeadActivity.java myhive-backend/src/main/java/com/myhive/backend/entity/EmailSuppression.java myhive-backend/src/main/java/com/myhive/backend/repository/TripLeadRepository.java myhive-backend/src/main/java/com/myhive/backend/repository/TripLeadActivityRepository.java myhive-backend/src/main/java/com/myhive/backend/repository/EmailSuppressionRepository.java myhive-backend/src/test/java/com/myhive/backend/repository/TripLeadTablesTest.java
git commit -m "feat(leads): TripLead/TripLeadActivity/EmailSuppression entities and repositories"
```

---

### Task 2: Conversion-check queries on existing repositories

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/repository/BookingRepository.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/repository/VoteSessionRepository.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/repository/TripLeadConversionQueriesTest.java`

**Interfaces:**
- Produces: `BookingRepository.existsByUserEmailIgnoreCaseAndCreatedAtAfter(String, LocalDateTime)`, `BookingRepository.existsByVoteSessionId(UUID)`, `VoteSessionRepository.existsByInitiatorEmailIgnoreCaseAndCreatedAtAfter(String, LocalDateTime)` — consumed by Task 7's stop-condition checks.

- [ ] **Step 1: Write the failing test**

`TripLeadConversionQueriesTest.java`:

```java
package com.myhive.backend.repository;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.model.VoteMode;
import com.myhive.backend.model.VoteSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class TripLeadConversionQueriesTest {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private DestinationRepository destinationRepository;

    @Test
    void existsByUserEmailIgnoreCaseAndCreatedAtAfter_matchesCaseInsensitively() {
        LocalDateTime past = LocalDateTime.now(ZoneOffset.UTC).minusHours(2);
        Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
        booking.setId(null);
        booking.setUserEmail("Alice@Example.COM");
        bookingRepository.saveAndFlush(booking);

        assertThat(bookingRepository
                .existsByUserEmailIgnoreCaseAndCreatedAtAfter("alice@example.com", past)).isTrue();
        assertThat(bookingRepository
                .existsByUserEmailIgnoreCaseAndCreatedAtAfter("other@example.com", past)).isFalse();
        assertThat(bookingRepository.existsByUserEmailIgnoreCaseAndCreatedAtAfter(
                "alice@example.com", LocalDateTime.now(ZoneOffset.UTC).plusHours(1))).isFalse();
    }

    @Test
    void existsByVoteSessionId_findsLinkedBooking() {
        UUID expectedSessionId = UUID.randomUUID();
        Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
        booking.setId(null);
        booking.setVoteSessionId(expectedSessionId);
        bookingRepository.saveAndFlush(booking);

        assertThat(bookingRepository.existsByVoteSessionId(expectedSessionId)).isTrue();
        assertThat(bookingRepository.existsByVoteSessionId(UUID.randomUUID())).isFalse();
    }

    @Test
    void existsByInitiatorEmailIgnoreCaseAndCreatedAtAfter_matchesSessions() {
        LocalDateTime past = LocalDateTime.now(ZoneOffset.UTC).minusHours(2);
        Destination destination = destinationRepository.saveAndFlush(TestDataFactory.destination("Prague"));

        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail("Bob@Example.com");
        session.setNumberOfTravelers(4);
        session.setStartDate(LocalDate.now().plusDays(7));
        session.setEndDate(LocalDate.now().plusDays(9));
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setVoteMode(VoteMode.CART);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(24));
        voteSessionRepository.saveAndFlush(session);

        assertThat(voteSessionRepository
                .existsByInitiatorEmailIgnoreCaseAndCreatedAtAfter("bob@example.com", past)).isTrue();
        assertThat(voteSessionRepository
                .existsByInitiatorEmailIgnoreCaseAndCreatedAtAfter("nobody@example.com", past)).isFalse();
    }
}
```

Note: `TestDataFactory.booking(...)` presets an id — `booking.setId(null)` lets JPA assign one so `saveAndFlush` inserts instead of merging a detached row.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd myhive-backend && ./gradlew test --tests '*TripLeadConversionQueriesTest'`
Expected: COMPILE FAILURE (methods missing).

- [ ] **Step 3: Add the derived queries**

In `BookingRepository.java` add (with imports `java.time.LocalDateTime` already needed — add it):

```java
    /** Trip-lead reminder stop condition: any booking by this email since the lead was captured. */
    boolean existsByUserEmailIgnoreCaseAndCreatedAtAfter(String userEmail, LocalDateTime createdAt);

    /** Trip-lead reminder stop condition: any booking (incl. consultation) from this vote session. */
    boolean existsByVoteSessionId(UUID voteSessionId);
```

In `VoteSessionRepository.java` add:

```java
    /** Trip-lead reminder stop condition: the lead started a vote after being captured. */
    boolean existsByInitiatorEmailIgnoreCaseAndCreatedAtAfter(String initiatorEmail, LocalDateTime createdAt);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd myhive-backend && ./gradlew test --tests '*TripLeadConversionQueriesTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/repository/BookingRepository.java myhive-backend/src/main/java/com/myhive/backend/repository/VoteSessionRepository.java myhive-backend/src/test/java/com/myhive/backend/repository/TripLeadConversionQueriesTest.java
git commit -m "feat(leads): conversion-check queries for reminder stop conditions"
```

---

### Task 3: DTOs + TripLeadService (create / sync / restore / unsubscribe)

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/TripLeadCreateRequest.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/TripLeadSyncRequest.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/TripLeadCreateResponse.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/TripLeadRestoreResponse.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/TripLeadUnsubscribeRequest.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/TripLeadService.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/TripLeadServiceTest.java`

**Interfaces:**
- Consumes: Task 1 entities/repositories.
- Produces: `TripLeadService.create(TripLeadCreateRequest): TripLeadCreateResponse`, `sync(UUID, TripLeadSyncRequest): void` (throws `ResourceNotFoundException` on unknown id or token mismatch), `restore(UUID): TripLeadRestoreResponse` (throws `ResourceNotFoundException`), `unsubscribe(UUID): void` (idempotent, silent on unknown token), `static normalizeEmail(String): String`. Also package-visible `newLead`/`saveItemSnapshot` reused by Task 5's `createFromVoteSession` (same class).

- [ ] **Step 1: Write the DTOs**

`dto/TripLeadCreateRequest.java`:

```java
package com.myhive.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class TripLeadCreateRequest {
    @NotNull @Email private String email;
    private UUID destinationId;
    @Min(1) @Max(99) private Integer numberOfTravelers;
    private LocalDate startDate;
    private LocalDate endDate;
    @PositiveOrZero private BigDecimal budget;
}
```

`dto/TripLeadSyncRequest.java`:

```java
package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class TripLeadSyncRequest {
    @NotNull private UUID restoreToken;
    @Min(1) @Max(99) private Integer numberOfTravelers;
    private LocalDate startDate;
    private LocalDate endDate;
    @PositiveOrZero private BigDecimal budget;
    @Size(max = 100_000) private String quizResponsesJson;
    /** null = leave the snapshot untouched; empty list = clear it. */
    @Size(max = 100) @Valid private List<SyncItem> items;

    @Getter
    @Setter
    public static class SyncItem {
        @NotNull private UUID activityId;
        @NotNull private Integer sortOrder;
    }
}
```

`dto/TripLeadCreateResponse.java`:

```java
package com.myhive.backend.dto;

import java.util.UUID;

public record TripLeadCreateResponse(UUID id, UUID restoreToken) {
}
```

`dto/TripLeadRestoreResponse.java`:

```java
package com.myhive.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TripLeadRestoreResponse(
        UUID leadId,
        String email,
        UUID destinationId,
        String destinationSlug,
        String destinationName,
        Integer numberOfTravelers,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal budget,
        String quizResponsesJson,
        List<RestoreItem> items) {

    public record RestoreItem(
            UUID activityId,
            String name,
            BigDecimal price,
            BigDecimal minPrice,
            String imageUrl,
            Integer duration,
            String slug,
            String destinationSlug,
            String description,
            String includes) {
    }
}
```

`dto/TripLeadUnsubscribeRequest.java`:

```java
package com.myhive.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class TripLeadUnsubscribeRequest {
    @NotNull private UUID token;
}
```

- [ ] **Step 2: Write the failing test**

`service/TripLeadServiceTest.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.TripLeadCreateRequest;
import com.myhive.backend.dto.TripLeadCreateResponse;
import com.myhive.backend.dto.TripLeadRestoreResponse;
import com.myhive.backend.dto.TripLeadSyncRequest;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.TripLead;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.model.TripLeadStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.EmailSuppressionRepository;
import com.myhive.backend.repository.TripLeadActivityRepository;
import com.myhive.backend.repository.TripLeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class TripLeadServiceTest {

    @Autowired private TripLeadService tripLeadService;
    @Autowired private TripLeadRepository tripLeadRepository;
    @Autowired private TripLeadActivityRepository tripLeadActivityRepository;
    @Autowired private EmailSuppressionRepository emailSuppressionRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    private Destination destination;
    private Activity karting;

    @BeforeEach
    void setUp() {
        destination = destinationRepository.saveAndFlush(TestDataFactory.destination("Prague"));
        karting = activityRepository.saveAndFlush(
                TestDataFactory.activity(destination, "Karting", new BigDecimal("50.00")));
    }

    private TripLeadCreateRequest createRequest(String email) {
        TripLeadCreateRequest request = new TripLeadCreateRequest();
        request.setEmail(email);
        request.setDestinationId(destination.getId());
        request.setNumberOfTravelers(6);
        request.setStartDate(LocalDate.now().plusDays(30));
        request.setEndDate(LocalDate.now().plusDays(32));
        return request;
    }

    @Test
    void create_normalizesEmailAndIssuesTokens() {
        TripLeadCreateResponse response = tripLeadService.create(createRequest("  Stag.Lead@Example.COM "));

        TripLead saved = tripLeadRepository.findById(response.id()).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("stag.lead@example.com");
        assertThat(saved.getRestoreToken()).isEqualTo(response.restoreToken());
        assertThat(saved.getUnsubscribeToken()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(TripLeadStatus.ACTIVE);
        assertThat(saved.getReminderStage()).isZero();
    }

    @Test
    void create_reusesActiveLeadForSameEmail() {
        TripLeadCreateResponse first = tripLeadService.create(createRequest("dup@example.com"));
        TripLeadCreateResponse second = tripLeadService.create(createRequest("DUP@example.com"));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.restoreToken()).isEqualTo(first.restoreToken());
        assertThat(tripLeadRepository.findAllByEmailAndStatus("dup@example.com", TripLeadStatus.ACTIVE))
                .hasSize(1);
    }

    @Test
    void sync_snapshotsItemsFromCatalogAndDropsUnknownIds() {
        TripLeadCreateResponse lead = tripLeadService.create(createRequest("sync@example.com"));

        TripLeadSyncRequest.SyncItem known = new TripLeadSyncRequest.SyncItem();
        known.setActivityId(karting.getId());
        known.setSortOrder(0);
        TripLeadSyncRequest.SyncItem unknown = new TripLeadSyncRequest.SyncItem();
        unknown.setActivityId(UUID.randomUUID());
        unknown.setSortOrder(1);
        TripLeadSyncRequest request = new TripLeadSyncRequest();
        request.setRestoreToken(lead.restoreToken());
        request.setItems(List.of(known, unknown));

        tripLeadService.sync(lead.id(), request);

        var rows = tripLeadActivityRepository.findByLeadIdOrderBySortOrder(lead.id());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getActivityName()).isEqualTo("Karting");
        assertThat(rows.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void sync_rejectsWrongToken() {
        TripLeadCreateResponse lead = tripLeadService.create(createRequest("token@example.com"));
        TripLeadSyncRequest request = new TripLeadSyncRequest();
        request.setRestoreToken(UUID.randomUUID());

        assertThatThrownBy(() -> tripLeadService.sync(lead.id(), request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void restore_returnsSnapshotWithLiveCatalogData() {
        TripLeadCreateResponse lead = tripLeadService.create(createRequest("restore@example.com"));
        TripLeadSyncRequest.SyncItem item = new TripLeadSyncRequest.SyncItem();
        item.setActivityId(karting.getId());
        item.setSortOrder(0);
        TripLeadSyncRequest sync = new TripLeadSyncRequest();
        sync.setRestoreToken(lead.restoreToken());
        sync.setQuizResponsesJson("[{\"questionId\":\"q\",\"answerId\":\"a\"}]");
        sync.setItems(List.of(item));
        tripLeadService.sync(lead.id(), sync);

        TripLeadRestoreResponse response = tripLeadService.restore(lead.restoreToken());

        assertThat(response.leadId()).isEqualTo(lead.id());
        assertThat(response.email()).isEqualTo("restore@example.com");
        assertThat(response.destinationSlug()).isEqualTo(destination.getSlug());
        assertThat(response.numberOfTravelers()).isEqualTo(6);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).activityId()).isEqualTo(karting.getId());
        assertThat(response.items().get(0).name()).isEqualTo("Karting");
    }

    @Test
    void restore_unknownTokenThrows404() {
        assertThatThrownBy(() -> tripLeadService.restore(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unsubscribe_suppressesEmailAndMarksActiveLeads() {
        TripLeadCreateResponse lead = tripLeadService.create(createRequest("bye@example.com"));
        UUID unsubscribeToken = tripLeadRepository.findById(lead.id()).orElseThrow().getUnsubscribeToken();

        tripLeadService.unsubscribe(unsubscribeToken);

        assertThat(emailSuppressionRepository.existsByEmail("bye@example.com")).isTrue();
        assertThat(tripLeadRepository.findById(lead.id()).orElseThrow().getStatus())
                .isEqualTo(TripLeadStatus.UNSUBSCRIBED);
    }

    @Test
    void unsubscribe_unknownTokenIsSilentlyIgnored() {
        tripLeadService.unsubscribe(UUID.randomUUID());
        // No exception — idempotent, token validity is never leaked.
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd myhive-backend && ./gradlew test --tests '*TripLeadServiceTest'`
Expected: COMPILE FAILURE (`TripLeadService` missing).

- [ ] **Step 4: Write TripLeadService**

`service/TripLeadService.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.TripLeadCreateRequest;
import com.myhive.backend.dto.TripLeadCreateResponse;
import com.myhive.backend.dto.TripLeadRestoreResponse;
import com.myhive.backend.dto.TripLeadSyncRequest;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.EmailSuppression;
import com.myhive.backend.entity.TripLead;
import com.myhive.backend.entity.TripLeadActivity;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.model.TripLeadSource;
import com.myhive.backend.model.TripLeadStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.EmailSuppressionRepository;
import com.myhive.backend.repository.TripLeadActivityRepository;
import com.myhive.backend.repository.TripLeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TripLeadService {

    private final TripLeadRepository tripLeadRepository;
    private final TripLeadActivityRepository tripLeadActivityRepository;
    private final EmailSuppressionRepository emailSuppressionRepository;
    private final DestinationRepository destinationRepository;
    private final ActivityRepository activityRepository;

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public TripLeadCreateResponse create(TripLeadCreateRequest request) {
        String email = normalizeEmail(request.getEmail());
        TripLead lead = tripLeadRepository.findFirstByEmailAndStatus(email, TripLeadStatus.ACTIVE)
                .orElseGet(() -> newLead(email));
        applySetup(lead, request.getDestinationId(), request.getNumberOfTravelers(),
                request.getStartDate(), request.getEndDate(), request.getBudget());
        lead.setLastActivityAt(LocalDateTime.now(ZoneOffset.UTC));
        lead = tripLeadRepository.save(lead);
        return new TripLeadCreateResponse(lead.getId(), lead.getRestoreToken());
    }

    TripLead newLead(String email) {
        TripLead lead = new TripLead();
        lead.setEmail(email);
        lead.setSource(TripLeadSource.QUIZ);
        lead.setRestoreToken(UUID.randomUUID());
        lead.setUnsubscribeToken(UUID.randomUUID());
        lead.setStatus(TripLeadStatus.ACTIVE);
        lead.setReminderStage(0);
        return lead;
    }

    private void applySetup(TripLead lead, UUID destinationId, Integer numberOfTravelers,
                            LocalDate startDate, LocalDate endDate, BigDecimal budget) {
        if (destinationId != null) {
            destinationRepository.findById(destinationId).ifPresent(lead::setDestination);
        }
        if (numberOfTravelers != null) {
            lead.setNumberOfTravelers(numberOfTravelers);
        }
        if (startDate != null) {
            lead.setStartDate(startDate);
        }
        if (endDate != null) {
            lead.setEndDate(endDate);
        }
        if (budget != null) {
            lead.setBudget(budget);
        }
    }

    @Transactional
    public void sync(UUID leadId, TripLeadSyncRequest request) {
        // Same 404 for a missing lead and a token mismatch — existence is never leaked.
        TripLead lead = tripLeadRepository.findById(leadId)
                .filter(l -> l.getRestoreToken().equals(request.getRestoreToken()))
                .orElseThrow(() -> new ResourceNotFoundException("Trip lead not found"));
        if (lead.getStatus() != TripLeadStatus.ACTIVE) {
            return; // converted/finished leads accept no further sync — not an error for the client
        }
        applySetup(lead, null, request.getNumberOfTravelers(), request.getStartDate(),
                request.getEndDate(), request.getBudget());
        if (request.getQuizResponsesJson() != null) {
            lead.setQuizResponsesJson(request.getQuizResponsesJson());
        }
        if (request.getItems() != null) {
            replaceItemsFromCatalog(lead, request.getItems());
        }
        lead.setLastActivityAt(LocalDateTime.now(ZoneOffset.UTC));
        tripLeadRepository.save(lead);
    }

    /** Snapshots name/price/minPrice from the catalog — client-sent prices are never trusted. */
    private void replaceItemsFromCatalog(TripLead lead, List<TripLeadSyncRequest.SyncItem> items) {
        tripLeadActivityRepository.deleteByLeadId(lead.getId());
        List<TripLeadSyncRequest.SyncItem> ordered = items.stream()
                .sorted(Comparator.comparingInt(TripLeadSyncRequest.SyncItem::getSortOrder))
                .toList();
        Map<UUID, Activity> activitiesById = activityRepository
                .findAllById(ordered.stream().map(TripLeadSyncRequest.SyncItem::getActivityId).toList())
                .stream()
                .collect(Collectors.toMap(Activity::getId, a -> a));
        int sortOrder = 0;
        for (TripLeadSyncRequest.SyncItem item : ordered) {
            Activity activity = activitiesById.get(item.getActivityId());
            if (activity == null) {
                continue; // stale cart entry — the activity is no longer in the catalog
            }
            saveItemSnapshot(lead, activity, sortOrder++);
        }
    }

    void saveItemSnapshot(TripLead lead, Activity activity, int sortOrder) {
        TripLeadActivity row = new TripLeadActivity();
        row.setLead(lead);
        row.setActivity(activity);
        row.setActivityName(activity.getName());
        row.setPrice(activity.getPrice());
        row.setMinPrice(activity.getMinPrice());
        row.setSortOrder(sortOrder);
        tripLeadActivityRepository.save(row);
    }

    public TripLeadRestoreResponse restore(UUID restoreToken) {
        TripLead lead = tripLeadRepository.findByRestoreToken(restoreToken)
                .orElseThrow(() -> new ResourceNotFoundException("Trip lead not found"));
        List<TripLeadRestoreResponse.RestoreItem> items = tripLeadActivityRepository
                .findByLeadIdOrderBySortOrder(lead.getId()).stream()
                .map(TripLeadService::toRestoreItem)
                .toList();
        Destination destination = lead.getDestination();
        return new TripLeadRestoreResponse(
                lead.getId(),
                lead.getEmail(),
                destination == null ? null : destination.getId(),
                destination == null ? null : destination.getSlug(),
                destination == null ? null : destination.getName(),
                lead.getNumberOfTravelers(),
                lead.getStartDate(),
                lead.getEndDate(),
                lead.getBudget(),
                lead.getQuizResponsesJson(),
                items);
    }

    /** Restore serves live catalog data (current names/prices) — the snapshot only preserves order/membership. */
    private static TripLeadRestoreResponse.RestoreItem toRestoreItem(TripLeadActivity row) {
        Activity activity = row.getActivity();
        String destinationSlug = activity.getDestination() == null
                ? null : activity.getDestination().getSlug();
        return new TripLeadRestoreResponse.RestoreItem(
                activity.getId(), activity.getName(), activity.getPrice(), activity.getMinPrice(),
                activity.getImageUrl(), activity.getDuration(), activity.getSlug(), destinationSlug,
                activity.getDescription(), activity.getIncludes());
    }

    @Transactional
    public void unsubscribe(UUID token) {
        tripLeadRepository.findByUnsubscribeToken(token).ifPresent(lead -> {
            suppress(lead.getEmail());
            for (TripLead active : tripLeadRepository
                    .findAllByEmailAndStatus(lead.getEmail(), TripLeadStatus.ACTIVE)) {
                active.setStatus(TripLeadStatus.UNSUBSCRIBED);
                tripLeadRepository.save(active);
            }
        });
    }

    private void suppress(String email) {
        if (!emailSuppressionRepository.existsByEmail(email)) {
            EmailSuppression suppression = new EmailSuppression();
            suppression.setEmail(email);
            emailSuppressionRepository.save(suppression);
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd myhive-backend && ./gradlew test --tests '*TripLeadServiceTest'`
Expected: PASS (8 tests).

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/TripLeadCreateRequest.java myhive-backend/src/main/java/com/myhive/backend/dto/TripLeadSyncRequest.java myhive-backend/src/main/java/com/myhive/backend/dto/TripLeadCreateResponse.java myhive-backend/src/main/java/com/myhive/backend/dto/TripLeadRestoreResponse.java myhive-backend/src/main/java/com/myhive/backend/dto/TripLeadUnsubscribeRequest.java myhive-backend/src/main/java/com/myhive/backend/service/TripLeadService.java myhive-backend/src/test/java/com/myhive/backend/service/TripLeadServiceTest.java
git commit -m "feat(leads): TripLeadService create/sync/restore/unsubscribe"
```

---

### Task 4: TripLeadController + SecurityConfig

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/controller/TripLeadController.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/config/SecurityConfig.java` (add `/leads/**` permitAll after the `/vote/**` matcher, ~line 62)
- Test: `myhive-backend/src/test/java/com/myhive/backend/controller/TripLeadControllerTest.java`

**Interfaces:**
- Consumes: Task 3 service + DTOs.
- Produces: `POST /leads` → 201 `{id, restoreToken}`; `PATCH /leads/{id}` → 204; `GET /leads/restore/{restoreToken}` → 200/404; `POST /leads/unsubscribe` (body `{token}`) → 204; `POST /leads/unsubscribe/one-click?token=` → 200.

- [ ] **Step 1: Write the failing test**

`controller/TripLeadControllerTest.java` (model: `VoteSessionControllerTest` — `@SpringBootTest @AutoConfigureMockMvc` + `@TestConfiguration @Primary` mock):

```java
package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.TripLeadCreateResponse;
import com.myhive.backend.dto.TripLeadRestoreResponse;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.service.TripLeadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, TripLeadControllerTest.MockConfig.class})
class TripLeadControllerTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        public TripLeadService tripLeadService() {
            return mock(TripLeadService.class);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private TripLeadService tripLeadService;

    @BeforeEach
    void setUp() {
        reset(tripLeadService);
    }

    @Test
    void create_returns201WithTokens() throws Exception {
        UUID expectedId = UUID.randomUUID();
        UUID expectedToken = UUID.randomUUID();
        when(tripLeadService.create(any())).thenReturn(new TripLeadCreateResponse(expectedId, expectedToken));

        String requestJson = """
                {
                    "email": "lead@example.com",
                    "destinationId": "%s",
                    "numberOfTravelers": 6,
                    "startDate": "2026-09-01",
                    "endDate": "2026-09-03"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(expectedId.toString()))
                .andExpect(jsonPath("$.restoreToken").value(expectedToken.toString()));
    }

    @Test
    void create_rejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sync_returns204() throws Exception {
        UUID leadId = UUID.randomUUID();
        String requestJson = """
                {
                    "restoreToken": "%s",
                    "numberOfTravelers": 4,
                    "items": [{"activityId": "%s", "sortOrder": 0}]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(patch("/leads/" + leadId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNoContent());

        verify(tripLeadService).sync(eq(leadId), any());
    }

    @Test
    void restore_returns404ForUnknownToken() throws Exception {
        UUID token = UUID.randomUUID();
        when(tripLeadService.restore(token)).thenThrow(new ResourceNotFoundException("Trip lead not found"));

        mockMvc.perform(get("/leads/restore/" + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void restore_returnsSnapshot() throws Exception {
        UUID token = UUID.randomUUID();
        TripLeadRestoreResponse response = new TripLeadRestoreResponse(
                UUID.randomUUID(), "lead@example.com", UUID.randomUUID(), "prague", "Prague",
                6, null, null, null, null, List.of());
        when(tripLeadService.restore(token)).thenReturn(response);

        mockMvc.perform(get("/leads/restore/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinationSlug").value("prague"))
                .andExpect(jsonPath("$.email").value("lead@example.com"));
    }

    @Test
    void unsubscribe_returns204AndDelegates() throws Exception {
        UUID expectedToken = UUID.randomUUID();

        mockMvc.perform(post("/leads/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + expectedToken + "\"}"))
                .andExpect(status().isNoContent());

        verify(tripLeadService).unsubscribe(expectedToken);
    }

    @Test
    void unsubscribeOneClick_returns200AndDelegates() throws Exception {
        UUID expectedToken = UUID.randomUUID();

        mockMvc.perform(post("/leads/unsubscribe/one-click").param("token", expectedToken.toString()))
                .andExpect(status().isOk());

        verify(tripLeadService).unsubscribe(expectedToken);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd myhive-backend && ./gradlew test --tests '*TripLeadControllerTest'`
Expected: COMPILE FAILURE (controller missing).

- [ ] **Step 3: Write the controller and open the route**

`controller/TripLeadController.java`:

```java
package com.myhive.backend.controller;

import com.myhive.backend.dto.TripLeadCreateRequest;
import com.myhive.backend.dto.TripLeadCreateResponse;
import com.myhive.backend.dto.TripLeadRestoreResponse;
import com.myhive.backend.dto.TripLeadSyncRequest;
import com.myhive.backend.dto.TripLeadUnsubscribeRequest;
import com.myhive.backend.service.TripLeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/leads")
@RequiredArgsConstructor
public class TripLeadController {

    private final TripLeadService tripLeadService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripLeadCreateResponse create(@Valid @RequestBody TripLeadCreateRequest request) {
        return tripLeadService.create(request);
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sync(@PathVariable UUID id, @Valid @RequestBody TripLeadSyncRequest request) {
        tripLeadService.sync(id, request);
    }

    @GetMapping("/restore/{restoreToken}")
    public TripLeadRestoreResponse restore(@PathVariable UUID restoreToken) {
        return tripLeadService.restore(restoreToken);
    }

    @PostMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody TripLeadUnsubscribeRequest request) {
        tripLeadService.unsubscribe(request.getToken());
    }

    /** RFC 8058 one-click target — mail providers POST here with no meaningful body. */
    @PostMapping("/unsubscribe/one-click")
    @ResponseStatus(HttpStatus.OK)
    public void unsubscribeOneClick(@RequestParam UUID token) {
        tripLeadService.unsubscribe(token);
    }
}
```

In `SecurityConfig.java`, after `.requestMatchers("/vote/**").permitAll()`:

```java
                        // Trip lead capture / restore / unsubscribe (token-authorized, never JWT)
                        .requestMatchers("/leads/**").permitAll()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd myhive-backend && ./gradlew test --tests '*TripLeadControllerTest'`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/controller/TripLeadController.java myhive-backend/src/main/java/com/myhive/backend/config/SecurityConfig.java myhive-backend/src/test/java/com/myhive/backend/controller/TripLeadControllerTest.java
git commit -m "feat(leads): public /leads endpoints"
```

---

### Task 5: VOTE-lead creation on vote completion

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/TripLeadService.java` (add `createFromVoteSession` + item copy)
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java` (inject `TripLeadService`; hook in `processSession` after the vote-result email try-block, ~line 557)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/TripLeadFromVoteSessionTest.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionLeadCaptureFailureTest.java`

**Interfaces:**
- Consumes: `VoteSessionActivityRepository.findBySessionIdOrderBySortOrder(UUID)`, `VoteSessionResultActivityRepository.findBySessionIdOrderBySortOrder(UUID)`, `BookingRepository.existsByVoteSessionId(UUID)` (Task 2).
- Produces: `TripLeadService.createFromVoteSession(VoteSession): void` — `REQUIRES_NEW`, never throws into the caller's flow (caller wraps in try/catch anyway).

- [ ] **Step 1: Write the failing test**

`service/TripLeadFromVoteSessionTest.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.EmailSuppression;
import com.myhive.backend.entity.TripLead;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.model.TripLeadSource;
import com.myhive.backend.model.TripLeadStatus;
import com.myhive.backend.model.VoteMode;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.BookingRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.EmailSuppressionRepository;
import com.myhive.backend.repository.TripLeadActivityRepository;
import com.myhive.backend.repository.TripLeadRepository;
import com.myhive.backend.repository.VoteSessionActivityRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Not @Transactional: createFromVoteSession opens a REQUIRES_NEW transaction, which would
// deadlock/miss data pinned in an uncommitted test transaction. Cleanup is manual.
@SpringBootTest
@Import(TestSecurityConfig.class)
class TripLeadFromVoteSessionTest {

    @Autowired private TripLeadService tripLeadService;
    @Autowired private TripLeadRepository tripLeadRepository;
    @Autowired private TripLeadActivityRepository tripLeadActivityRepository;
    @Autowired private EmailSuppressionRepository emailSuppressionRepository;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteSessionActivityRepository voteSessionActivityRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private BookingRepository bookingRepository;

    private Destination destination;
    private Activity karting;

    @BeforeEach
    void setUp() {
        // No broad deleteAll: this class is not @Transactional and shares the cached context DB
        // with other test classes — each test isolates itself via unique emails/session ids instead.
        destination = destinationRepository.saveAndFlush(TestDataFactory.destination("Prague"));
        karting = activityRepository.saveAndFlush(
                TestDataFactory.activity(destination, "Karting", new BigDecimal("50.00")));
    }

    private VoteSession completedSession(String email) {
        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail(email);
        session.setNumberOfTravelers(8);
        session.setStartDate(LocalDate.now().plusDays(20));
        session.setEndDate(LocalDate.now().plusDays(22));
        session.setStatus(VoteSessionStatus.COMPLETED);
        session.setVoteMode(VoteMode.CART);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC));
        session = voteSessionRepository.saveAndFlush(session);

        VoteSessionActivity ballotRow = new VoteSessionActivity();
        ballotRow.setSession(session);
        ballotRow.setActivity(karting);
        ballotRow.setActivityName("Karting");
        ballotRow.setPrice(new BigDecimal("50.00"));
        ballotRow.setSortOrder(0);
        voteSessionActivityRepository.saveAndFlush(ballotRow);
        return session;
    }

    @Test
    void createFromVoteSession_createsVoteLeadWithBallotSnapshot() {
        VoteSession session = completedSession("Organizer@Example.com");

        tripLeadService.createFromVoteSession(session);

        List<TripLead> leads = tripLeadRepository
                .findAllByEmailAndStatus("organizer@example.com", TripLeadStatus.ACTIVE);
        assertThat(leads).hasSize(1);
        TripLead lead = leads.get(0);
        assertThat(lead.getSource()).isEqualTo(TripLeadSource.VOTE);
        assertThat(lead.getVoteSessionId()).isEqualTo(session.getId());
        assertThat(lead.getNumberOfTravelers()).isEqualTo(8);
        assertThat(tripLeadActivityRepository.findByLeadIdOrderBySortOrder(lead.getId())).hasSize(1);
    }

    @Test
    void createFromVoteSession_skipsWhenBookingExists() {
        VoteSession session = completedSession("booked@example.com");
        Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
        booking.setId(null);
        booking.setVoteSessionId(session.getId());
        bookingRepository.saveAndFlush(booking);

        tripLeadService.createFromVoteSession(session);

        assertThat(tripLeadRepository.findAllByEmailAndStatus("booked@example.com", TripLeadStatus.ACTIVE))
                .isEmpty();
    }

    @Test
    void createFromVoteSession_skipsSuppressedEmails() {
        VoteSession session = completedSession("optout@example.com");
        EmailSuppression suppression = new EmailSuppression();
        suppression.setEmail("optout@example.com");
        emailSuppressionRepository.saveAndFlush(suppression);

        tripLeadService.createFromVoteSession(session);

        assertThat(tripLeadRepository.findAllByEmailAndStatus("optout@example.com", TripLeadStatus.ACTIVE))
                .isEmpty();
    }

    @Test
    void createFromVoteSession_repurposesExistingActiveLead() {
        VoteSession session = completedSession("existing@example.com");
        TripLead quizLead = tripLeadService.newLead("existing@example.com");
        quizLead.setReminderStage(1);
        quizLead.setLastActivityAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        tripLeadRepository.saveAndFlush(quizLead);

        tripLeadService.createFromVoteSession(session);

        List<TripLead> leads = tripLeadRepository
                .findAllByEmailAndStatus("existing@example.com", TripLeadStatus.ACTIVE);
        assertThat(leads).hasSize(1);
        assertThat(leads.get(0).getId()).isEqualTo(quizLead.getId());
        assertThat(leads.get(0).getSource()).isEqualTo(TripLeadSource.VOTE);
        assertThat(leads.get(0).getReminderStage()).isZero();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd myhive-backend && ./gradlew test --tests '*TripLeadFromVoteSessionTest'`
Expected: COMPILE FAILURE (`createFromVoteSession` missing).

- [ ] **Step 3: Add createFromVoteSession to TripLeadService**

Add to `TripLeadService.java` (new imports: `com.myhive.backend.entity.VoteSession`, `com.myhive.backend.entity.VoteSessionActivity`, `com.myhive.backend.entity.VoteSessionResultActivity`, `com.myhive.backend.repository.BookingRepository`, `com.myhive.backend.repository.VoteSessionActivityRepository`, `com.myhive.backend.repository.VoteSessionResultActivityRepository`, `org.springframework.transaction.annotation.Propagation`; new constructor deps: `bookingRepository`, `voteSessionActivityRepository`, `resultActivityRepository`):

```java
    /**
     * Captures a reminder lead when a vote session completes without a booking. REQUIRES_NEW so a
     * failure here can be swallowed by the caller without poisoning the vote-completion transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createFromVoteSession(VoteSession session) {
        String email = normalizeEmail(session.getInitiatorEmail());
        if (bookingRepository.existsByVoteSessionId(session.getId())) {
            return; // already booked from this vote — nothing to remind about
        }
        if (emailSuppressionRepository.existsByEmail(email)) {
            return;
        }
        TripLead lead = tripLeadRepository.findFirstByEmailAndStatus(email, TripLeadStatus.ACTIVE)
                .orElseGet(() -> newLead(email));
        lead.setSource(TripLeadSource.VOTE);
        lead.setVoteSessionId(session.getId());
        lead.setDestination(session.getDestination());
        lead.setNumberOfTravelers(session.getNumberOfTravelers());
        lead.setStartDate(session.getStartDate());
        lead.setEndDate(session.getEndDate());
        lead.setBudget(session.getBudget());
        // The vote result is a fresh trigger: the VOTE cadence (24h/72h) starts over.
        lead.setReminderStage(0);
        lead.setLastActivityAt(LocalDateTime.now(ZoneOffset.UTC));
        lead = tripLeadRepository.save(lead);
        replaceItemsFromVoteResult(lead, session);
    }

    private void replaceItemsFromVoteResult(TripLead lead, VoteSession session) {
        tripLeadActivityRepository.deleteByLeadId(lead.getId());
        List<VoteSessionResultActivity> results = resultActivityRepository
                .findBySessionIdOrderBySortOrder(session.getId());
        // Winners in ranked order when the vote produced results; the full ballot otherwise.
        List<Activity> ordered = results.isEmpty()
                ? voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId()).stream()
                        .map(VoteSessionActivity::getActivity)
                        .toList()
                : results.stream()
                        .map(VoteSessionResultActivity::getActivity)
                        .toList();
        int sortOrder = 0;
        for (Activity activity : ordered) {
            saveItemSnapshot(lead, activity, sortOrder++);
        }
    }
```

- [ ] **Step 4: Hook into VoteSessionService.processSession**

In `VoteSessionService.java`: add `private final TripLeadService tripLeadService;` to the injected fields, and in `processSession`, immediately after the existing vote-result-email `try/catch` block:

```java
        try {
            tripLeadService.createFromVoteSession(session);
        } catch (Exception e) {
            // A failed lead capture must never fail vote completion — log and move on.
            log.error("Failed to create trip lead for session {}: {}", session.getId(), e.getMessage(), e);
        }
```

- [ ] **Step 5: Write the never-fails-completion test**

`service/VoteSessionLeadCaptureFailureTest.java` — a broken lead capture must never roll back vote completion:

```java
package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.model.VoteMode;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteSessionActivityRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

// Not @Transactional: processSession runs REQUIRES_NEW.
@SpringBootTest
@Import({TestSecurityConfig.class, VoteSessionLeadCaptureFailureTest.MockConfig.class})
class VoteSessionLeadCaptureFailureTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        public TripLeadService tripLeadService() {
            TripLeadService broken = mock(TripLeadService.class);
            doThrow(new RuntimeException("lead capture down")).when(broken).createFromVoteSession(any());
            return broken;
        }
    }

    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private VoteSessionActivityRepository voteSessionActivityRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;

    @Test
    void processSession_completesEvenWhenLeadCaptureThrows() {
        Destination destination = destinationRepository.saveAndFlush(TestDataFactory.destination("Prague"));
        Activity activity = activityRepository.saveAndFlush(
                TestDataFactory.activity(destination, "Karting", new BigDecimal("50.00")));

        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail("organizer@example.com");
        session.setNumberOfTravelers(4);
        session.setStartDate(LocalDate.now().plusDays(10));
        session.setEndDate(LocalDate.now().plusDays(12));
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setVoteMode(VoteMode.CART);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC));
        session = voteSessionRepository.saveAndFlush(session);

        VoteSessionActivity ballotRow = new VoteSessionActivity();
        ballotRow.setSession(session);
        ballotRow.setActivity(activity);
        ballotRow.setActivityName("Karting");
        ballotRow.setPrice(new BigDecimal("50.00"));
        ballotRow.setSortOrder(0);
        voteSessionActivityRepository.saveAndFlush(ballotRow);

        voteSessionService.processSession(session);

        assertThat(voteSessionRepository.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(VoteSessionStatus.COMPLETED);
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `cd myhive-backend && ./gradlew test --tests '*TripLeadFromVoteSessionTest' --tests '*VoteSessionLeadCaptureFailureTest' --tests '*VoteSessionProcessSessionTest' --tests '*VoteSessionCartProcessTest'`
Expected: PASS (new tests + existing processSession tests still green — they use the real Spring context, so the new dependency wires in automatically).

- [ ] **Step 7: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/TripLeadService.java myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java myhive-backend/src/test/java/com/myhive/backend/service/TripLeadFromVoteSessionTest.java myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionLeadCaptureFailureTest.java
git commit -m "feat(leads): capture VOTE lead when a vote completes without a booking"
```

---

### Task 6: Reminder email — EmailService method, headers support, template

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java`
- Create: `myhive-backend/src/main/resources/templates/email/trip-reminder.html`
- Modify: `myhive-backend/src/main/resources/application.properties` (add `app.api.public-url`)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/TripReminderTemplateRenderTest.java`

**Interfaces:**
- Consumes: `TripLead`, `TripLeadActivity`, `TripLeadSource` (Task 1), `VotePoolActivityDTO` (existing).
- Produces: `EmailService.sendTripReminder(TripLead lead, int stage, List<TripLeadActivity> items, List<VotePoolActivityDTO> recommendations, String frontendUrl): void` — consumed by Task 7. `EmailSpec` gains an optional `Map<String, String> headers` field.

- [ ] **Step 1: Write the failing render test**

`service/TripReminderTemplateRenderTest.java` (model: `VoteCreatedTemplateRenderTest` — plain unit test with its own `SpringTemplateEngine`):

```java
package com.myhive.backend.service;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TripReminderTemplateRenderTest {

    private SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/templates/email/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }

    private Context baseContext() {
        Context context = new Context();
        context.setVariable("source", "QUIZ");
        context.setVariable("stage", 1);
        context.setVariable("lastTouch", false);
        context.setVariable("showConsultation", false);
        context.setVariable("destinationName", "Prague");
        context.setVariable("travelers", 6);
        context.setVariable("hasItems", false);
        context.setVariable("lines", List.of());
        context.setVariable("totalPrice", BigDecimal.ZERO);
        context.setVariable("recommendations", List.of());
        context.setVariable("restoreUrl", "https://trivlu.com/destination/prague?tab=trip-builder&restore=tok-1");
        context.setVariable("contactUrl", "https://trivlu.com/contact");
        context.setVariable("unsubscribeUrl", "https://trivlu.com/unsubscribe?token=unsub-1");
        context.setVariable("supportEmail", "support@trivlu.com");
        return context;
    }

    @Test
    void rendersCartLinesTotalAndLinks() {
        EmailService.ReminderLineView line = new EmailService.ReminderLineView();
        line.name = "Karting";
        line.price = new BigDecimal("50.00");
        line.lineTotal = new BigDecimal("300.00");
        line.groupMinApplies = false;
        EmailService.ReminderLineView floored = new EmailService.ReminderLineView();
        floored.name = "Shooting Range";
        floored.price = new BigDecimal("40.00");
        floored.lineTotal = new BigDecimal("400.00");
        floored.groupMinApplies = true;

        Context context = baseContext();
        context.setVariable("hasItems", true);
        context.setVariable("lines", List.of(line, floored));
        context.setVariable("totalPrice", new BigDecimal("700.00"));

        String html = engine().process("trip-reminder", context);

        assertThat(html)
                .contains("Karting")
                .contains("Shooting Range")
                .contains("group minimum")
                .contains("https://trivlu.com/destination/prague?tab=trip-builder&amp;restore=tok-1")
                .contains("https://trivlu.com/unsubscribe?token=unsub-1")
                .contains("Prague");
    }

    @Test
    void rendersRecommendationsWhenCartIsEmpty() {
        Context context = baseContext();
        context.setVariable("recommendations", List.of(
                Map.of("name", "Beer Bike", "price", new BigDecimal("35.00"))));

        String html = engine().process("trip-reminder", context);

        assertThat(html)
                .contains("Beer Bike")
                .contains("we picked for your group")
                .doesNotContain("group minimum");
    }

    @Test
    void rendersConsultationAndUrgencyBlocksByStage() {
        Context consultationContext = baseContext();
        consultationContext.setVariable("stage", 2);
        consultationContext.setVariable("showConsultation", true);
        String consultationHtml = engine().process("trip-reminder", consultationContext);

        Context urgencyContext = baseContext();
        urgencyContext.setVariable("stage", 3);
        urgencyContext.setVariable("lastTouch", true);
        String urgencyHtml = engine().process("trip-reminder", urgencyContext);

        assertThat(consultationHtml).contains("Need a hand?");
        assertThat(urgencyHtml).contains("fill up early");
        assertThat(consultationHtml).doesNotContain("fill up early");
    }
}
```

Note: recommendations are rendered via `${rec.name}`/`${rec.price}` — a `Map` works in the test because Thymeleaf resolves map keys like properties; production passes `VotePoolActivityDTO` objects with the same property names.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd myhive-backend && ./gradlew test --tests '*TripReminderTemplateRenderTest'`
Expected: FAIL — template `trip-reminder` not found (and `ReminderLineView` missing → compile failure first).

- [ ] **Step 3: Write the template**

`templates/email/trip-reminder.html`:

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Your Trip Is Waiting</title>
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; background: #f0f0f0; margin: 0; padding: 20px 0; }
        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 8px; overflow: hidden; }
        .header { background: #6A1B9A; color: white; padding: 32px 30px; text-align: center; border-bottom: 3px solid #4A148C; }
        .header h1 { margin: 12px 0 8px; font-size: 22px; font-weight: 700; color: #f5f5f5; }
        .content { padding: 30px; }
        .item-table { width: 100%; border-collapse: collapse; margin: 16px 0; }
        .item-table td { padding: 8px 4px; border-bottom: 1px solid #eee; font-size: 14px; }
        .item-table .price-cell { text-align: right; white-space: nowrap; }
        .total-row td { font-weight: 700; border-bottom: none; }
        .group-min-note { color: #666; font-size: 12px; }
        .section { margin: 20px 0; padding: 18px 20px; border-left: 4px solid #6A1B9A; background: #f8f9fa; border-radius: 0 6px 6px 0; }
        .section h2 { margin: 0 0 12px; font-size: 16px; color: #1f2121; }
        .cta-button { display: block; width: fit-content; margin: 30px auto; padding: 14px 32px; background: #6A1B9A; color: white; text-decoration: none; border-radius: 6px; font-weight: 600; font-size: 16px; text-align: center; }
        .muted { color: #666; font-size: 14px; }
        .footer { background: #4A148C; padding: 24px 20px; text-align: center; border-top: 3px solid #6A1B9A; }
        .footer p { color: rgba(245,245,245,0.6); font-size: 12px; margin: 4px 0; }
        .footer a { color: rgba(245,245,245,0.85); }
    </style>
</head>
<body>
<div class="container">
    <div class="header">
        <img src="https://trivlu.com/logo-white.png" alt="Trivlu Travel" style="max-height: 56px;">
        <h1 th:if="${source == 'VOTE'}">Your group has voted!</h1>
        <h1 th:unless="${source == 'VOTE'}" th:text="'Your ' + ${destinationName} + ' trip is waiting'">Your Prague trip is waiting</h1>
        <p th:if="${source == 'VOTE'}" th:text="'Trip to ' + ${destinationName}" style="margin: 0; color: rgba(245,245,245,0.75); font-size: 14px;">Trip to Prague</p>
    </div>
    <div class="content">
        <p th:if="${source == 'VOTE'}">
            The votes are in for your <strong th:text="${destinationName}">Prague</strong> trip —
            the winning activities are saved and ready to book whenever you are.
        </p>
        <p th:unless="${source == 'VOTE'}">
            You started planning a trip to <strong th:text="${destinationName}">Prague</strong>
            for <span th:text="${travelers}">6</span> people — we saved everything so you can pick up
            right where you left off.
        </p>

        <!-- Saved cart -->
        <div th:if="${hasItems}">
            <h2 style="font-size: 16px;">Your saved itinerary</h2>
            <table class="item-table">
                <tr th:each="line : ${lines}">
                    <td>
                        <span th:text="${line.name}">Karting</span>
                        <span class="group-min-note" th:if="${line.groupMinApplies}">(group minimum applies)</span>
                    </td>
                    <td class="price-cell">
                        <span th:text="'€' + ${line.price} + ' / person'">€50.00 / person</span>
                    </td>
                </tr>
                <tr class="total-row">
                    <td th:text="'Total for ' + ${travelers} + ' people'">Total for 6 people</td>
                    <td class="price-cell" th:text="'€' + ${totalPrice}">€700.00</td>
                </tr>
            </table>
        </div>

        <!-- No cart yet: quiz-based recommendations -->
        <div th:if="${!hasItems and !recommendations.isEmpty()}" class="section">
            <h2>A few activities we picked for your group</h2>
            <table class="item-table">
                <tr th:each="rec : ${recommendations}">
                    <td th:text="${rec.name}">Beer Bike</td>
                    <td class="price-cell" th:text="'€' + ${rec.price} + ' / person'">€35.00 / person</td>
                </tr>
            </table>
        </div>

        <p th:if="${!hasItems and recommendations.isEmpty()}" class="muted">
            Your trip details are saved — jump back in and pick the activities your group will love.
        </p>

        <!-- Stage 2 (QUIZ) / stage 1 (VOTE): reassurance + consultation -->
        <div th:if="${showConsultation}" class="section">
            <h2>Need a hand?</h2>
            <p style="margin: 0; font-size: 14px;">
                Planning for a group can be a lot. Tell us what you have in mind and we'll put the
                itinerary together with you — no strings attached.
                <a th:href="${contactUrl}">Get in touch</a>.
            </p>
        </div>

        <!-- Final touch: honest urgency -->
        <p th:if="${lastTouch}">
            One last nudge from us — popular dates and time slots fill up early, so booking ahead
            keeps your group's first choices available. This is the last reminder we'll send.
        </p>

        <a th:href="${restoreUrl}" class="cta-button" style="color: #ffffff !important;"
           th:text="${source == 'VOTE'} ? 'Book the winning trip' : 'Continue planning your trip'">Continue planning your trip</a>

        <p class="muted">
            This link opens your saved trip on any device.
            Questions? Email us at
            <a th:href="'mailto:' + ${supportEmail}" th:text="${supportEmail}">support@trivlu.com</a>.
        </p>
    </div>
    <div class="footer">
        <img src="https://trivlu.com/logo-white.png" alt="Trivlu Travel" style="max-height: 36px; margin-bottom: 10px;">
        <p>Creating unforgettable travel experiences</p>
        <p>You're getting this because you saved a trip on trivlu.com.</p>
        <p><a th:href="${unsubscribeUrl}">Unsubscribe from trip reminders</a></p>
        <p>For support, contact us at support@trivlu.com</p>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 4: Extend EmailService**

In `EmailService.java`:

1. New imports: `com.myhive.backend.entity.TripLead`, `com.myhive.backend.entity.TripLeadActivity`, `com.myhive.backend.dto.VotePoolActivityDTO`, `com.myhive.backend.model.TripLeadSource`.
2. Add to `EmailSpec` record a new component after `variables`: `Map<String, String> headers,` (builder picks it up automatically).
3. In `send(...)`, after `helper.setSubject(spec.subject());`:

```java
            if (spec.headers() != null) {
                for (Map.Entry<String, String> header : spec.headers().entrySet()) {
                    message.setHeader(header.getKey(), header.getValue());
                }
            }
```

4. Add the config field next to the other `@Value` fields:

```java
    /** Public base URL of this API (RFC 8058 one-click unsubscribe target); blank = headers omitted. */
    @Value("${app.api.public-url:}")
    private String apiPublicUrl;
```

5. Add the public method + helpers:

```java
    /** One reminder itinerary line: snapshot name/price plus the floored group total. */
    public static class ReminderLineView {
        public String name;
        public BigDecimal price;
        public BigDecimal lineTotal;
        public boolean groupMinApplies;
    }

    public void sendTripReminder(TripLead lead, int stage, List<TripLeadActivity> items,
            List<VotePoolActivityDTO> recommendations, String frontendUrl) {
        String destinationName = lead.getDestination() == null
                ? "your trip" : lead.getDestination().getName();
        int travelers = lead.getNumberOfTravelers() == null || lead.getNumberOfTravelers() < 1
                ? 1 : lead.getNumberOfTravelers();
        List<ReminderLineView> lines = items.stream()
                .map(item -> toReminderLine(item, travelers))
                .toList();
        BigDecimal totalPrice = lines.stream()
                .map(line -> line.lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean lastTouch = lead.getSource() == TripLeadSource.VOTE ? stage >= 2 : stage >= 3;
        boolean showConsultation = lead.getSource() == TripLeadSource.VOTE ? stage == 1 : stage == 2;

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("source", lead.getSource().name());
        variables.put("stage", stage);
        variables.put("lastTouch", lastTouch);
        variables.put("showConsultation", showConsultation);
        variables.put("destinationName", destinationName);
        variables.put("travelers", travelers);
        variables.put("hasItems", !lines.isEmpty());
        variables.put("lines", lines);
        variables.put("totalPrice", totalPrice);
        variables.put("recommendations", recommendations);
        variables.put("restoreUrl", restoreUrlFor(lead, frontendUrl));
        variables.put("contactUrl", frontendUrl + "/contact");
        variables.put("unsubscribeUrl", frontendUrl + "/unsubscribe?token=" + lead.getUnsubscribeToken());
        variables.put("supportEmail", SUPPORT_EMAIL);

        send(EmailSpec.builder()
                .to(lead.getEmail())
                .subject(reminderSubject(lead.getSource(), stage, destinationName))
                .template("trip-reminder")
                .variables(variables)
                .headers(unsubscribeHeaders(lead))
                .description("trip reminder (stage " + stage + ") to " + maskEmail(lead.getEmail()))
                .build());
    }

    private static ReminderLineView toReminderLine(TripLeadActivity item, int travelers) {
        ReminderLineView line = new ReminderLineView();
        line.name = item.getActivityName();
        line.price = item.getPrice();
        BigDecimal groupTotal = item.getPrice().multiply(BigDecimal.valueOf(travelers));
        boolean floored = item.getMinPrice() != null && groupTotal.compareTo(item.getMinPrice()) < 0;
        line.groupMinApplies = floored;
        line.lineTotal = floored ? item.getMinPrice() : groupTotal;
        return line;
    }

    /** Mirrors resultUrlFor: the Trip Builder tab deep link, restore token appended. */
    private static String restoreUrlFor(TripLead lead, String frontendUrl) {
        String destinationSlug = lead.getDestination() == null ? null : lead.getDestination().getSlug();
        if (destinationSlug == null) {
            return frontendUrl;
        }
        return frontendUrl + "/destination/" + destinationSlug
                + "?tab=trip-builder&restore=" + lead.getRestoreToken();
    }

    private static String reminderSubject(TripLeadSource source, int stage, String destinationName) {
        if (source == TripLeadSource.VOTE) {
            return stage == 1
                    ? "Your group voted — ready to book " + destinationName + "?"
                    : "Still thinking it over? Your " + destinationName + " trip is saved";
        }
        return switch (stage) {
            case 1 -> "Your " + destinationName + " trip is waiting";
            case 2 -> "Need a hand planning " + destinationName + "?";
            default -> "Last call — your saved " + destinationName + " trip";
        };
    }

    private Map<String, String> unsubscribeHeaders(TripLead lead) {
        if (apiPublicUrl == null || apiPublicUrl.isBlank()) {
            return Map.of();
        }
        String oneClickUrl = apiPublicUrl + "/leads/unsubscribe/one-click?token=" + lead.getUnsubscribeToken();
        return Map.of(
                "List-Unsubscribe", "<" + oneClickUrl + ">",
                "List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
    }
```

6. In `application.properties`, after `app.email.enabled`:

```properties
# Public base URL of this API (for RFC 8058 List-Unsubscribe headers); empty = headers omitted
app.api.public-url=${API_PUBLIC_URL:}
```

- [ ] **Step 5: Run the tests**

Run: `cd myhive-backend && ./gradlew test --tests '*TripReminderTemplateRenderTest' --tests '*EmailServiceTest'`
Expected: PASS (3 new render tests; existing EmailService tests unaffected by the additive `headers` field).

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java myhive-backend/src/main/resources/templates/email/trip-reminder.html myhive-backend/src/main/resources/application.properties myhive-backend/src/test/java/com/myhive/backend/service/TripReminderTemplateRenderTest.java
git commit -m "feat(leads): adaptive trip-reminder email with one-click unsubscribe headers"
```

---

### Task 7: Reminder engine — TripLeadReminderService + scheduler + kill switch

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/TripLeadReminderService.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/TripLeadReminderScheduler.java`
- Modify: `myhive-backend/src/main/resources/application.properties` (add `app.leads.reminders-enabled`)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/TripLeadReminderServiceTest.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/TripLeadReminderSchedulerTest.java`

**Interfaces:**
- Consumes: Task 1 repos, Task 2 queries, Task 6 `EmailService.sendTripReminder`, existing `VotePoolService.buildPool(VotePoolRequest): VotePoolResponse`.
- Produces: `TripLeadReminderService.processReminder(UUID leadId): void`; `TripLeadReminderScheduler.processDueReminders()` (fixedDelay 10 min) and `cleanupOldLeads()` (cron 02:30, 30-day retention).

- [ ] **Step 1: Write the failing service test**

`service/TripLeadReminderServiceTest.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.entity.Booking;
import com.myhive.backend.entity.EmailSuppression;
import com.myhive.backend.entity.TripLead;
import com.myhive.backend.model.BookingStatus;
import com.myhive.backend.model.TripLeadSource;
import com.myhive.backend.model.TripLeadStatus;
import com.myhive.backend.repository.BookingRepository;
import com.myhive.backend.repository.EmailSuppressionRepository;
import com.myhive.backend.repository.TripLeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

// Not @Transactional: processReminder manages its own transaction; state is cleaned per test.
@SpringBootTest
@Import({TestSecurityConfig.class, TripLeadReminderServiceTest.MockConfig.class})
class TripLeadReminderServiceTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        public EmailService emailService() {
            return mock(EmailService.class);
        }
    }

    @Autowired private TripLeadReminderService reminderService;
    @Autowired private TripLeadRepository tripLeadRepository;
    @Autowired private EmailSuppressionRepository emailSuppressionRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private EmailService emailService;

    @BeforeEach
    void setUp() {
        // No deleteAll: the class is not @Transactional and shares the cached-context DB with
        // other test classes — isolation comes from a unique email per test instead.
        reset(emailService);
    }

    private TripLead lead(String email, TripLeadSource source, int stage, LocalDateTime lastActivityAt) {
        TripLead lead = new TripLead();
        lead.setEmail(email);
        lead.setSource(source);
        lead.setRestoreToken(UUID.randomUUID());
        lead.setUnsubscribeToken(UUID.randomUUID());
        lead.setStatus(TripLeadStatus.ACTIVE);
        lead.setReminderStage(stage);
        lead.setLastActivityAt(lastActivityAt);
        return tripLeadRepository.saveAndFlush(lead);
    }

    private LocalDateTime hoursAgo(int hours) {
        return LocalDateTime.now(ZoneOffset.UTC).minusHours(hours);
    }

    @Test
    void quizLead_firstReminderSentAfterOneHour() {
        TripLead due = lead("quiz-due@example.com", TripLeadSource.QUIZ, 0, hoursAgo(2));

        reminderService.processReminder(due.getId());

        verify(emailService).sendTripReminder(any(), anyInt(), anyList(), anyList(), anyString());
        TripLead updated = tripLeadRepository.findById(due.getId()).orElseThrow();
        assertThat(updated.getReminderStage()).isEqualTo(1);
        assertThat(updated.getStatus()).isEqualTo(TripLeadStatus.ACTIVE);
        assertThat(updated.getLastReminderAt()).isNotNull();
    }

    @Test
    void quizLead_notDueYet_nothingHappens() {
        TripLead notDue = lead("quiz-early@example.com", TripLeadSource.QUIZ, 0,
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(30));

        reminderService.processReminder(notDue.getId());

        verify(emailService, never()).sendTripReminder(any(), anyInt(), anyList(), anyList(), anyString());
        assertThat(tripLeadRepository.findById(notDue.getId()).orElseThrow().getReminderStage()).isZero();
    }

    @Test
    void quizLead_finalStageCompletesSeries() {
        TripLead lastStage = lead("quiz-final@example.com", TripLeadSource.QUIZ, 2, hoursAgo(73));

        reminderService.processReminder(lastStage.getId());

        TripLead updated = tripLeadRepository.findById(lastStage.getId()).orElseThrow();
        assertThat(updated.getReminderStage()).isEqualTo(3);
        assertThat(updated.getStatus()).isEqualTo(TripLeadStatus.COMPLETED);
    }

    @Test
    void voteLead_firstReminderWaits24Hours() {
        TripLead tooEarly = lead("vote-early@example.com", TripLeadSource.VOTE, 0, hoursAgo(2));

        reminderService.processReminder(tooEarly.getId());

        verify(emailService, never()).sendTripReminder(any(), anyInt(), anyList(), anyList(), anyString());
    }

    @Test
    void voteLead_secondReminderCompletesSeries() {
        TripLead lastStage = lead("vote-final@example.com", TripLeadSource.VOTE, 1, hoursAgo(73));

        reminderService.processReminder(lastStage.getId());

        TripLead updated = tripLeadRepository.findById(lastStage.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TripLeadStatus.COMPLETED);
    }

    @Test
    void suppressedEmail_marksUnsubscribedWithoutSending() {
        TripLead due = lead("suppressed@example.com", TripLeadSource.QUIZ, 0, hoursAgo(2));
        EmailSuppression suppression = new EmailSuppression();
        suppression.setEmail("suppressed@example.com");
        emailSuppressionRepository.saveAndFlush(suppression);

        reminderService.processReminder(due.getId());

        verify(emailService, never()).sendTripReminder(any(), anyInt(), anyList(), anyList(), anyString());
        assertThat(tripLeadRepository.findById(due.getId()).orElseThrow().getStatus())
                .isEqualTo(TripLeadStatus.UNSUBSCRIBED);
    }

    @Test
    void bookingByEmail_marksConvertedWithoutSending() {
        TripLead due = lead("converted@example.com", TripLeadSource.QUIZ, 0, hoursAgo(2));
        Booking booking = TestDataFactory.booking(BookingStatus.PENDING);
        booking.setId(null);
        booking.setUserEmail("Converted@Example.com");
        bookingRepository.saveAndFlush(booking);

        reminderService.processReminder(due.getId());

        verify(emailService, never()).sendTripReminder(any(), anyInt(), anyList(), anyList(), anyString());
        assertThat(tripLeadRepository.findById(due.getId()).orElseThrow().getStatus())
                .isEqualTo(TripLeadStatus.CONVERTED);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd myhive-backend && ./gradlew test --tests '*TripLeadReminderServiceTest'`
Expected: COMPILE FAILURE (`TripLeadReminderService` missing).

- [ ] **Step 3: Write TripLeadReminderService**

`service/TripLeadReminderService.java`:

```java
package com.myhive.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhive.backend.dto.QuizResponseDTO;
import com.myhive.backend.dto.VotePoolActivityDTO;
import com.myhive.backend.dto.VotePoolRequest;
import com.myhive.backend.entity.TripLead;
import com.myhive.backend.entity.TripLeadActivity;
import com.myhive.backend.model.TripLeadSource;
import com.myhive.backend.model.TripLeadStatus;
import com.myhive.backend.repository.BookingRepository;
import com.myhive.backend.repository.EmailSuppressionRepository;
import com.myhive.backend.repository.TripLeadActivityRepository;
import com.myhive.backend.repository.TripLeadRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripLeadReminderService {

    static final Duration[] QUIZ_CADENCE = {Duration.ofHours(1), Duration.ofHours(24), Duration.ofHours(72)};
    static final Duration[] VOTE_CADENCE = {Duration.ofHours(24), Duration.ofHours(72)};

    private final TripLeadRepository tripLeadRepository;
    private final TripLeadActivityRepository tripLeadActivityRepository;
    private final EmailSuppressionRepository emailSuppressionRepository;
    private final BookingRepository bookingRepository;
    private final VoteSessionRepository voteSessionRepository;
    private final VotePoolService votePoolService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend.url:https://trivlu.com}")
    private String frontendUrl;

    @Transactional
    public void processReminder(UUID leadId) {
        TripLead lead = tripLeadRepository.findById(leadId).orElse(null);
        if (lead == null || lead.getStatus() != TripLeadStatus.ACTIVE) {
            return;
        }
        Duration[] cadence = cadenceFor(lead.getSource());
        if (lead.getReminderStage() >= cadence.length) {
            // Repurposed/legacy edge — series already exhausted; close it out.
            lead.setStatus(TripLeadStatus.COMPLETED);
            tripLeadRepository.save(lead);
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (now.isBefore(lead.getLastActivityAt().plus(cadence[lead.getReminderStage()]))) {
            return; // not due yet — user activity pushes every remaining stage out
        }
        if (emailSuppressionRepository.existsByEmail(lead.getEmail())) {
            lead.setStatus(TripLeadStatus.UNSUBSCRIBED);
            tripLeadRepository.save(lead);
            return;
        }
        if (hasConverted(lead)) {
            lead.setStatus(TripLeadStatus.CONVERTED);
            tripLeadRepository.save(lead);
            return;
        }
        int stage = lead.getReminderStage() + 1;
        lead.setReminderStage(stage);
        lead.setLastReminderAt(now);
        if (stage >= cadence.length) {
            lead.setStatus(TripLeadStatus.COMPLETED); // final touch — series over
        }
        tripLeadRepository.save(lead);
        sendReminderQuietly(lead, stage);
    }

    static Duration[] cadenceFor(TripLeadSource source) {
        return source == TripLeadSource.VOTE ? VOTE_CADENCE : QUIZ_CADENCE;
    }

    private boolean hasConverted(TripLead lead) {
        if (bookingRepository.existsByUserEmailIgnoreCaseAndCreatedAtAfter(
                lead.getEmail(), lead.getCreatedAt())) {
            return true;
        }
        if (lead.getSource() == TripLeadSource.VOTE && lead.getVoteSessionId() != null
                && bookingRepository.existsByVoteSessionId(lead.getVoteSessionId())) {
            return true;
        }
        return lead.getSource() == TripLeadSource.QUIZ
                && voteSessionRepository.existsByInitiatorEmailIgnoreCaseAndCreatedAtAfter(
                        lead.getEmail(), lead.getCreatedAt());
    }

    private void sendReminderQuietly(TripLead lead, int stage) {
        try {
            List<TripLeadActivity> items =
                    tripLeadActivityRepository.findByLeadIdOrderBySortOrder(lead.getId());
            List<VotePoolActivityDTO> recommendations =
                    items.isEmpty() ? buildRecommendations(lead) : List.of();
            emailService.sendTripReminder(lead, stage, items, recommendations, frontendUrl);
        } catch (Exception e) {
            // The stage advance must commit even if the hand-off fails, or the next tick would
            // resend the same stage forever. Delivery is best-effort, like the vote-result email.
            log.error("Failed to send trip reminder for lead {}: {}", lead.getId(), e.getMessage(), e);
        }
    }

    private List<VotePoolActivityDTO> buildRecommendations(TripLead lead) {
        if (lead.getQuizResponsesJson() == null || lead.getDestination() == null) {
            return List.of();
        }
        try {
            List<QuizResponseDTO> responses = objectMapper.readValue(
                    lead.getQuizResponsesJson(), new TypeReference<List<QuizResponseDTO>>() {});
            VotePoolRequest request = new VotePoolRequest();
            request.setDestinationId(lead.getDestination().getId());
            request.setResponses(responses);
            List<VotePoolActivityDTO> pool = votePoolService.buildPool(request).getPool();
            return pool.size() > 3 ? pool.subList(0, 3) : pool;
        } catch (Exception e) {
            // Malformed stored answers must not block the reminder — send it without recommendations.
            log.warn("Could not build recommendations for lead {}: {}", lead.getId(), e.getMessage());
            return List.of();
        }
    }
}
```

- [ ] **Step 4: Run service test to verify it passes**

Run: `cd myhive-backend && ./gradlew test --tests '*TripLeadReminderServiceTest'`
Expected: PASS (7 tests).

- [ ] **Step 5: Write the failing scheduler test**

`service/TripLeadReminderSchedulerTest.java` (model: `VoteSessionSchedulerTest` — plain Mockito):

```java
package com.myhive.backend.service;

import com.myhive.backend.entity.TripLead;
import com.myhive.backend.model.TripLeadStatus;
import com.myhive.backend.repository.TripLeadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripLeadReminderSchedulerTest {

    @Mock private TripLeadRepository tripLeadRepository;
    @Mock private TripLeadReminderService tripLeadReminderService;

    @InjectMocks
    private TripLeadReminderScheduler scheduler;

    private TripLead activeLead() {
        TripLead lead = new TripLead();
        lead.setId(UUID.randomUUID());
        lead.setStatus(TripLeadStatus.ACTIVE);
        return lead;
    }

    private void enableFlags() {
        ReflectionTestUtils.setField(scheduler, "remindersEnabled", true);
        ReflectionTestUtils.setField(scheduler, "emailEnabled", true);
    }

    @Test
    void processDueReminders_delegatesPerActiveLead() {
        enableFlags();
        TripLead lead1 = activeLead();
        TripLead lead2 = activeLead();
        when(tripLeadRepository.findByStatus(TripLeadStatus.ACTIVE)).thenReturn(List.of(lead1, lead2));

        scheduler.processDueReminders();

        verify(tripLeadReminderService).processReminder(lead1.getId());
        verify(tripLeadReminderService).processReminder(lead2.getId());
    }

    @Test
    void processDueReminders_continuesOnError() {
        enableFlags();
        TripLead failing = activeLead();
        TripLead healthy = activeLead();
        when(tripLeadRepository.findByStatus(TripLeadStatus.ACTIVE)).thenReturn(List.of(failing, healthy));
        doThrow(new RuntimeException("boom")).when(tripLeadReminderService).processReminder(failing.getId());

        scheduler.processDueReminders();

        verify(tripLeadReminderService).processReminder(healthy.getId());
    }

    @Test
    void processDueReminders_noopWhenRemindersDisabled() {
        ReflectionTestUtils.setField(scheduler, "remindersEnabled", false);
        ReflectionTestUtils.setField(scheduler, "emailEnabled", true);

        scheduler.processDueReminders();

        verify(tripLeadReminderService, never()).processReminder(any());
    }

    @Test
    void processDueReminders_noopWhenEmailDisabled() {
        // A disabled mailer must not silently burn the series stages.
        ReflectionTestUtils.setField(scheduler, "remindersEnabled", true);
        ReflectionTestUtils.setField(scheduler, "emailEnabled", false);

        scheduler.processDueReminders();

        verify(tripLeadReminderService, never()).processReminder(any());
    }

    @Test
    void cleanupOldLeads_deletesByRetentionCutoff() {
        when(tripLeadRepository.deleteByUpdatedAtBefore(any())).thenReturn(3);

        scheduler.cleanupOldLeads();

        verify(tripLeadRepository).deleteByUpdatedAtBefore(any());
    }
}
```

- [ ] **Step 6: Write the scheduler + property**

`service/TripLeadReminderScheduler.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.entity.TripLead;
import com.myhive.backend.model.TripLeadStatus;
import com.myhive.backend.repository.TripLeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class TripLeadReminderScheduler {

    private final TripLeadRepository tripLeadRepository;
    private final TripLeadReminderService tripLeadReminderService;

    /** Kill switch — capture keeps working when off, only the sending stops. */
    @Value("${app.leads.reminders-enabled:true}")
    private boolean remindersEnabled;

    /** With the mailer off, ticking would silently burn series stages — skip instead. */
    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Scheduled(fixedDelay = 600_000)
    public void processDueReminders() {
        if (!remindersEnabled || !emailEnabled) {
            return;
        }
        for (TripLead lead : tripLeadRepository.findByStatus(TripLeadStatus.ACTIVE)) {
            try {
                tripLeadReminderService.processReminder(lead.getId());
            } catch (Exception e) {
                log.error("Failed to process trip lead {}: {}", lead.getId(), e.getMessage(), e);
            }
        }
    }

    /** GDPR retention: leads vanish 30 days after their last touch; suppression rows never do. */
    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void cleanupOldLeads() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(30);
        int deleted = tripLeadRepository.deleteByUpdatedAtBefore(cutoff);
        log.info("Cleaned up {} trip leads", deleted);
    }
}
```

In `application.properties`, after `app.api.public-url`:

```properties
# Trip lead reminder emails (abandoned-trip series) — kill switch
app.leads.reminders-enabled=${REMINDERS_ENABLED:true}
```

- [ ] **Step 7: Run the tests**

Run: `cd myhive-backend && ./gradlew test --tests '*TripLeadReminderSchedulerTest' --tests '*TripLeadReminderServiceTest'`
Expected: PASS (12 tests).

- [ ] **Step 8: Run the full backend suite and commit**

Run: `cd myhive-backend && ./gradlew test`
Expected: PASS.

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/TripLeadReminderService.java myhive-backend/src/main/java/com/myhive/backend/service/TripLeadReminderScheduler.java myhive-backend/src/main/resources/application.properties myhive-backend/src/test/java/com/myhive/backend/service/TripLeadReminderServiceTest.java myhive-backend/src/test/java/com/myhive/backend/service/TripLeadReminderSchedulerTest.java
git commit -m "feat(leads): reminder cadence engine with stop conditions and kill switch"
```

---

### Task 8: Frontend capture — leadApi, storage util, consent notices, conversion cleanup

**Files:**
- Create: `myhive-react-app/src/services/leadApi.js`
- Create: `myhive-react-app/src/utils/tripLead.js`
- Create: `myhive-react-app/src/components/EmailConsentNote.js`
- Modify: `myhive-react-app/src/hooks/useStartGroupVote.js` (fire-and-forget lead capture)
- Modify: `myhive-react-app/src/components/TripSetupModal.js` (consent note under vote email field, ~line 226)
- Modify: `myhive-react-app/src/components/vote/StartGroupVoteModal.js` (consent note + `clearTripLead()` on success)
- Modify: `myhive-react-app/src/components/TripBuilder.js` (`clearTripLead()` in `handleQuizVoteCreate` and `handleContactSubmit`)
- Test: `myhive-react-app/src/hooks/useStartGroupVote.test.js` (extend existing)

**Interfaces:**
- Produces: `leadApi.createLead(payload): Promise<{id, restoreToken}>`, `leadApi.syncLead(id, body)` (throws `Error('LEAD_GONE')` on 404), `leadApi.restoreLead(token)`, `leadApi.unsubscribe(token)`; `readTripLead()/writeTripLead(lead)/clearTripLead()` over localStorage key `myhive-trip-lead`.

- [ ] **Step 1: Write leadApi and the storage util**

`services/leadApi.js`:

```js
import { API_BASE_URL } from './config';

const leadApi = {
  // Fire-and-forget capture at quiz-vote setup; server dedups by email.
  async createLead({ email, destinationId, numberOfTravelers, startDate, endDate, budget }) {
    const response = await fetch(`${API_BASE_URL}/leads`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, destinationId, numberOfTravelers, startDate, endDate, budget }),
    });
    if (!response.ok) throw new Error('Failed to save trip lead');
    return response.json();
  },

  async syncLead(id, body) {
    const response = await fetch(`${API_BASE_URL}/leads/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (response.status === 404) throw new Error('LEAD_GONE');
    if (!response.ok) throw new Error('Failed to sync trip lead');
  },

  async restoreLead(token) {
    const response = await fetch(`${API_BASE_URL}/leads/restore/${encodeURIComponent(token)}`);
    if (!response.ok) throw new Error('Failed to restore trip');
    return response.json();
  },

  async unsubscribe(token) {
    const response = await fetch(`${API_BASE_URL}/leads/unsubscribe`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    });
    if (!response.ok) throw new Error('Failed to unsubscribe');
  },
};

export default leadApi;
```

`utils/tripLead.js`:

```js
// The active reminder lead this browser is syncing to: {id, restoreToken}.
// localStorage (not sessionStorage) so the sync continues in later Trip Builder visits.
const TRIP_LEAD_KEY = 'myhive-trip-lead';

export function readTripLead() {
  try {
    const raw = localStorage.getItem(TRIP_LEAD_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    // Malformed/blocked storage — behave as if no lead is being tracked.
    return null;
  }
}

export function writeTripLead(lead) {
  try {
    localStorage.setItem(TRIP_LEAD_KEY, JSON.stringify(lead));
  } catch (e) {
    // Blocked storage must never break the flow that captured the lead.
  }
}

export function clearTripLead() {
  try {
    localStorage.removeItem(TRIP_LEAD_KEY);
  } catch (e) {
    // Same rationale as writeTripLead.
  }
}
```

- [ ] **Step 2: Write the consent note component and place it**

`components/EmailConsentNote.js`:

```js
// GDPR notice shown wherever we capture an email that later feeds reminder emails.
function EmailConsentNote() {
    return (
        <p style={{fontSize: '0.8rem', color: '#6c757d', margin: '4px 0 0'}}>
            We&apos;ll email you a link to your trip and a couple of reminders. Unsubscribe anytime.
        </p>
    );
}

export default EmailConsentNote;
```

In `TripSetupModal.js`: import `EmailConsentNote` and render it directly after the vote email `<input>` (inside the `isVoteMode` form-group, after the input closing tag ~line 226):

```jsx
                                <EmailConsentNote />
```

In `StartGroupVoteModal.js`: import `EmailConsentNote` and render it after the email error span (`{errors.email && ...}`, line 116):

```jsx
            <EmailConsentNote />
```

- [ ] **Step 3: Extend the existing hook test**

Add to `hooks/useStartGroupVote.test.js` — a `jest.mock('../services/leadApi')` at module scope, and two tests (mock return values defined inside each test — CRA `resetMocks`):

```js
import leadApi from '../services/leadApi';

jest.mock('../services/leadApi');

// ...inside the existing describe, reusing the existing Harness that calls handleVoteConfirm:

test('captures a trip lead on vote confirm and stores its tokens', async () => {
    leadApi.createLead.mockResolvedValue({ id: 'lead-1', restoreToken: 'tok-1' });

    // drive the existing harness so handleVoteConfirm fires with
    // {travelers: 2, startDate: '2026-09-01', endDate: '2026-09-03',
    //  email: 'a@b.com', destination: {id: 'dest-1'}, budget: null}

    await waitFor(() => expect(leadApi.createLead).toHaveBeenCalledWith(
        expect.objectContaining({ email: 'a@b.com', destinationId: 'dest-1' })));
    await waitFor(() => expect(
        JSON.parse(localStorage.getItem('myhive-trip-lead'))).toEqual(
        { id: 'lead-1', restoreToken: 'tok-1' }));
});

test('a failed lead capture does not block navigation to the quiz', async () => {
    leadApi.createLead.mockRejectedValue(new Error('down'));

    // drive the same confirm; assert the quiz route stub still rendered
});
```

(Adapt the driving code to the file's existing `Harness`/`QuizStub` setup — it already asserts navigation to `/vote/new/quiz`.)

- [ ] **Step 4: Wire capture into useStartGroupVote**

In `hooks/useStartGroupVote.js`, add imports:

```js
import leadApi from '../services/leadApi';
import {writeTripLead} from '../utils/tripLead';
```

and in `handleVoteConfirm`, before `navigate(...)`:

```js
        // Fire-and-forget lead capture: a failure must never block the quiz flow.
        leadApi.createLead({
            email,
            destinationId: destination.id,
            numberOfTravelers: travelers,
            startDate: startDate || null,
            endDate: endDate || null,
            budget,
        }).then(writeTripLead).catch(() => {});
```

- [ ] **Step 5: Clear the lead on conversion**

- `StartGroupVoteModal.js` — import `clearTripLead` from `'../../utils/tripLead'`; in `handleCreate` after `localStorage.setItem('myhive-trip-vote-session', ...)`: add `clearTripLead();`
- `TripBuilder.js` — import `clearTripLead` from `'../utils/tripLead'`; in `handleQuizVoteCreate` next to `clearQuizFlow()` (~line 598) add `clearTripLead();`; in `handleContactSubmit` next to `clearQuizFlow()` (~line 499) add `clearTripLead();`

(The backend stop-conditions are authoritative; this is hygiene that also stops the sync loop.)

- [ ] **Step 6: Run the tests**

Run: `cd myhive-react-app && npm test -- --watchAll=false useStartGroupVote`
Expected: PASS (existing + 2 new tests).

- [ ] **Step 7: Commit**

```bash
git add myhive-react-app/src/services/leadApi.js myhive-react-app/src/utils/tripLead.js myhive-react-app/src/components/EmailConsentNote.js myhive-react-app/src/hooks/useStartGroupVote.js myhive-react-app/src/hooks/useStartGroupVote.test.js myhive-react-app/src/components/TripSetupModal.js myhive-react-app/src/components/vote/StartGroupVoteModal.js myhive-react-app/src/components/TripBuilder.js
git commit -m "feat(leads): capture quiz-flow leads with consent notice"
```

---

### Task 9: Frontend debounced sync

**Files:**
- Create: `myhive-react-app/src/hooks/useTripLeadSync.js`
- Modify: `myhive-react-app/src/components/Layout.js` (call the hook once, inside the component body)
- Test: `myhive-react-app/src/hooks/useTripLeadSync.test.js`

**Interfaces:**
- Consumes: `useTrip()` state, `leadApi.syncLead`, `readTripLead/clearTripLead`, `readQuizFlow`.
- Produces: `useTripLeadSync()` — no return value; PATCHes the lead 2s after any cart/setup change while `myhive-trip-lead` exists.

- [ ] **Step 1: Write the failing test**

`hooks/useTripLeadSync.test.js`:

```js
import { render, act } from '@testing-library/react';
import { TripProvider, useTrip } from '../context/TripContext';
import { useTripLeadSync } from './useTripLeadSync';
import leadApi from '../services/leadApi';

jest.mock('../services/leadApi');

let tripDispatch;

function Harness() {
    const { dispatch } = useTrip();
    tripDispatch = dispatch;
    useTripLeadSync();
    return null;
}

function renderHarness() {
    return render(
        <TripProvider>
            <Harness />
        </TripProvider>
    );
}

describe('useTripLeadSync', () => {
    beforeEach(() => {
        jest.useFakeTimers();
        localStorage.clear();
        sessionStorage.clear();
        leadApi.syncLead.mockResolvedValue();
    });

    afterEach(() => {
        jest.useRealTimers();
    });

    test('debounces a PATCH with the cart snapshot after a change', async () => {
        localStorage.setItem('myhive-trip-lead', JSON.stringify({ id: 'lead-1', restoreToken: 'tok-1' }));
        renderHarness();

        act(() => {
            tripDispatch({ type: 'ADD_TO_TRIP', silent: true, activity: { id: 'act-1', name: 'Karting', price: 50 } });
        });
        act(() => {
            jest.advanceTimersByTime(2000);
        });

        expect(leadApi.syncLead).toHaveBeenCalledWith('lead-1', expect.objectContaining({
            restoreToken: 'tok-1',
            items: [{ activityId: 'act-1', sortOrder: 0 }],
        }));
    });

    test('does nothing without a stored lead', () => {
        renderHarness();

        act(() => {
            tripDispatch({ type: 'UPDATE_TRIP_TRAVELERS', travelers: 4 });
        });
        act(() => {
            jest.advanceTimersByTime(3000);
        });

        expect(leadApi.syncLead).not.toHaveBeenCalled();
    });

    test('clears the stored lead when the server says it is gone', async () => {
        localStorage.setItem('myhive-trip-lead', JSON.stringify({ id: 'lead-1', restoreToken: 'tok-1' }));
        leadApi.syncLead.mockRejectedValue(new Error('LEAD_GONE'));
        renderHarness();

        act(() => {
            tripDispatch({ type: 'UPDATE_TRIP_TRAVELERS', travelers: 4 });
        });
        await act(async () => {
            jest.advanceTimersByTime(2000);
        });

        expect(localStorage.getItem('myhive-trip-lead')).toBeNull();
    });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd myhive-react-app && npm test -- --watchAll=false useTripLeadSync`
Expected: FAIL (hook module missing).

- [ ] **Step 3: Write the hook and mount it**

`hooks/useTripLeadSync.js`:

```js
import {useEffect} from 'react';
import {useTrip} from '../context/TripContext';
import leadApi from '../services/leadApi';
import {clearTripLead, readTripLead} from '../utils/tripLead';
import {readQuizFlow} from '../utils/quizFlow';

const SYNC_DEBOUNCE_MS = 2000;

/**
 * While this browser holds an active trip lead (myhive-trip-lead), mirrors every
 * cart/setup change to the server (debounced) so the reminder email's restore link
 * always carries the user's latest state — including on other devices.
 */
export function useTripLeadSync() {
    const {state} = useTrip();
    const {tripItems, tripTravelers, tripStartDate, tripEndDate, tripBudget} = state;

    useEffect(() => {
        const lead = readTripLead();
        if (!lead) {
            return undefined;
        }
        const timer = setTimeout(() => {
            const quizFlow = readQuizFlow();
            leadApi.syncLead(lead.id, {
                restoreToken: lead.restoreToken,
                numberOfTravelers: tripTravelers,
                startDate: tripStartDate || null,
                endDate: tripEndDate || null,
                budget: tripBudget,
                quizResponsesJson: quizFlow?.responses ? JSON.stringify(quizFlow.responses) : null,
                items: tripItems.map((item, index) => ({activityId: item.id, sortOrder: index})),
            }).catch(e => {
                if (e.message === 'LEAD_GONE') {
                    clearTripLead();
                }
                // Any other failure is silent — the next change retries.
            });
        }, SYNC_DEBOUNCE_MS);
        return () => clearTimeout(timer);
    }, [tripItems, tripTravelers, tripStartDate, tripEndDate, tripBudget]);
}
```

In `components/Layout.js`: import `{useTripLeadSync} from '../hooks/useTripLeadSync';` and call `useTripLeadSync();` as the first line of the `Layout` component body (Layout renders inside `AppProviders`, so `useTrip` is available).

- [ ] **Step 4: Run test to verify it passes**

Run: `cd myhive-react-app && npm test -- --watchAll=false useTripLeadSync`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/hooks/useTripLeadSync.js myhive-react-app/src/hooks/useTripLeadSync.test.js myhive-react-app/src/components/Layout.js
git commit -m "feat(leads): debounced server sync of the trip lead snapshot"
```

---

### Task 10: Frontend restore (cross-device link)

**Files:**
- Create: `myhive-react-app/src/hooks/useTripLeadRestore.js`
- Modify: `myhive-react-app/src/components/TripBuilder.js` (use the hook + confirm `AppModal`)
- Test: `myhive-react-app/src/hooks/useTripLeadRestore.test.js`

**Interfaces:**
- Consumes: `leadApi.restoreLead(token)`, `useTrip()`, `useCatalog()`, `writeTripLead`, `writeQuizFlow`, `generateUuid` (existing `utils/uuid`).
- Produces: `useTripLeadRestore(onQuizFlowRestored): {pendingRestore, confirmRestore, cancelRestore}` — reads `?restore=` from the URL, applies the cascade (items → quiz flow → setup only), asks before clobbering a non-empty cart.

- [ ] **Step 1: Write the failing test**

`hooks/useTripLeadRestore.test.js`:

```js
import { render, screen, waitFor, act } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { TripProvider, useTrip } from '../context/TripContext';
import { CatalogContext } from '../context/CatalogContext';
import { useTripLeadRestore } from './useTripLeadRestore';
import leadApi from '../services/leadApi';

jest.mock('../services/leadApi');

const catalogValue = {
    state: {
        destinations: [{ id: 'dest-1', slug: 'prague', name: 'Prague' }],
        loading: false,
        error: null,
    },
};

let latestTripState;
let restoreApi;

function Harness({ onQuizFlowRestored }) {
    const { state } = useTrip();
    latestTripState = state;
    restoreApi = useTripLeadRestore(onQuizFlowRestored);
    return <div data-testid="pending">{restoreApi.pendingRestore ? 'pending' : 'idle'}</div>;
}

function renderAt(url, onQuizFlowRestored) {
    return render(
        <CatalogContext.Provider value={catalogValue}>
            <TripProvider>
                <MemoryRouter initialEntries={[url]}>
                    <Routes>
                        <Route path="/destination/:slug" element={<Harness onQuizFlowRestored={onQuizFlowRestored} />} />
                    </Routes>
                </MemoryRouter>
            </TripProvider>
        </CatalogContext.Provider>
    );
}

describe('useTripLeadRestore', () => {
    beforeEach(() => {
        localStorage.clear();
        sessionStorage.clear();
    });

    test('restores cart items into the trip when local cart is empty', async () => {
        leadApi.restoreLead.mockResolvedValue({
            leadId: 'lead-1',
            email: 'a@b.com',
            destinationId: 'dest-1',
            destinationSlug: 'prague',
            numberOfTravelers: 6,
            startDate: '2026-09-01',
            endDate: '2026-09-03',
            budget: null,
            quizResponsesJson: null,
            items: [{ activityId: 'act-1', name: 'Karting', price: 50, minPrice: null,
                      imageUrl: 'img', duration: 60, slug: 'karting', destinationSlug: 'prague',
                      description: '', includes: '' }],
        });

        renderAt('/destination/prague?tab=trip-builder&restore=tok-1');

        await waitFor(() => expect(latestTripState.tripItems).toHaveLength(1));
        expect(latestTripState.tripItems[0]).toEqual(expect.objectContaining({ id: 'act-1', name: 'Karting' }));
        expect(latestTripState.tripTravelers).toBe(6);
        expect(JSON.parse(localStorage.getItem('myhive-trip-lead'))).toEqual(
            { id: 'lead-1', restoreToken: 'tok-1' });
    });

    test('rebuilds the quiz flow when there are no items but quiz answers exist', async () => {
        const onQuizFlowRestored = jest.fn();
        leadApi.restoreLead.mockResolvedValue({
            leadId: 'lead-2',
            email: 'a@b.com',
            destinationId: 'dest-1',
            destinationSlug: 'prague',
            numberOfTravelers: 4,
            startDate: null,
            endDate: null,
            budget: null,
            quizResponsesJson: '[{"questionId":"q1","answerId":"a1"}]',
            items: [],
        });

        renderAt('/destination/prague?tab=trip-builder&restore=tok-2', onQuizFlowRestored);

        await waitFor(() => expect(onQuizFlowRestored).toHaveBeenCalled());
        const flow = JSON.parse(sessionStorage.getItem('myhive-quiz-flow'));
        expect(flow.responses).toEqual([{ questionId: 'q1', answerId: 'a1' }]);
        expect(flow.setup.destination.id).toBe('dest-1');
        expect(flow.setup.email).toBe('a@b.com');
    });

    test('asks before replacing a non-empty local cart', async () => {
        localStorage.setItem('myhive-trip-items', JSON.stringify([{ id: 'local-1', name: 'Local', price: 10 }]));
        leadApi.restoreLead.mockResolvedValue({
            leadId: 'lead-3', email: 'a@b.com', destinationId: 'dest-1', destinationSlug: 'prague',
            numberOfTravelers: 2, startDate: null, endDate: null, budget: null,
            quizResponsesJson: null,
            items: [{ activityId: 'act-9', name: 'Boat', price: 80, minPrice: null, imageUrl: '',
                      duration: 90, slug: 'boat', destinationSlug: 'prague', description: '', includes: '' }],
        });

        renderAt('/destination/prague?tab=trip-builder&restore=tok-3');

        await waitFor(() => expect(screen.getByTestId('pending')).toHaveTextContent('pending'));
        expect(latestTripState.tripItems[0].id).toBe('local-1');

        act(() => restoreApi.confirmRestore());
        await waitFor(() => expect(latestTripState.tripItems[0].id).toBe('act-9'));
    });

    test('an unknown token is ignored silently', async () => {
        leadApi.restoreLead.mockRejectedValue(new Error('Failed to restore trip'));

        renderAt('/destination/prague?tab=trip-builder&restore=bad');

        await waitFor(() => expect(leadApi.restoreLead).toHaveBeenCalled());
        expect(latestTripState.tripItems).toHaveLength(0);
    });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd myhive-react-app && npm test -- --watchAll=false useTripLeadRestore`
Expected: FAIL (hook missing).

- [ ] **Step 3: Write the hook**

`hooks/useTripLeadRestore.js`:

```js
import {useEffect, useRef, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import {useCatalog} from '../context/CatalogContext';
import leadApi from '../services/leadApi';
import {writeTripLead} from '../utils/tripLead';
import {writeQuizFlow} from '../utils/quizFlow';
import {generateUuid} from '../utils/uuid';

/**
 * Handles the reminder email's cross-device restore link (?restore=<token>).
 * Cascade: items -> rebuild cart; no items but quiz answers -> rebuild the quiz flow
 * (recommendations reappear); neither -> just travelers/dates. A non-empty local cart
 * is never clobbered without confirmation (pendingRestore + confirm/cancel).
 */
export function useTripLeadRestore(onQuizFlowRestored) {
    const [searchParams, setSearchParams] = useSearchParams();
    const {state, dispatch} = useTrip();
    const {state: catalog} = useCatalog();
    const [pendingRestore, setPendingRestore] = useState(null);
    const restoreToken = searchParams.get('restore');
    // Mount-time cart emptiness decides whether to ask — reading live state in the
    // fetch callback would still be the pre-restore value, but a ref is explicit.
    const hasLocalCartRef = useRef(state.tripItems.length > 0);

    const stripParam = () => {
        setSearchParams(params => {
            params.delete('restore');
            return params;
        }, {replace: true});
    };

    const apply = (data) => {
        writeTripLead({id: data.leadId, restoreToken});
        if (data.items && data.items.length > 0) {
            dispatch({
                type: 'SET_TRIP_ITEMS',
                tripItems: data.items.map(item => ({
                    id: item.activityId,
                    name: item.name,
                    price: item.price,
                    minPrice: item.minPrice,
                    imageUrl: item.imageUrl,
                    duration: item.duration,
                    slug: item.slug,
                    destinationSlug: item.destinationSlug,
                    description: item.description,
                    includes: item.includes,
                })),
            });
        }
        if (data.numberOfTravelers) {
            dispatch({type: 'UPDATE_TRIP_TRAVELERS', travelers: data.numberOfTravelers});
        }
        if (data.startDate || data.endDate) {
            dispatch({type: 'UPDATE_TRIP_DATES', startDate: data.startDate ?? '', endDate: data.endDate ?? ''});
        }
        dispatch({type: 'UPDATE_TRIP_BUDGET', budget: data.budget ?? null});
        dispatch({type: 'SET_TRIP_ID', tripId: generateUuid()});
        if ((!data.items || data.items.length === 0) && data.quizResponsesJson) {
            const destination = catalog.destinations.find(d => d.id === data.destinationId) || null;
            if (destination) {
                try {
                    const responses = JSON.parse(data.quizResponsesJson);
                    const flow = {
                        setup: {
                            travelers: data.numberOfTravelers || 1,
                            startDate: data.startDate || '',
                            endDate: data.endDate || '',
                            email: data.email,
                            destination,
                            budget: data.budget ?? null,
                        },
                        responses,
                    };
                    writeQuizFlow(flow);
                    if (onQuizFlowRestored) {
                        onQuizFlowRestored(flow);
                    }
                } catch (e) {
                    // Malformed stored answers — the plain builder still restores setup fields.
                }
            }
        }
        stripParam();
    };

    useEffect(() => {
        if (!restoreToken || catalog.loading) {
            return undefined;
        }
        let cancelled = false;
        leadApi.restoreLead(restoreToken)
            .then(data => {
                if (cancelled) {
                    return;
                }
                if (hasLocalCartRef.current && data.items && data.items.length > 0) {
                    setPendingRestore(data);
                } else {
                    apply(data);
                }
            })
            .catch(() => {
                if (!cancelled) {
                    stripParam(); // dead/unknown token — open the builder normally
                }
            });
        return () => {
            cancelled = true;
        };
        // apply/stripParam are stable within a render pass; re-running on their identity
        // would refetch on every render.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [restoreToken, catalog.loading]);

    return {
        pendingRestore,
        confirmRestore: () => {
            apply(pendingRestore);
            setPendingRestore(null);
        },
        cancelRestore: () => {
            setPendingRestore(null);
            stripParam();
        },
    };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd myhive-react-app && npm test -- --watchAll=false useTripLeadRestore`
Expected: PASS (4 tests).

- [ ] **Step 5: Integrate into TripBuilder**

In `TripBuilder.js`:

1. Import: `import {useTripLeadRestore} from '../hooks/useTripLeadRestore';` (and `clearTripLead` is already imported from Task 8).
2. In the component body (near the other hook state, after `quizFlow` state ~line 61):

```js
  const {pendingRestore, confirmRestore, cancelRestore} = useTripLeadRestore(flow => setQuizFlow(flow));
```

3. In the JSX, next to the existing `StartGroupVoteModal` render (~line 945), add:

```jsx
      <AppModal
        isOpen={pendingRestore != null}
        onClose={cancelRestore}
        title="Replace your current trip?"
        footer={(
          <>
            <button type="button" className="btn btn--secondary" onClick={cancelRestore}>Keep current</button>
            <button type="button" className="btn btn--primary" onClick={confirmRestore}>Open saved trip</button>
          </>
        )}
      >
        <p>Opening your saved trip will replace the activities currently in your itinerary.</p>
      </AppModal>
```

(`AppModal` is already imported in TripBuilder; if not, add `import AppModal from './AppModal';`.)

- [ ] **Step 6: Run the wider frontend suite**

Run: `cd myhive-react-app && npm test -- --watchAll=false`
Expected: PASS (TripBuilder tests still green — the hook is inert without a `?restore=` param).

- [ ] **Step 7: Commit**

```bash
git add myhive-react-app/src/hooks/useTripLeadRestore.js myhive-react-app/src/hooks/useTripLeadRestore.test.js myhive-react-app/src/components/TripBuilder.js
git commit -m "feat(leads): cross-device trip restore from the reminder link"
```

---

### Task 11: Unsubscribe page

**Files:**
- Create: `myhive-react-app/src/pages/UnsubscribePage.js`
- Modify: `myhive-react-app/src/components/Layout.js` (add route)
- Test: `myhive-react-app/src/pages/UnsubscribePage.test.js`

**Interfaces:**
- Consumes: `leadApi.unsubscribe(token)`.
- Produces: route `/unsubscribe?token=...` — confirm-button page (GET must not change state; mail scanners prefetch links).

- [ ] **Step 1: Write the failing test**

`pages/UnsubscribePage.test.js`:

```js
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import UnsubscribePage from './UnsubscribePage';
import leadApi from '../services/leadApi';

jest.mock('../services/leadApi');

function renderAt(url) {
    return render(
        <MemoryRouter initialEntries={[url]}>
            <UnsubscribePage />
        </MemoryRouter>
    );
}

describe('UnsubscribePage', () => {
    test('unsubscribes on confirm click', async () => {
        leadApi.unsubscribe.mockResolvedValue();
        renderAt('/unsubscribe?token=tok-1');

        await userEvent.click(screen.getByRole('button', { name: /unsubscribe/i }));

        await waitFor(() => expect(leadApi.unsubscribe).toHaveBeenCalledWith('tok-1'));
        expect(screen.getByText(/you're unsubscribed/i)).toBeInTheDocument();
    });

    test('button is disabled without a token', () => {
        renderAt('/unsubscribe');

        expect(screen.getByRole('button', { name: /unsubscribe/i })).toBeDisabled();
    });

    test('shows an error when the request fails', async () => {
        leadApi.unsubscribe.mockRejectedValue(new Error('down'));
        renderAt('/unsubscribe?token=tok-1');

        await userEvent.click(screen.getByRole('button', { name: /unsubscribe/i }));

        expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();
    });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd myhive-react-app && npm test -- --watchAll=false UnsubscribePage`
Expected: FAIL (page missing).

- [ ] **Step 3: Write the page and the route**

`pages/UnsubscribePage.js`:

```js
import {useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import leadApi from '../services/leadApi';

// Deliberately a confirm-button page: mail scanners prefetch GET links, so the
// link itself must not unsubscribe anyone. The POST happens on click.
function UnsubscribePage() {
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');
    const [status, setStatus] = useState('idle');

    const handleUnsubscribe = async () => {
        setStatus('working');
        try {
            await leadApi.unsubscribe(token);
            setStatus('done');
        } catch (e) {
            setStatus('error');
        }
    };

    return (
        <div className="container" style={{maxWidth: 560, margin: '60px auto', textAlign: 'center'}}>
            {status === 'done' ? (
                <>
                    <h1>You&apos;re unsubscribed</h1>
                    <p>We won&apos;t send you any more trip reminders.</p>
                </>
            ) : (
                <>
                    <h1>Unsubscribe from trip reminders</h1>
                    <p>
                        You&apos;ll stop receiving reminder emails about your saved trip.
                        Booking and vote confirmations are not affected.
                    </p>
                    {status === 'error' && <p style={{color: '#c0392b'}}>Something went wrong. Please try again.</p>}
                    <button
                        type="button"
                        className="btn btn--primary"
                        onClick={handleUnsubscribe}
                        disabled={!token || status === 'working'}
                    >
                        {status === 'working' ? 'Unsubscribing…' : 'Unsubscribe'}
                    </button>
                </>
            )}
        </div>
    );
}

export default UnsubscribePage;
```

In `components/Layout.js`: import the page and add next to the other static routes (~line 48):

```jsx
            <Route path="/unsubscribe" element={<UnsubscribePage/>}/>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd myhive-react-app && npm test -- --watchAll=false UnsubscribePage`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/pages/UnsubscribePage.js myhive-react-app/src/pages/UnsubscribePage.test.js myhive-react-app/src/components/Layout.js
git commit -m "feat(leads): unsubscribe confirmation page"
```

---

### Task 12: Full verification + docs

**Files:**
- Modify: `README.md` (if it documents endpoints/features), `CLAUDE.md` (add a Key Architectural Patterns bullet for trip-lead reminders)

- [ ] **Step 1: Full test sweep**

Run: `cd myhive-backend && ./gradlew test` then `cd myhive-react-app && npm test -- --watchAll=false`
Expected: both PASS. Fix anything broken before proceeding.

- [ ] **Step 2: Manual smoke (dev)**

Backend `./gradlew bootRun --args='--spring.profiles.active=dev'`, frontend `npm start`:
1. Start Group Vote → enter email in the setup modal → consent note visible → check `POST /leads` fired (network tab) and `myhive-trip-lead` in localStorage.
2. Add cart items in Trip Builder → after ~2s a `PATCH /leads/{id}` fires.
3. Copy the restore link `http://localhost:3000/destination/prague?tab=trip-builder&restore=<token>` into an incognito window → cart restores.
4. `/unsubscribe?token=<unsubscribeToken>` → confirm → second reminder pass skips the lead (email disabled in dev — verify via logs/H2 console that status becomes UNSUBSCRIBED on the next scheduler tick, or call the service from a test).

- [ ] **Step 3: Update CLAUDE.md**

Add one bullet under Key Architectural Patterns summarizing: TripLead capture points, cadence (QUIZ 1h/24h/72h, VOTE 24h/72h from vote completion), stop conditions, restore link, suppression, `REMINDERS_ENABLED` kill switch, `API_PUBLIC_URL` for one-click unsubscribe headers, 30-day retention.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs: trip lead reminder architecture notes"
```

---

## Rollout notes (after merge — operator steps)

1. Deploy backend + frontend in any order (additive; scheduler no-ops until leads exist; `ddl-auto=update` creates the tables).
2. Render env for the backend service: optionally set `API_PUBLIC_URL` to the public API base (enables RFC 8058 one-click headers); `REMINDERS_ENABLED` defaults to true — set `false` to pause the series.
3. Watch Resend deliverability + unsubscribe rate for the first week; revisit cadence on spam complaints.
