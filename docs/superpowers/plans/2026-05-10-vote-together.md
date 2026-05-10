# Vote Together & Build a Trip — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a group voting feature where an initiator swipes categories + activities, shares a link, friends vote, and after 24 h the best-fitting activities are emailed back as a ready-to-book trip.

**Architecture:** New backend entities (`VoteSession`, `VoteActivityLike`, `VoteSessionResultActivity`) with a public `/vote/**` REST API, a `@Scheduled` greedy-fill job, and a Resend/Thymeleaf email. Frontend uses `react-tinder-card` for swipe UI across four new pages; existing `TripBuilderDropdown`, `TripSetupModal`, and `TripBuilder` get small targeted changes.

**Tech Stack:** Spring Boot 4 / Java 25 / JPA (backend), React 19 / react-tinder-card (frontend), Resend SMTP + Thymeleaf (email), PostgreSQL (prod) / H2 (dev/test).

**Spec:** `docs/superpowers/specs/2026-05-10-vote-together-design.md`

---

## Task 1: Backend — Exception, Enum, Entities, Repositories

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/exception/SessionFullException.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/model/VoteSessionStatus.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/VoteSession.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/VoteActivityLike.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/VoteSessionResultActivity.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/repository/VoteSessionRepository.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/repository/VoteActivityLikeRepository.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/repository/VoteSessionResultActivityRepository.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create SessionFullException**

```java
// myhive-backend/src/main/java/com/myhive/backend/exception/SessionFullException.java
package com.myhive.backend.exception;

public class SessionFullException extends RuntimeException {
    public SessionFullException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Register SessionFullException → 403 in GlobalExceptionHandler**

Add this handler method to `GlobalExceptionHandler.java` after the `handleBadRequest` method:

```java
@ExceptionHandler(SessionFullException.class)
public ResponseEntity<ErrorResponse> handleSessionFull(SessionFullException ex, HttpServletRequest request) {
    ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error("Session Full")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
}
```

- [ ] **Step 3: Create VoteSessionStatus enum**

```java
// myhive-backend/src/main/java/com/myhive/backend/model/VoteSessionStatus.java
package com.myhive.backend.model;

public enum VoteSessionStatus {
    ACTIVE, COMPLETED
}
```

- [ ] **Step 4: Create VoteSession entity**

```java
// myhive-backend/src/main/java/com/myhive/backend/entity/VoteSession.java
package com.myhive.backend.entity;

import com.myhive.backend.model.VoteSessionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "vote_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"destination", "likedCategories"})
public class VoteSession {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "share_token", unique = true, nullable = false, updatable = false)
    private UUID shareToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id", nullable = false)
    private Destination destination;

    @Column(name = "initiator_email", nullable = false)
    private String initiatorEmail;

    @Column(name = "number_of_travelers", nullable = false)
    private Integer numberOfTravelers;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private VoteSessionStatus status;

    @Column(name = "max_participants", nullable = false)
    private Integer maxParticipants = 50;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "vote_session_liked_categories",
            joinColumns = @JoinColumn(name = "session_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> likedCategories = new HashSet<>();
}
```

- [ ] **Step 5: Create VoteActivityLike entity**

```java
// myhive-backend/src/main/java/com/myhive/backend/entity/VoteActivityLike.java
package com.myhive.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "vote_activity_likes",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"session_id", "voter_token", "activity_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class VoteActivityLike {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private VoteSession session;

    @Column(name = "voter_token", nullable = false)
    private UUID voterToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @Column(nullable = false)
    private Boolean liked;
}
```

- [ ] **Step 6: Create VoteSessionResultActivity entity**

```java
// myhive-backend/src/main/java/com/myhive/backend/entity/VoteSessionResultActivity.java
package com.myhive.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "vote_session_result_activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class VoteSessionResultActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private VoteSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
```

- [ ] **Step 7: Create VoteSessionRepository**

```java
// myhive-backend/src/main/java/com/myhive/backend/repository/VoteSessionRepository.java
package com.myhive.backend.repository;

import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.model.VoteSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoteSessionRepository extends JpaRepository<VoteSession, UUID> {

    Optional<VoteSession> findByShareToken(UUID shareToken);

    List<VoteSession> findByStatusAndExpiresAtBefore(VoteSessionStatus status, LocalDateTime time);

    @Modifying
    @Query("DELETE FROM VoteSession s WHERE s.status = :status AND s.expiresAt < :cutoff")
    int deleteByStatusAndExpiresAtBefore(
            @Param("status") VoteSessionStatus status,
            @Param("cutoff") LocalDateTime cutoff);
}
```

- [ ] **Step 8: Create VoteActivityLikeRepository**

```java
// myhive-backend/src/main/java/com/myhive/backend/repository/VoteActivityLikeRepository.java
package com.myhive.backend.repository;

import com.myhive.backend.entity.VoteActivityLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoteActivityLikeRepository extends JpaRepository<VoteActivityLike, UUID> {

    Optional<VoteActivityLike> findBySessionIdAndVoterTokenAndActivityId(
            UUID sessionId, UUID voterToken, UUID activityId);

    boolean existsBySessionIdAndVoterToken(UUID sessionId, UUID voterToken);

    @Query("SELECT COUNT(DISTINCT l.voterToken) FROM VoteActivityLike l WHERE l.session.id = :sessionId")
    long countDistinctVoterTokensBySessionId(@Param("sessionId") UUID sessionId);

    @Query("""
            SELECT l.activity.id, l.activity.duration, COUNT(l) AS likes
            FROM VoteActivityLike l
            WHERE l.session.id = :sessionId AND l.liked = true
            GROUP BY l.activity.id, l.activity.duration
            ORDER BY likes DESC
            """)
    List<Object[]> findLikedActivitiesWithCounts(@Param("sessionId") UUID sessionId);
}
```

- [ ] **Step 9: Create VoteSessionResultActivityRepository**

```java
// myhive-backend/src/main/java/com/myhive/backend/repository/VoteSessionResultActivityRepository.java
package com.myhive.backend.repository;

import com.myhive.backend.entity.VoteSessionResultActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VoteSessionResultActivityRepository extends JpaRepository<VoteSessionResultActivity, UUID> {

    List<VoteSessionResultActivity> findBySessionIdOrderBySortOrder(UUID sessionId);
}
```

- [ ] **Step 10: Verify app starts**

```bash
cd myhive-backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Expected: app starts on :8080, H2 creates the four new tables (`vote_sessions`, `vote_session_liked_categories`, `vote_activity_likes`, `vote_session_result_activities`). Check the console logs — Hibernate DDL should list the table creation.

- [ ] **Step 11: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/exception/SessionFullException.java \
        myhive-backend/src/main/java/com/myhive/backend/exception/GlobalExceptionHandler.java \
        myhive-backend/src/main/java/com/myhive/backend/model/VoteSessionStatus.java \
        myhive-backend/src/main/java/com/myhive/backend/entity/VoteSession.java \
        myhive-backend/src/main/java/com/myhive/backend/entity/VoteActivityLike.java \
        myhive-backend/src/main/java/com/myhive/backend/entity/VoteSessionResultActivity.java \
        myhive-backend/src/main/java/com/myhive/backend/repository/VoteSessionRepository.java \
        myhive-backend/src/main/java/com/myhive/backend/repository/VoteActivityLikeRepository.java \
        myhive-backend/src/main/java/com/myhive/backend/repository/VoteSessionResultActivityRepository.java
git commit -m "feat: add VoteSession entities and repositories"
```

---

## Task 2: Backend — DTOs + VoteSessionService

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/VoteSessionCreateRequest.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/VoteSessionResponse.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/VoteActivityResponse.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/VoteRequest.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/VoteResultResponse.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java`
- Create: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionServiceTest.java`

- [ ] **Step 1: Create DTOs**

```java
// VoteSessionCreateRequest.java
package com.myhive.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class VoteSessionCreateRequest {
    @NotNull private UUID destinationId;
    @NotNull @Email private String initiatorEmail;
    @NotNull @Min(1) private Integer numberOfTravelers;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    @NotEmpty private List<UUID> likedCategoryIds;
}
```

```java
// VoteSessionResponse.java
package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VoteSessionResponse {
    private UUID shareToken;
    private String destinationName;
    private String destinationSlug;
    private String status;
    private LocalDateTime expiresAt;
    private long participantCount;
}
```

```java
// VoteActivityResponse.java
package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VoteActivityResponse {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer duration;
    private String imageUrl;
    private String slug;
}
```

```java
// VoteRequest.java
package com.myhive.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class VoteRequest {
    @NotNull private UUID voterToken;
    @NotNull private UUID activityId;
    @NotNull private Boolean liked;
}
```

```java
// VoteResultResponse.java
package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class VoteResultResponse {
    private String destinationName;
    private String destinationSlug;
    private List<VoteActivityResponse> activities;
    private BigDecimal totalPrice;
    private Integer numberOfTravelers;
    private LocalDate startDate;
    private LocalDate endDate;
}
```

- [ ] **Step 2: Write failing tests**

```java
// myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionServiceTest.java
package com.myhive.backend.service;

import com.myhive.backend.dto.VoteRequest;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteActivityLike;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.exception.SessionFullException;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.repository.VoteSessionResultActivityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoteSessionServiceTest {

    @Mock private VoteSessionRepository voteSessionRepository;
    @Mock private VoteActivityLikeRepository voteActivityLikeRepository;
    @Mock private VoteSessionResultActivityRepository resultActivityRepository;
    @Mock private DestinationRepository destinationRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ActivityRepository activityRepository;

    @InjectMocks
    private VoteSessionService voteSessionService;

    @Test
    void createSession_savesSessionAndReturnsShareToken() {
        UUID destId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();

        Category category = new Category();
        category.setId(catId);

        Destination destination = new Destination();
        destination.setId(destId);
        destination.setName("Bali");
        destination.setSlug("bali");
        destination.setCategories(Set.of(category));

        when(destinationRepository.findById(destId)).thenReturn(Optional.of(destination));
        when(categoryRepository.findById(catId)).thenReturn(Optional.of(category));
        when(voteSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(voteActivityLikeRepository.countDistinctVoterTokensBySessionId(any())).thenReturn(0L);

        VoteSessionCreateRequest request = new VoteSessionCreateRequest();
        request.setDestinationId(destId);
        request.setInitiatorEmail("alice@example.com");
        request.setNumberOfTravelers(3);
        request.setStartDate(LocalDate.of(2026, 7, 1));
        request.setEndDate(LocalDate.of(2026, 7, 7));
        request.setLikedCategoryIds(List.of(catId));

        VoteSessionResponse response = voteSessionService.createSession(request);

        ArgumentCaptor<VoteSession> captor = ArgumentCaptor.forClass(VoteSession.class);
        verify(voteSessionRepository).save(captor.capture());
        VoteSession saved = captor.getValue();

        assertThat(saved.getInitiatorEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getStatus()).isEqualTo(VoteSessionStatus.ACTIVE);
        assertThat(saved.getShareToken()).isNotNull();
        assertThat(saved.getMaxParticipants()).isEqualTo(50);
        assertThat(response.getShareToken()).isEqualTo(saved.getShareToken());
    }

    @Test
    void createSession_rejectsCategoryNotBelongingToDestination() {
        UUID destId = UUID.randomUUID();
        UUID foreignCatId = UUID.randomUUID();

        Destination destination = new Destination();
        destination.setId(destId);
        destination.setCategories(Set.of()); // no categories

        when(destinationRepository.findById(destId)).thenReturn(Optional.of(destination));

        VoteSessionCreateRequest request = new VoteSessionCreateRequest();
        request.setDestinationId(destId);
        request.setInitiatorEmail("alice@example.com");
        request.setNumberOfTravelers(1);
        request.setStartDate(LocalDate.of(2026, 7, 1));
        request.setEndDate(LocalDate.of(2026, 7, 3));
        request.setLikedCategoryIds(List.of(foreignCatId));

        assertThatThrownBy(() -> voteSessionService.createSession(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void castVote_throwsSessionFullForNewVoterAtLimit() {
        UUID shareToken = UUID.randomUUID();
        UUID voterToken = UUID.randomUUID();

        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setMaxParticipants(50);

        when(voteSessionRepository.findByShareToken(shareToken)).thenReturn(Optional.of(session));
        when(voteActivityLikeRepository.existsBySessionIdAndVoterToken(any(), any())).thenReturn(false);
        when(voteActivityLikeRepository.countDistinctVoterTokensBySessionId(any())).thenReturn(50L);

        VoteRequest request = new VoteRequest();
        request.setVoterToken(voterToken);
        request.setActivityId(UUID.randomUUID());
        request.setLiked(true);

        assertThatThrownBy(() -> voteSessionService.castVote(shareToken, request))
                .isInstanceOf(SessionFullException.class);
    }

    @Test
    void castVote_existingVoterCanUpdateVoteEvenWhenFull() {
        UUID shareToken = UUID.randomUUID();
        UUID voterToken = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setMaxParticipants(50);

        Activity activity = new Activity();
        activity.setId(activityId);

        VoteActivityLike existing = new VoteActivityLike();
        existing.setLiked(false);

        when(voteSessionRepository.findByShareToken(shareToken)).thenReturn(Optional.of(session));
        when(voteActivityLikeRepository.existsBySessionIdAndVoterToken(any(), eq(voterToken))).thenReturn(true);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(voteActivityLikeRepository.findBySessionIdAndVoterTokenAndActivityId(any(), any(), any()))
                .thenReturn(Optional.of(existing));
        when(voteActivityLikeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        VoteRequest request = new VoteRequest();
        request.setVoterToken(voterToken);
        request.setActivityId(activityId);
        request.setLiked(true);

        voteSessionService.castVote(shareToken, request);

        assertThat(existing.getLiked()).isTrue();
    }

    @Test
    void getResult_throwsNotFoundWhenActive() {
        UUID shareToken = UUID.randomUUID();
        VoteSession session = new VoteSession();
        session.setStatus(VoteSessionStatus.ACTIVE);

        when(voteSessionRepository.findByShareToken(shareToken)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> voteSessionService.getResult(shareToken))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 3: Run tests to confirm they fail**

```bash
cd myhive-backend
./gradlew test --tests '*VoteSessionServiceTest'
```

Expected: compilation error — `VoteSessionService` does not exist yet.

- [ ] **Step 4: Implement VoteSessionService**

```java
// myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java
package com.myhive.backend.service;

import com.myhive.backend.dto.VoteActivityResponse;
import com.myhive.backend.dto.VoteRequest;
import com.myhive.backend.dto.VoteResultResponse;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteActivityLike;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.exception.SessionFullException;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.repository.VoteSessionResultActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VoteSessionService {

    private final VoteSessionRepository voteSessionRepository;
    private final VoteActivityLikeRepository voteActivityLikeRepository;
    private final VoteSessionResultActivityRepository resultActivityRepository;
    private final DestinationRepository destinationRepository;
    private final CategoryRepository categoryRepository;
    private final ActivityRepository activityRepository;

    @Transactional
    public VoteSessionResponse createSession(VoteSessionCreateRequest request) {
        Destination destination = destinationRepository.findById(request.getDestinationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found"));

        Set<UUID> destinationCategoryIds = destination.getCategories().stream()
                .map(Category::getId)
                .collect(Collectors.toSet());

        boolean allValid = request.getLikedCategoryIds().stream()
                .allMatch(destinationCategoryIds::contains);
        if (!allValid) {
            throw new BadRequestException("Some categories do not belong to this destination");
        }

        Set<Category> likedCategories = request.getLikedCategoryIds().stream()
                .map(id -> categoryRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id)))
                .collect(Collectors.toSet());

        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail(request.getInitiatorEmail());
        session.setNumberOfTravelers(request.getNumberOfTravelers());
        session.setStartDate(request.getStartDate());
        session.setEndDate(request.getEndDate());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setMaxParticipants(50);
        session.setExpiresAt(LocalDateTime.now().plusHours(24));
        session.setLikedCategories(likedCategories);

        session = voteSessionRepository.save(session);
        return toResponse(session, 0L);
    }

    @Transactional(readOnly = true)
    public VoteSessionResponse getSession(UUID shareToken) {
        VoteSession session = findByShareToken(shareToken);
        long count = voteActivityLikeRepository.countDistinctVoterTokensBySessionId(session.getId());
        return toResponse(session, count);
    }

    @Transactional(readOnly = true)
    public List<VoteActivityResponse> getActivities(UUID shareToken) {
        VoteSession session = findByShareToken(shareToken);
        Set<UUID> categoryIds = session.getLikedCategories().stream()
                .map(Category::getId)
                .collect(Collectors.toSet());

        List<Activity> activities = activityRepository.findByDestinationIdAndCategoriesIdIn(
                session.getDestination().getId(), categoryIds);
        return activities.stream().map(this::toActivityResponse).toList();
    }

    @Transactional
    public void castVote(UUID shareToken, VoteRequest request) {
        VoteSession session = findByShareToken(shareToken);

        if (session.getStatus() != VoteSessionStatus.ACTIVE) {
            throw new BadRequestException("Session is no longer active");
        }

        boolean isNewVoter = !voteActivityLikeRepository
                .existsBySessionIdAndVoterToken(session.getId(), request.getVoterToken());

        if (isNewVoter) {
            long voterCount = voteActivityLikeRepository
                    .countDistinctVoterTokensBySessionId(session.getId());
            if (voterCount >= session.getMaxParticipants()) {
                throw new SessionFullException("Session has reached the maximum number of participants");
            }
        }

        Activity activity = activityRepository.findById(request.getActivityId())
                .orElseThrow(() -> new ResourceNotFoundException("Activity not found"));

        VoteActivityLike like = voteActivityLikeRepository
                .findBySessionIdAndVoterTokenAndActivityId(
                        session.getId(), request.getVoterToken(), request.getActivityId())
                .orElse(new VoteActivityLike());

        like.setSession(session);
        like.setVoterToken(request.getVoterToken());
        like.setActivity(activity);
        like.setLiked(request.getLiked());
        voteActivityLikeRepository.save(like);
    }

    @Transactional(readOnly = true)
    public long getParticipantCount(UUID shareToken) {
        VoteSession session = findByShareToken(shareToken);
        return voteActivityLikeRepository.countDistinctVoterTokensBySessionId(session.getId());
    }

    @Transactional(readOnly = true)
    public VoteResultResponse getResult(UUID shareToken) {
        VoteSession session = findByShareToken(shareToken);
        if (session.getStatus() != VoteSessionStatus.COMPLETED) {
            throw new ResourceNotFoundException("Result not available yet");
        }

        List<VoteSessionResultActivity> results = resultActivityRepository
                .findBySessionIdOrderBySortOrder(session.getId());

        List<VoteActivityResponse> activities = results.stream()
                .map(r -> toActivityResponse(r.getActivity()))
                .toList();

        BigDecimal totalPrice = activities.stream()
                .map(VoteActivityResponse::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(session.getNumberOfTravelers()));

        return new VoteResultResponse(
                session.getDestination().getName(),
                session.getDestination().getSlug(),
                activities,
                totalPrice,
                session.getNumberOfTravelers(),
                session.getStartDate(),
                session.getEndDate());
    }

    private VoteSession findByShareToken(UUID shareToken) {
        return voteSessionRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new ResourceNotFoundException("Vote session not found"));
    }

    private VoteSessionResponse toResponse(VoteSession session, long participantCount) {
        return new VoteSessionResponse(
                session.getShareToken(),
                session.getDestination().getName(),
                session.getDestination().getSlug(),
                session.getStatus().name(),
                session.getExpiresAt(),
                participantCount);
    }

    private VoteActivityResponse toActivityResponse(Activity activity) {
        return new VoteActivityResponse(
                activity.getId(),
                activity.getName(),
                activity.getDescription(),
                activity.getPrice(),
                activity.getDuration(),
                activity.getImageUrl(),
                activity.getSlug());
    }
}
```

- [ ] **Step 5: Add `findByDestinationIdAndCategoriesIdIn` to ActivityRepository**

Add this method to `myhive-backend/src/main/java/com/myhive/backend/repository/ActivityRepository.java`:

```java
@Query("SELECT DISTINCT a FROM Activity a JOIN a.categories c WHERE a.destination.id = :destinationId AND c.id IN :categoryIds")
List<Activity> findByDestinationIdAndCategoriesIdIn(
        @Param("destinationId") UUID destinationId,
        @Param("categoryIds") Set<UUID> categoryIds);
```

Add the necessary imports at the top:
```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Set;
```

- [ ] **Step 6: Run tests**

```bash
cd myhive-backend
./gradlew test --tests '*VoteSessionServiceTest'
```

Expected: all 4 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/VoteSessionCreateRequest.java \
        myhive-backend/src/main/java/com/myhive/backend/dto/VoteSessionResponse.java \
        myhive-backend/src/main/java/com/myhive/backend/dto/VoteActivityResponse.java \
        myhive-backend/src/main/java/com/myhive/backend/dto/VoteRequest.java \
        myhive-backend/src/main/java/com/myhive/backend/dto/VoteResultResponse.java \
        myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java \
        myhive-backend/src/main/java/com/myhive/backend/repository/ActivityRepository.java \
        myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionServiceTest.java
git commit -m "feat: add VoteSessionService with create, vote, and result logic"
```

---

## Task 3: Backend — VoteSessionController + SecurityConfig

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/controller/VoteSessionController.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/config/SecurityConfig.java`
- Create: `myhive-backend/src/test/java/com/myhive/backend/controller/VoteSessionControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

```java
// myhive-backend/src/test/java/com/myhive/backend/controller/VoteSessionControllerTest.java
package com.myhive.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.myhive.backend.dto.VoteActivityResponse;
import com.myhive.backend.dto.VoteResultResponse;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.exception.SessionFullException;
import com.myhive.backend.service.VoteSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VoteSessionController.class)
class VoteSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VoteSessionService voteSessionService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void createSession_returns201WithShareToken() throws Exception {
        UUID expectedToken = UUID.randomUUID();
        VoteSessionResponse response = new VoteSessionResponse(
                expectedToken, "Bali", "bali", "ACTIVE",
                LocalDateTime.now().plusHours(24), 0L);

        when(voteSessionService.createSession(any())).thenReturn(response);

        VoteSessionCreateRequest request = new VoteSessionCreateRequest();
        request.setDestinationId(UUID.randomUUID());
        request.setInitiatorEmail("alice@example.com");
        request.setNumberOfTravelers(2);
        request.setStartDate(LocalDate.of(2026, 7, 1));
        request.setEndDate(LocalDate.of(2026, 7, 7));
        request.setLikedCategoryIds(List.of(UUID.randomUUID()));

        mockMvc.perform(post("/vote/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shareToken").value(expectedToken.toString()));
    }

    @Test
    void getResult_returns404WhenActive() throws Exception {
        UUID shareToken = UUID.randomUUID();
        when(voteSessionService.getResult(shareToken))
                .thenThrow(new ResourceNotFoundException("Result not available yet"));

        mockMvc.perform(get("/vote/sessions/{shareToken}/result", shareToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void castVote_returns403WhenSessionFull() throws Exception {
        UUID shareToken = UUID.randomUUID();
        when(voteSessionService.castVote(any(), any()))
                .thenThrow(new SessionFullException("Session is full"));

        mockMvc.perform(post("/vote/sessions/{shareToken}/votes", shareToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voterToken\":\"" + UUID.randomUUID() + "\",\"activityId\":\"" + UUID.randomUUID() + "\",\"liked\":true}"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd myhive-backend
./gradlew test --tests '*VoteSessionControllerTest'
```

Expected: compilation error — `VoteSessionController` does not exist.

- [ ] **Step 3: Create VoteSessionController**

```java
// myhive-backend/src/main/java/com/myhive/backend/controller/VoteSessionController.java
package com.myhive.backend.controller;

import com.myhive.backend.dto.VoteActivityResponse;
import com.myhive.backend.dto.VoteRequest;
import com.myhive.backend.dto.VoteResultResponse;
import com.myhive.backend.dto.VoteSessionCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.service.VoteSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/vote/sessions")
@RequiredArgsConstructor
public class VoteSessionController {

    private final VoteSessionService voteSessionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VoteSessionResponse createSession(@Valid @RequestBody VoteSessionCreateRequest request) {
        return voteSessionService.createSession(request);
    }

    @GetMapping("/{shareToken}")
    public VoteSessionResponse getSession(@PathVariable UUID shareToken) {
        return voteSessionService.getSession(shareToken);
    }

    @GetMapping("/{shareToken}/activities")
    public List<VoteActivityResponse> getActivities(@PathVariable UUID shareToken) {
        return voteSessionService.getActivities(shareToken);
    }

    @PostMapping("/{shareToken}/votes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void castVote(@PathVariable UUID shareToken, @Valid @RequestBody VoteRequest request) {
        voteSessionService.castVote(shareToken, request);
    }

    @GetMapping("/{shareToken}/participant-count")
    public Map<String, Long> getParticipantCount(@PathVariable UUID shareToken) {
        return Map.of("count", voteSessionService.getParticipantCount(shareToken));
    }

    @GetMapping("/{shareToken}/result")
    public VoteResultResponse getResult(@PathVariable UUID shareToken) {
        return voteSessionService.getResult(shareToken);
    }
}
```

- [ ] **Step 4: Allow `/vote/**` in SecurityConfig**

In `SecurityConfig.java`, add this line inside `authorizeHttpRequests` after the other public endpoints (before `.anyRequest().authenticated()`):

```java
.requestMatchers("/vote/**").permitAll()
```

- [ ] **Step 5: Run tests**

```bash
cd myhive-backend
./gradlew test --tests '*VoteSessionControllerTest'
```

Expected: all 3 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/controller/VoteSessionController.java \
        myhive-backend/src/main/java/com/myhive/backend/config/SecurityConfig.java \
        myhive-backend/src/test/java/com/myhive/backend/controller/VoteSessionControllerTest.java
git commit -m "feat: add VoteSessionController and permit /vote/** in SecurityConfig"
```

---

## Task 4: Backend — VoteSessionScheduler

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionScheduler.java`
- Create: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionSchedulerTest.java`

- [ ] **Step 1: Write failing tests**

```java
// myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionSchedulerTest.java
package com.myhive.backend.service;

import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.repository.VoteSessionResultActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoteSessionSchedulerTest {

    @Mock private VoteSessionRepository voteSessionRepository;
    @Mock private VoteActivityLikeRepository voteActivityLikeRepository;
    @Mock private VoteSessionResultActivityRepository resultActivityRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private VoteSessionScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "emailEnabled", false);
    }

    @Test
    void processSession_selectsActivitiesByLikesWithinBudget() {
        // 2 days = 960 min budget. A=480min(3 likes), B=480min(2 likes), C=480min(1 like)
        // Expected: A and B selected, C excluded (budget exhausted)
        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setStartDate(LocalDate.of(2026, 7, 1));
        session.setEndDate(LocalDate.of(2026, 7, 2));

        UUID actAId = UUID.randomUUID();
        UUID actBId = UUID.randomUUID();
        UUID actCId = UUID.randomUUID();

        Activity actA = new Activity(); actA.setId(actAId); actA.setDuration(480);
        Activity actB = new Activity(); actB.setId(actBId); actB.setDuration(480);

        when(voteActivityLikeRepository.findLikedActivitiesWithCounts(session.getId()))
                .thenReturn(List.of(
                        new Object[]{actAId, 480, 3L},
                        new Object[]{actBId, 480, 2L},
                        new Object[]{actCId, 480, 1L}));
        when(activityRepository.findById(actAId)).thenReturn(Optional.of(actA));
        when(activityRepository.findById(actBId)).thenReturn(Optional.of(actB));
        when(voteSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scheduler.processSession(session);

        ArgumentCaptor<VoteSessionResultActivity> captor =
                ArgumentCaptor.forClass(VoteSessionResultActivity.class);
        verify(resultActivityRepository, times(2)).save(captor.capture());

        List<VoteSessionResultActivity> saved = captor.getAllValues();
        assertThat(saved.get(0).getActivity().getId()).isEqualTo(actAId);
        assertThat(saved.get(0).getSortOrder()).isEqualTo(0);
        assertThat(saved.get(1).getActivity().getId()).isEqualTo(actBId);
        assertThat(saved.get(1).getSortOrder()).isEqualTo(1);
        assertThat(session.getStatus()).isEqualTo(VoteSessionStatus.COMPLETED);
    }

    @Test
    void processSession_completesWithEmptyResultWhenNoLikes() {
        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setStartDate(LocalDate.of(2026, 7, 1));
        session.setEndDate(LocalDate.of(2026, 7, 3));

        when(voteActivityLikeRepository.findLikedActivitiesWithCounts(session.getId()))
                .thenReturn(List.of());
        when(voteSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scheduler.processSession(session);

        verify(resultActivityRepository, never()).save(any());
        assertThat(session.getStatus()).isEqualTo(VoteSessionStatus.COMPLETED);
    }

    @Test
    void processSession_skipsActivityWithNullDuration() {
        VoteSession session = new VoteSession();
        session.setId(UUID.randomUUID());
        session.setStatus(VoteSessionStatus.ACTIVE);
        session.setStartDate(LocalDate.of(2026, 7, 1));
        session.setEndDate(LocalDate.of(2026, 7, 1));

        UUID actId = UUID.randomUUID();
        when(voteActivityLikeRepository.findLikedActivitiesWithCounts(session.getId()))
                .thenReturn(List.of(new Object[]{actId, null, 5L}));
        when(voteSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scheduler.processSession(session);

        verify(resultActivityRepository, never()).save(any());
        assertThat(session.getStatus()).isEqualTo(VoteSessionStatus.COMPLETED);
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd myhive-backend
./gradlew test --tests '*VoteSessionSchedulerTest'
```

Expected: compilation error.

- [ ] **Step 3: Implement VoteSessionScheduler**

```java
// myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionScheduler.java
package com.myhive.backend.service;

import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionResultActivity;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.repository.VoteSessionResultActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoteSessionScheduler {

    private static final int MINUTES_PER_DAY = 480;

    private final VoteSessionRepository voteSessionRepository;
    private final VoteActivityLikeRepository voteActivityLikeRepository;
    private final VoteSessionResultActivityRepository resultActivityRepository;
    private final ActivityRepository activityRepository;
    private final EmailService emailService;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.site.url:https://trivlu.com}")
    private String siteUrl;

    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void processExpiredSessions() {
        List<VoteSession> expired = voteSessionRepository
                .findByStatusAndExpiresAtBefore(VoteSessionStatus.ACTIVE, LocalDateTime.now());

        for (VoteSession session : expired) {
            try {
                processSession(session);
            } catch (Exception e) {
                log.error("Failed to process vote session {}: {}", session.getId(), e.getMessage(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        int deleted = voteSessionRepository
                .deleteByStatusAndExpiresAtBefore(VoteSessionStatus.COMPLETED, cutoff);
        log.info("Cleaned up {} completed vote sessions", deleted);
    }

    void processSession(VoteSession session) {
        long tripDays = ChronoUnit.DAYS.between(session.getStartDate(), session.getEndDate()) + 1;
        int budgetMinutes = (int) (tripDays * MINUTES_PER_DAY);

        List<Object[]> likedRows = voteActivityLikeRepository
                .findLikedActivitiesWithCounts(session.getId());

        int remaining = budgetMinutes;
        int sortOrder = 0;

        for (Object[] row : likedRows) {
            UUID activityId = (UUID) row[0];
            Integer duration = row[1] != null ? ((Number) row[1]).intValue() : null;

            if (duration == null || duration > remaining) {
                continue;
            }

            Optional<Activity> activityOpt = activityRepository.findById(activityId);
            if (activityOpt.isEmpty()) {
                continue;
            }

            VoteSessionResultActivity result = new VoteSessionResultActivity();
            result.setSession(session);
            result.setActivity(activityOpt.get());
            result.setSortOrder(sortOrder++);
            resultActivityRepository.save(result);
            remaining -= duration;
        }

        session.setStatus(VoteSessionStatus.COMPLETED);
        voteSessionRepository.save(session);
        log.info("Processed vote session {} — {} activities selected", session.getId(), sortOrder);

        if (emailEnabled) {
            emailService.sendVoteResult(session, siteUrl);
        }
    }
}
```

Also add `@EnableScheduling` to a config class. The simplest place is `EmailTemplateConfig.java` — add `@EnableScheduling` at the class level. Or create a dedicated `SchedulingConfig`:

```java
// myhive-backend/src/main/java/com/myhive/backend/config/SchedulingConfig.java
package com.myhive.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {}
```

- [ ] **Step 4: Run tests**

```bash
cd myhive-backend
./gradlew test --tests '*VoteSessionSchedulerTest'
```

Expected: all 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionScheduler.java \
        myhive-backend/src/main/java/com/myhive/backend/config/SchedulingConfig.java \
        myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionSchedulerTest.java
git commit -m "feat: add VoteSessionScheduler with greedy-fill algorithm and cleanup"
```

---

## Task 5: Backend — Vote Result Email

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java`
- Create: `myhive-backend/src/main/resources/templates/email/vote-result.html`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java`

- [ ] **Step 1: Add `sendVoteResult` method to EmailService**

Add this method to `EmailService.java` (after `sendContactNotification`). Also add the import `import com.myhive.backend.entity.VoteSession;`:

```java
public void sendVoteResult(VoteSession session, String siteUrl) {
    log.info("Sending vote result email to: {}", session.getInitiatorEmail());
    try {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(session.getInitiatorEmail());
        helper.setSubject("Your group trip to " + session.getDestination().getName() + " is ready!");

        Context context = new Context();
        context.setVariable("session", session);
        context.setVariable("resultUrl", siteUrl + "/vote/" + session.getShareToken() + "/result");

        String htmlContent = templateEngine.process("vote-result", context);
        helper.setText(htmlContent, true);

        mailSender.send(message);
        log.info("Vote result email sent to: {}", session.getInitiatorEmail());

    } catch (Exception e) {
        log.error("Failed to send vote result email to: {}. Cause: {}", session.getInitiatorEmail(), e.getMessage(), e);
        throw new EmailSendException("Failed to send vote result email", e);
    }
}
```

- [ ] **Step 2: Create vote-result.html email template**

```html
<!-- myhive-backend/src/main/resources/templates/email/vote-result.html -->
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Your Group Trip is Ready</title>
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; background: #f0f0f0; margin: 0; padding: 20px 0; }
        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 8px; overflow: hidden; }
        .header { background: #6A1B9A; color: white; padding: 32px 30px; text-align: center; border-bottom: 3px solid #4A148C; }
        .header h1 { margin: 12px 0 8px; font-size: 22px; font-weight: 700; }
        .header p { margin: 0; color: rgba(245,245,245,0.75); font-size: 14px; }
        .content { padding: 30px; }
        .activity-item { display: flex; align-items: center; padding: 12px 0; border-bottom: 1px solid #eee; }
        .activity-info { flex: 1; }
        .activity-name { font-weight: 600; font-size: 15px; margin-bottom: 4px; }
        .activity-meta { color: #666; font-size: 13px; }
        .cta-button { display: block; width: fit-content; margin: 30px auto; padding: 14px 32px; background: #6A1B9A; color: white; text-decoration: none; border-radius: 6px; font-weight: 600; font-size: 16px; text-align: center; }
        .footer { background: #f8f8f8; padding: 20px 30px; text-align: center; font-size: 12px; color: #999; }
    </style>
</head>
<body>
<div class="container">
    <div class="header">
        <h1>Your Group Trip is Ready! 🎉</h1>
        <p th:text="'Trip to ' + ${session.destination.name}"></p>
    </div>
    <div class="content">
        <p>The 24-hour voting window has closed. Here are the activities your group chose for <strong th:text="${session.destination.name}"></strong>:</p>

        <div th:each="result : ${session.resultActivities}">
            <div class="activity-item">
                <div class="activity-info">
                    <div class="activity-name" th:text="${result.activity.name}"></div>
                    <div class="activity-meta">
                        <span th:if="${result.activity.duration != null}" th:text="${result.activity.duration / 60} + 'h · '"></span>
                        <span th:text="'€' + ${result.activity.price} + '/person'"></span>
                    </div>
                </div>
            </div>
        </div>

        <p style="margin-top: 20px; color: #666; font-size: 14px;">
            Open in Trip Builder to review, make changes if needed, and complete your booking.
        </p>

        <a th:href="${resultUrl}" class="cta-button">Open in Trip Builder</a>
    </div>
    <div class="footer">
        <p>Trivlu Travel · <a href="https://trivlu.com" style="color: #6A1B9A;">trivlu.com</a></p>
    </div>
</div>
</body>
</html>
```

Note: the template uses `session.resultActivities` — add a convenience accessor to `VoteSession` by adding a `@OneToMany` or simply passing the results from the scheduler. The cleanest approach is to pass a `VoteResultEmailContext` object with the pre-loaded data. Update `EmailService.sendVoteResult` and the template to receive pre-loaded activities:

Update the method signature in `EmailService.java` to pass result activities separately:
```java
public void sendVoteResult(VoteSession session, List<VoteSessionResultActivity> resultActivities, String siteUrl) {
    ...
    context.setVariable("session", session);
    context.setVariable("resultActivities", resultActivities);
    context.setVariable("resultUrl", siteUrl + "/vote/" + session.getShareToken() + "/result");
    ...
}
```

Update the template to use `resultActivities` instead of `session.resultActivities`:
```html
<div th:each="result : ${resultActivities}">
```

Update `VoteSessionScheduler.processSession` to load and pass results:
```java
if (emailEnabled) {
    List<VoteSessionResultActivity> results = resultActivityRepository
            .findBySessionIdOrderBySortOrder(session.getId());
    emailService.sendVoteResult(session, results, siteUrl);
}
```

- [ ] **Step 3: Write test for sendVoteResult**

In `EmailServiceTest.java`, add:

```java
@Test
void sendVoteResult_doesNotThrowWhenMailSucceeds() throws Exception {
    VoteSession session = new VoteSession();
    session.setShareToken(UUID.randomUUID());

    Destination destination = new Destination();
    destination.setName("Bali");
    session.setDestination(destination);
    session.setInitiatorEmail("alice@example.com");

    MimeMessage mimeMessage = mock(MimeMessage.class);
    MimeMessageHelper helper = mock(MimeMessageHelper.class);
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    when(templateEngine.process(eq("vote-result"), any())).thenReturn("<html>test</html>");
    doNothing().when(mailSender).send(any(MimeMessage.class));

    assertThatCode(() -> emailService.sendVoteResult(session, List.of(), "https://trivlu.com"))
            .doesNotThrowAnyException();
}
```

- [ ] **Step 4: Run all backend tests**

```bash
cd myhive-backend
./gradlew test
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java \
        myhive-backend/src/main/resources/templates/email/vote-result.html \
        myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java
git commit -m "feat: add vote result email template and EmailService method"
```

---

## Task 6: Frontend — voteApi.js

**Files:**
- Create: `myhive-react-app/src/services/voteApi.js`

- [ ] **Step 1: Create voteApi.js**

```js
// myhive-react-app/src/services/voteApi.js
import { API_BASE_URL } from './config';

const voteApi = {
    async createSession({ destinationId, initiatorEmail, numberOfTravelers, startDate, endDate, likedCategoryIds }) {
        const response = await fetch(`${API_BASE_URL}/vote/sessions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ destinationId, initiatorEmail, numberOfTravelers, startDate, endDate, likedCategoryIds }),
        });
        if (!response.ok) throw new Error('Failed to create vote session');
        return response.json();
    },

    async getSession(shareToken) {
        const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}`);
        if (!response.ok) throw new Error('Failed to fetch vote session');
        return response.json();
    },

    async getActivities(shareToken) {
        const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}/activities`);
        if (!response.ok) throw new Error('Failed to fetch vote activities');
        return response.json();
    },

    async castVote(shareToken, { voterToken, activityId, liked }) {
        const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}/votes`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ voterToken, activityId, liked }),
        });
        if (response.status === 403) throw new Error('Session is full');
        if (!response.ok) throw new Error('Failed to cast vote');
    },

    async getParticipantCount(shareToken) {
        const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}/participant-count`);
        if (!response.ok) throw new Error('Failed to fetch participant count');
        return response.json();
    },

    async getResult(shareToken) {
        const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}/result`);
        if (response.status === 404) throw new Error('Result not available yet');
        if (!response.ok) throw new Error('Failed to fetch vote result');
        return response.json();
    },
};

export default voteApi;
```

- [ ] **Step 2: Commit**

```bash
git add myhive-react-app/src/services/voteApi.js
git commit -m "feat: add voteApi service"
```

---

## Task 7: Frontend — SwipeCard Component

**Files:**
- Create: `myhive-react-app/src/components/SwipeCard.js`
- Create: `myhive-react-app/src/components/SwipeCard.css`

- [ ] **Step 1: Install react-tinder-card**

```bash
cd myhive-react-app
npm install react-tinder-card
```

Expected: `react-tinder-card` appears in `package.json` dependencies.

- [ ] **Step 2: Create SwipeCard.css**

```css
/* myhive-react-app/src/components/SwipeCard.css */
.swipe-card-page {
    display: flex;
    flex-direction: column;
    align-items: center;
    min-height: 100vh;
    padding: 24px 16px;
    background: var(--bs-body-bg, #f8f9fa);
}

.swipe-card-title {
    font-size: 1.4rem;
    font-weight: 700;
    margin-bottom: 8px;
    text-align: center;
}

.swipe-card-subtitle {
    color: #6c757d;
    margin-bottom: 24px;
    text-align: center;
    font-size: 0.95rem;
}

.swipe-card-progress {
    font-size: 0.85rem;
    color: #6c757d;
    margin-bottom: 16px;
}

.swipe-card-stack {
    position: relative;
    width: 320px;
    height: 420px;
    margin-bottom: 32px;
}

.swipe-tinder-card {
    position: absolute;
    width: 320px;
    height: 420px;
}

.swipe-card {
    position: relative;
    width: 100%;
    height: 100%;
    background: white;
    border-radius: 16px;
    overflow: hidden;
    box-shadow: 0 8px 32px rgba(0,0,0,0.12);
    user-select: none;
    cursor: grab;
}

.swipe-card:active {
    cursor: grabbing;
}

.swipe-card-image {
    width: 100%;
    height: 260px;
    object-fit: cover;
}

.swipe-card-image-placeholder {
    width: 100%;
    height: 260px;
    background: #e9ecef;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 3rem;
}

.swipe-card-info {
    padding: 16px;
}

.swipe-card-name {
    font-weight: 700;
    font-size: 1.1rem;
    margin-bottom: 6px;
}

.swipe-card-meta {
    color: #6c757d;
    font-size: 0.875rem;
}

.swipe-overlay {
    position: absolute;
    top: 20px;
    padding: 6px 16px;
    border-radius: 8px;
    font-weight: 800;
    font-size: 1.4rem;
    opacity: 0;
    transition: opacity 0.1s;
    pointer-events: none;
    border: 3px solid;
}

.swipe-overlay-like {
    left: 20px;
    color: #28a745;
    border-color: #28a745;
    background: rgba(40,167,69,0.1);
}

.swipe-overlay-dislike {
    right: 20px;
    color: #dc3545;
    border-color: #dc3545;
    background: rgba(220,53,69,0.1);
}

.swipe-buttons {
    display: flex;
    gap: 32px;
    justify-content: center;
}

.swipe-btn {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    border: 2px solid;
    font-size: 1.4rem;
    cursor: pointer;
    background: white;
    transition: transform 0.1s;
    display: flex;
    align-items: center;
    justify-content: center;
}

.swipe-btn:hover {
    transform: scale(1.1);
}

.swipe-btn-dislike {
    border-color: #dc3545;
    color: #dc3545;
}

.swipe-btn-like {
    border-color: #28a745;
    color: #28a745;
}

.swipe-done {
    text-align: center;
    padding: 40px;
}
```

- [ ] **Step 3: Create SwipeCard.js**

```js
// myhive-react-app/src/components/SwipeCard.js
import { useRef } from 'react';
import TinderCard from 'react-tinder-card';
import './SwipeCard.css';

function SwipeCard({ cards, currentIndex, onSwipe, title, subtitle }) {
    const refs = useRef(cards.map(() => null));

    const handleButtonSwipe = async (direction, index) => {
        if (refs.current[index]) {
            await refs.current[index].swipe(direction);
        }
    };

    if (currentIndex >= cards.length) {
        return (
            <div className="swipe-card-page">
                <div className="swipe-done">
                    <p>Processing your choices...</p>
                </div>
            </div>
        );
    }

    const card = cards[currentIndex];

    return (
        <div className="swipe-card-page">
            {title && <h2 className="swipe-card-title">{title}</h2>}
            {subtitle && <p className="swipe-card-subtitle">{subtitle}</p>}
            <div className="swipe-card-progress">
                {currentIndex + 1} / {cards.length}
            </div>

            <div className="swipe-card-stack">
                {cards.slice(currentIndex, currentIndex + 3).reverse().map((c, stackIdx) => {
                    const absoluteIndex = currentIndex + (2 - stackIdx);
                    return (
                        <TinderCard
                            key={c.id}
                            ref={el => { refs.current[absoluteIndex] = el; }}
                            onSwipe={dir => absoluteIndex === currentIndex && onSwipe(dir, c.id)}
                            preventSwipe={['up', 'down']}
                            className="swipe-tinder-card"
                        >
                            <div className="swipe-card">
                                {c.imageUrl
                                    ? <img src={c.imageUrl} alt={c.name} className="swipe-card-image" />
                                    : <div className="swipe-card-image-placeholder">🌍</div>
                                }
                                <div className="swipe-card-info">
                                    <div className="swipe-card-name">{c.name}</div>
                                    <div className="swipe-card-meta">
                                        {c.duration && <span>{Math.round(c.duration / 60)}h</span>}
                                        {c.duration && c.price && <span> · </span>}
                                        {c.price && <span>€{c.price}/person</span>}
                                    </div>
                                </div>
                                <div className="swipe-overlay swipe-overlay-like">LIKE ♥</div>
                                <div className="swipe-overlay swipe-overlay-dislike">NOPE ✕</div>
                            </div>
                        </TinderCard>
                    );
                })}
            </div>

            <div className="swipe-buttons">
                <button
                    className="swipe-btn swipe-btn-dislike"
                    onClick={() => handleButtonSwipe('left', currentIndex)}
                    aria-label="Dislike"
                >✕</button>
                <button
                    className="swipe-btn swipe-btn-like"
                    onClick={() => handleButtonSwipe('right', currentIndex)}
                    aria-label="Like"
                >♥</button>
            </div>
        </div>
    );
}

export default SwipeCard;
```

- [ ] **Step 4: Commit**

```bash
git add myhive-react-app/src/components/SwipeCard.js \
        myhive-react-app/src/components/SwipeCard.css \
        myhive-react-app/package.json \
        myhive-react-app/package-lock.json
git commit -m "feat: add SwipeCard component with react-tinder-card"
```

---

## Task 8: Frontend — CategoryVotePage

**Files:**
- Create: `myhive-react-app/src/pages/vote/CategoryVotePage.js`

- [ ] **Step 1: Create CategoryVotePage.js**

```js
// myhive-react-app/src/pages/vote/CategoryVotePage.js
import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import api from '../../services/api';
import voteApi from '../../services/voteApi';
import SwipeCard from '../../components/SwipeCard';

function CategoryVotePage() {
    const location = useLocation();
    const navigate = useNavigate();
    const { destinationId, destinationSlug, destinationName, voteSetup } = location.state || {};

    const [categories, setCategories] = useState([]);
    const [currentIndex, setCurrentIndex] = useState(0);
    const [likedCategoryIds, setLikedCategoryIds] = useState([]);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!destinationId || !voteSetup) {
            navigate('/');
            return;
        }
        api.getCategoriesForDestination(destinationId)
            .then(cats => setCategories(cats.map(c => ({ id: c.id, name: c.name }))))
            .catch(() => setError('Failed to load categories'))
            .finally(() => setLoading(false));
    }, [destinationId]);

    const handleSwipe = async (direction, categoryId) => {
        const updatedLikedIds = direction === 'right'
            ? [...likedCategoryIds, categoryId]
            : likedCategoryIds;

        const nextIndex = currentIndex + 1;
        setLikedCategoryIds(updatedLikedIds);
        setCurrentIndex(nextIndex);

        if (nextIndex >= categories.length) {
            await finishAndCreateSession(updatedLikedIds);
        }
    };

    const finishAndCreateSession = async (finalLikedIds) => {
        if (submitting) return;
        setSubmitting(true);
        setError(null);
        try {
            const session = await voteApi.createSession({
                destinationId,
                initiatorEmail: voteSetup.email,
                numberOfTravelers: voteSetup.travelers,
                startDate: voteSetup.startDate,
                endDate: voteSetup.endDate,
                likedCategoryIds: finalLikedIds,
            });
            navigate(`/vote/${session.shareToken}/activities`, {
                state: { isInitiator: true },
            });
        } catch (e) {
            setError(e.message || 'Failed to create session. Please try again.');
            setSubmitting(false);
        }
    };

    if (loading) return <div style={{ padding: 40, textAlign: 'center' }}>Loading categories...</div>;
    if (error) return <div style={{ padding: 40, textAlign: 'center', color: 'red' }}>{error}</div>;

    return (
        <SwipeCard
            cards={categories}
            currentIndex={currentIndex}
            onSwipe={handleSwipe}
            title={`What interests you in ${destinationName || 'this destination'}?`}
            subtitle="Swipe right to like a category, left to skip"
        />
    );
}

export default CategoryVotePage;
```

- [ ] **Step 2: Check that `api.getCategoriesForDestination` exists**

Open `myhive-react-app/src/services/api.js` and verify the method `getCategoriesForDestination(destinationId)` is defined. If it calls a different endpoint, confirm and adjust. In `TripBuilder.js` it is called as `api.getCategoriesForDestination(destinationId)`, so the method exists.

- [ ] **Step 3: Commit**

```bash
git add myhive-react-app/src/pages/vote/CategoryVotePage.js
git commit -m "feat: add CategoryVotePage"
```

---

## Task 9: Frontend — ActivityVotePage

**Files:**
- Create: `myhive-react-app/src/pages/vote/ActivityVotePage.js`

- [ ] **Step 1: Create ActivityVotePage.js**

```js
// myhive-react-app/src/pages/vote/ActivityVotePage.js
import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import SwipeCard from '../../components/SwipeCard';

const VOTER_TOKEN_KEY = (shareToken) => `myhive-voter-${shareToken}`;

function getOrCreateVoterToken(shareToken) {
    const key = VOTER_TOKEN_KEY(shareToken);
    let token = localStorage.getItem(key);
    if (!token) {
        token = crypto.randomUUID();
        localStorage.setItem(key, token);
    }
    return token;
}

function ActivityVotePage() {
    const { shareToken } = useParams();
    const location = useLocation();
    const navigate = useNavigate();

    const [activities, setActivities] = useState([]);
    const [currentIndex, setCurrentIndex] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const voterToken = getOrCreateVoterToken(shareToken);

    useEffect(() => {
        voteApi.getActivities(shareToken)
            .then(setActivities)
            .catch(e => setError(e.message))
            .finally(() => setLoading(false));
    }, [shareToken]);

    const handleSwipe = async (direction, activityId) => {
        const nextIndex = currentIndex + 1;
        setCurrentIndex(nextIndex);

        try {
            await voteApi.castVote(shareToken, {
                voterToken,
                activityId,
                liked: direction === 'right',
            });
        } catch (e) {
            if (e.message === 'Session is full') {
                navigate(`/vote/${shareToken}/waiting`);
                return;
            }
        }

        if (nextIndex >= activities.length) {
            navigate(`/vote/${shareToken}/waiting`);
        }
    };

    if (loading) return <div style={{ padding: 40, textAlign: 'center' }}>Loading activities...</div>;
    if (error) return <div style={{ padding: 40, textAlign: 'center', color: 'red' }}>{error}</div>;
    if (activities.length === 0) {
        return (
            <div style={{ padding: 40, textAlign: 'center' }}>
                <p>No activities found for the selected categories.</p>
            </div>
        );
    }

    return (
        <SwipeCard
            cards={activities}
            currentIndex={currentIndex}
            onSwipe={handleSwipe}
            title="Which activities are you up for?"
            subtitle="Swipe right to vote yes, left to skip"
        />
    );
}

export default ActivityVotePage;
```

- [ ] **Step 2: Commit**

```bash
git add myhive-react-app/src/pages/vote/ActivityVotePage.js
git commit -m "feat: add ActivityVotePage"
```

---

## Task 10: Frontend — VoteWaitingPage + VoteResultPage

**Files:**
- Create: `myhive-react-app/src/pages/vote/VoteWaitingPage.js`
- Create: `myhive-react-app/src/pages/vote/VoteResultPage.js`

- [ ] **Step 1: Create VoteWaitingPage.js**

```js
// myhive-react-app/src/pages/vote/VoteWaitingPage.js
import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import voteApi from '../../services/voteApi';

function VoteWaitingPage() {
    const { shareToken } = useParams();
    const [session, setSession] = useState(null);
    const [participantCount, setParticipantCount] = useState(0);
    const [timeLeft, setTimeLeft] = useState('');
    const [copied, setCopied] = useState(false);

    useEffect(() => {
        voteApi.getSession(shareToken).then(setSession).catch(() => {});
    }, [shareToken]);

    useEffect(() => {
        if (!session?.expiresAt) return;
        const tick = () => {
            const diff = new Date(session.expiresAt) - Date.now();
            if (diff <= 0) { setTimeLeft('Processing results...'); return; }
            const hours = Math.floor(diff / 3_600_000);
            const minutes = Math.floor((diff % 3_600_000) / 60_000);
            setTimeLeft(`${hours}h ${minutes}m`);
        };
        tick();
        const id = setInterval(tick, 60_000);
        return () => clearInterval(id);
    }, [session]);

    useEffect(() => {
        const poll = () => voteApi.getParticipantCount(shareToken)
            .then(data => setParticipantCount(data.count))
            .catch(() => {});
        poll();
        const id = setInterval(poll, 30_000);
        return () => clearInterval(id);
    }, [shareToken]);

    const shareUrl = `${window.location.origin}/vote/${shareToken}/activities`;

    const handleCopy = () => {
        navigator.clipboard.writeText(shareUrl);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    return (
        <div style={{ maxWidth: 480, margin: '60px auto', padding: '0 16px', textAlign: 'center' }}>
            <h2 style={{ marginBottom: 8 }}>Voting is open! 🗳️</h2>
            <p style={{ color: '#6c757d', marginBottom: 32 }}>
                {session?.destinationName ? `Trip to ${session.destinationName}` : 'Your vote session'}
            </p>

            <div style={{ background: '#f8f9fa', borderRadius: 12, padding: 24, marginBottom: 24 }}>
                <div style={{ fontSize: '2rem', fontWeight: 700, color: '#6A1B9A' }}>{timeLeft}</div>
                <div style={{ color: '#6c757d', fontSize: 14, marginTop: 4 }}>until results</div>
            </div>

            <div style={{ background: '#f8f9fa', borderRadius: 12, padding: 24, marginBottom: 24 }}>
                <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>{participantCount}</div>
                <div style={{ color: '#6c757d', fontSize: 14, marginTop: 4 }}>
                    {participantCount === 1 ? 'person voted' : 'people voted'}
                </div>
            </div>

            <p style={{ marginBottom: 12, fontWeight: 600 }}>Share with friends:</p>
            <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                <input
                    readOnly
                    value={shareUrl}
                    style={{ flex: 1, padding: '8px 12px', borderRadius: 6, border: '1px solid #dee2e6', fontSize: 13 }}
                />
                <button
                    onClick={handleCopy}
                    style={{ padding: '8px 16px', background: copied ? '#28a745' : '#6A1B9A', color: 'white', border: 'none', borderRadius: 6, cursor: 'pointer', fontWeight: 600 }}
                >
                    {copied ? 'Copied!' : 'Copy'}
                </button>
            </div>
            <p style={{ color: '#6c757d', fontSize: 13 }}>
                Results will be emailed to the trip organiser after the timer ends.
            </p>
        </div>
    );
}

export default VoteWaitingPage;
```

- [ ] **Step 2: Create VoteResultPage.js**

```js
// myhive-react-app/src/pages/vote/VoteResultPage.js
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import { formatPricePerPerson } from '../../utils/format';

function VoteResultPage() {
    const { shareToken } = useParams();
    const navigate = useNavigate();
    const [result, setResult] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        voteApi.getResult(shareToken)
            .then(setResult)
            .catch(e => setError(e.message))
            .finally(() => setLoading(false));
    }, [shareToken]);

    const handleOpenTripBuilder = () => {
        navigate(`/destination/${result.destinationSlug}?tab=trip-builder&voteSession=${shareToken}`);
    };

    if (loading) return <div style={{ padding: 40, textAlign: 'center' }}>Loading results...</div>;

    if (error) {
        return (
            <div style={{ padding: 40, textAlign: 'center' }}>
                <p style={{ color: '#dc3545' }}>{error}</p>
                <p style={{ color: '#6c757d' }}>Results are sent by email once the 24-hour window closes.</p>
            </div>
        );
    }

    return (
        <div style={{ maxWidth: 560, margin: '40px auto', padding: '0 16px' }}>
            <h2 style={{ marginBottom: 4 }}>Your Group Trip to {result.destinationName} 🎉</h2>
            <p style={{ color: '#6c757d', marginBottom: 24 }}>
                {result.activities.length} activities · {result.numberOfTravelers} travellers
            </p>

            {result.activities.length === 0 ? (
                <p style={{ color: '#6c757d' }}>No activities matched the group's votes. Try adjusting the categories.</p>
            ) : (
                <>
                    <div style={{ marginBottom: 24 }}>
                        {result.activities.map(activity => (
                            <div key={activity.id} style={{ display: 'flex', gap: 12, padding: '12px 0', borderBottom: '1px solid #dee2e6' }}>
                                {activity.imageUrl && (
                                    <img src={activity.imageUrl} alt={activity.name}
                                         style={{ width: 72, height: 72, objectFit: 'cover', borderRadius: 8, flexShrink: 0 }} />
                                )}
                                <div>
                                    <div style={{ fontWeight: 600 }}>{activity.name}</div>
                                    <div style={{ color: '#6c757d', fontSize: 13 }}>
                                        {activity.duration && <span>{Math.round(activity.duration / 60)}h · </span>}
                                        {formatPricePerPerson(activity.price)}
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                    <div style={{ fontWeight: 700, fontSize: '1.1rem', marginBottom: 24 }}>
                        Total: €{result.totalPrice}
                    </div>
                </>
            )}

            <button
                onClick={handleOpenTripBuilder}
                style={{ width: '100%', padding: '14px', background: '#6A1B9A', color: 'white', border: 'none', borderRadius: 8, fontWeight: 700, fontSize: '1rem', cursor: 'pointer' }}
            >
                Open in Trip Builder
            </button>
        </div>
    );
}

export default VoteResultPage;
```

- [ ] **Step 3: Commit**

```bash
git add myhive-react-app/src/pages/vote/VoteWaitingPage.js \
        myhive-react-app/src/pages/vote/VoteResultPage.js
git commit -m "feat: add VoteWaitingPage and VoteResultPage"
```

---

## Task 11: Frontend — Existing Code Changes + Routing

**Files:**
- Modify: `myhive-react-app/src/components/TripSetupModal.js`
- Modify: `myhive-react-app/src/components/TripBuilderDropdown.js`
- Modify: `myhive-react-app/src/components/TripBuilder.js`
- Modify: `myhive-react-app/src/App.js`

- [ ] **Step 1: Update TripSetupModal to support vote mode**

Replace `TripSetupModal.js` with the version below. The only changes are: `isVoteMode`, `voteOpen`, `onVoteConfirm`, `onVoteCancel` props, an `email` state field, and split confirm/cancel handlers. Normal dispatch flow is completely unchanged.

```js
import { useContext, useEffect, useState } from 'react';
import { AppContext } from '../context/AppContext';
import './ContactForm.css';
import DateRangePicker from './DateRangePicker';

function TripSetupModal({ isVoteMode = false, voteOpen = false, onVoteConfirm, onVoteCancel }) {
    const { state, dispatch } = useContext(AppContext);
    const [travelers, setTravelers] = useState(1);
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [email, setEmail] = useState('');

    const isOpen = isVoteMode ? voteOpen : state.tripSetupModalOpen;

    useEffect(() => {
        if (isOpen) {
            setTravelers(1);
            setStartDate('');
            setEndDate('');
            setEmail('');
        }
    }, [isOpen]);

    if (!isOpen) return null;

    const handleConfirm = () => {
        if (isVoteMode) {
            if (!startDate || !endDate || !email) return;
            onVoteConfirm({ travelers, startDate, endDate, email });
        } else {
            dispatch({ type: 'SET_TRIP_SETUP', travelers, startDate, endDate });
        }
    };

    const handleCancel = () => {
        if (isVoteMode) {
            onVoteCancel();
        } else {
            dispatch({ type: 'CANCEL_TRIP_SETUP' });
        }
    };

    return (
        <div className="app-modal">
            <div className="app-modal-content">
                <div className="app-modal-header">
                    <h2>{isVoteMode ? 'Set Up Your Vote Session' : 'Set Up Your Trip'}</h2>
                    <button className="app-modal-close-btn" onClick={handleCancel}>×</button>
                </div>
                <div className="app-modal-body">
                    <p className="trip-setup-description">
                        {isVoteMode
                            ? 'Enter your trip details — results will be sent to your email.'
                            : 'Tell us about your group so we can calculate the right price.'}
                    </p>
                    <form className="contact-form" onSubmit={e => e.preventDefault()}>
                        <div className="form-group">
                            <label htmlFor="tripTravelers">Number of Travelers *</label>
                            <input
                                type="number"
                                id="tripTravelers"
                                value={travelers}
                                onChange={e => setTravelers(Math.max(1, parseInt(e.target.value, 10) || 1))}
                                min="1"
                                max="20"
                            />
                        </div>
                        <DateRangePicker
                            from={startDate}
                            to={endDate}
                            onChange={(from, to) => { setStartDate(from); setEndDate(to); }}
                        />
                        {isVoteMode && (
                            <div className="form-group">
                                <label htmlFor="voteEmail">Your Email * <span style={{ fontWeight: 400, color: '#6c757d' }}>(results sent here)</span></label>
                                <input
                                    type="email"
                                    id="voteEmail"
                                    value={email}
                                    onChange={e => setEmail(e.target.value)}
                                    required
                                    placeholder="you@example.com"
                                />
                            </div>
                        )}
                    </form>
                </div>
                <div className="app-modal-footer">
                    <button className="btn btn--secondary" onClick={handleCancel}>Cancel</button>
                    <button
                        className="btn btn--primary"
                        onClick={handleConfirm}
                        disabled={isVoteMode && (!startDate || !endDate || !email)}
                    >
                        {isVoteMode ? 'Continue to Categories' : 'Confirm'}
                    </button>
                </div>
            </div>
        </div>
    );
}

export default TripSetupModal;
```

- [ ] **Step 2: Update TripBuilderDropdown to add "Vote together" button**

In `TripBuilderDropdown.js`:
1. Add imports at top: `import { useState } from 'react';`, `import { useNavigate } from 'react-router-dom';`, `import TripSetupModal from './TripSetupModal';`
2. Add inside the function body (after `const navigate = useNavigate();`):

```js
const [voteSetupOpen, setVoteSetupOpen] = useState(false);

const handleVoteClick = () => setVoteSetupOpen(true);

const handleVoteConfirm = ({ travelers, startDate, endDate, email }) => {
    setVoteSetupOpen(false);
    const destSlug = state.tripItems.find(i => i.destinationSlug)?.destinationSlug;
    const destination = state.destinations.find(d => d.slug === destSlug);
    if (!destination) return;
    dispatch({ type: 'CLOSE_TRIP_BUILDER_MODAL' });
    navigate('/vote/new/categories', {
        state: {
            destinationId: destination.id,
            destinationSlug: destination.slug,
            destinationName: destination.name,
            voteSetup: { travelers, startDate, endDate, email },
        },
    });
};
```

3. Add the "Vote together" button and modal inside the `tripItems.length > 0` block, after the "Complete Booking" button:

```jsx
<button className="trip-builder-vote-btn" onClick={handleVoteClick}>
    🗳️ Vote together &amp; build a trip
</button>

<TripSetupModal
    isVoteMode={true}
    voteOpen={voteSetupOpen}
    onVoteConfirm={handleVoteConfirm}
    onVoteCancel={() => setVoteSetupOpen(false)}
/>
```

4. Add CSS for the new button in `myhive-react-app/src/styles/global.css` (or wherever `trip-builder-complete-btn` is defined):

```css
.trip-builder-vote-btn {
    width: 100%;
    padding: 10px;
    background: transparent;
    color: #6A1B9A;
    border: 2px solid #6A1B9A;
    border-radius: 8px;
    font-weight: 600;
    cursor: pointer;
    margin-top: 8px;
    font-size: 0.9rem;
    transition: background 0.15s;
}

.trip-builder-vote-btn:hover {
    background: #f3e5f5;
}
```

- [ ] **Step 3: Update TripBuilder to load vote session results**

In `TripBuilder.js`, add these imports at the top:
```js
import { useSearchParams } from 'react-router-dom';
import voteApi from '../services/voteApi';
```

Add this hook inside the `TripBuilder` function body, after existing `useState`/`useEffect` hooks:

```js
const [searchParams] = useSearchParams();

useEffect(() => {
    const voteSession = searchParams.get('voteSession');
    if (!voteSession) return;
    voteApi.getResult(voteSession)
        .then(result => {
            result.activities.forEach(activity => {
                dispatch({ type: 'ADD_TO_TRIP', activity, silent: true });
            });
        })
        .catch(() => {});
}, []);
```

- [ ] **Step 4: Add /vote routes to App.js**

In `App.js`:

1. Add imports after existing imports:
```js
import CategoryVotePage from './pages/vote/CategoryVotePage';
import ActivityVotePage from './pages/vote/ActivityVotePage';
import VoteWaitingPage from './pages/vote/VoteWaitingPage';
import VoteResultPage from './pages/vote/VoteResultPage';
```

2. Add routes inside the public `<Route path="/*">` section. Open `App.js` and find where public page routes are defined (inside `Layout`). The public routes are likely in `Layout.js` or inside the `Route path="/*"` element. Check `Layout.js` — if it uses `<Outlet/>`, then add the routes as siblings in `App.js`. 

Since the `Route path="/*"` renders `<AppProvider><Layout/></AppProvider>` and Layout has an outlet, add the vote routes as children of that route in `App.js`:

```jsx
<Route path="/*" element={
    <AppProvider>
        <Layout/>
    </AppProvider>
}>
    <Route path="vote/new/categories" element={<CategoryVotePage />} />
    <Route path="vote/:shareToken/activities" element={<ActivityVotePage />} />
    <Route path="vote/:shareToken/waiting" element={<VoteWaitingPage />} />
    <Route path="vote/:shareToken/result" element={<VoteResultPage />} />
</Route>
```

If `Layout.js` does not use `<Outlet/>` and instead renders routes itself, open `Layout.js`, find its internal `<Routes>` block, and add the four vote routes there instead.

- [ ] **Step 5: Start dev servers and test the golden path manually**

Terminal 1 (backend):
```bash
cd myhive-backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Terminal 2 (frontend):
```bash
cd myhive-react-app
npm start
```

Test flow:
1. Open http://localhost:3000 and navigate to any destination
2. Add an activity to the trip builder
3. Open the trip builder dropdown — verify "Vote together" button appears
4. Click it — verify TripSetupModal opens with email field
5. Fill in dates, travelers, email → "Continue to Categories"
6. Verify category swipe page loads with destination categories
7. Swipe all categories — verify redirect to `/vote/:token/activities`
8. Swipe all activities — verify redirect to `/vote/:token/waiting`
9. Verify share link is displayed and copyable
10. Open the share link in incognito — verify activity swipe page loads

- [ ] **Step 6: Run all backend tests**

```bash
cd myhive-backend
./gradlew test
```

Expected: all tests PASS.

- [ ] **Step 7: Commit**

```bash
git add myhive-react-app/src/components/TripSetupModal.js \
        myhive-react-app/src/components/TripBuilderDropdown.js \
        myhive-react-app/src/components/TripBuilder.js \
        myhive-react-app/src/App.js
git commit -m "feat: wire up Vote Together flow end-to-end"
```

---

## Final Verification

- [ ] Run full backend test suite: `cd myhive-backend && ./gradlew test`
- [ ] Run frontend build to confirm no type/import errors: `cd myhive-react-app && npm run build`
- [ ] Manually test the full round-trip with two browser windows (initiator + friend)
