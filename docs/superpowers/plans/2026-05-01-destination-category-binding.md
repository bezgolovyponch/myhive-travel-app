# Destination–Category Binding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow admins to explicitly bind categories to destinations so that destination pages and TripBuilder show only relevant categories, with auto-computed fallback from activity categories.

**Architecture:** New `destination_categories` join table (auto-created by Hibernate). `DestinationService` gains two methods: `getCategoriesForDestination()` (explicit binding with fallback) and `updateDestinationCategories()` (full replacement). Public endpoint `GET /destinations/{id}/categories` and admin endpoint `PUT /admin/destinations/{id}/categories`. Frontend replaces `getCategories()` with destination-scoped calls in DestinationPage and TripBuilder; AdminDestinations edit form gets a category multiselect.

**Tech Stack:** Spring Boot 4.0 / Java 25 / JPA / Hibernate, JUnit 5 + Mockito, React 19 / Bootstrap 5

---

## File Map

**Backend — modify:**
- `entity/Destination.java` — add `categories` ManyToMany
- `entity/Category.java` — add `destinations` inverse
- `dto/DestinationDTO.java` — add `assignedCategories` field
- `service/DestinationService.java` — inject `CategoryRepository`, add two new methods, update `getDestinationById`
- `controller/DestinationController.java` — add `GET /destinations/{id}/categories`
- `controller/AdminController.java` — add `PUT /admin/destinations/{id}/categories`

**Backend — create:**
- `test/controller/DestinationCategoryControllerTest.java` — controller integration tests

**Backend — modify (tests):**
- `test/service/DestinationServiceTest.java` — add 5 new test methods, add `@Mock CategoryRepository`

**Frontend — modify:**
- `services/api.js` — add `getCategoriesForDestination()`
- `services/adminApi.js` — add `updateDestinationCategories()`
- `pages/DestinationPage.js` — use destination-scoped categories, pass `destinationId` to TripBuilder
- `components/TripBuilder.js` — accept `destinationId` prop, use destination-scoped categories
- `pages/AdminDestinations.js` — add category multiselect in edit form

---

## Task 1: Update Destination and Category entities

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/entity/Destination.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/entity/Category.java`

- [ ] **Step 1: Add `categories` to Destination.java**

Replace the existing `Destination.java` body. Add the `categories` field and update `@ToString` to exclude it:

```java
package com.myhive.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "destinations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"activities", "categories"})
public class Destination {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(unique = true, length = 300)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String city;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(precision = 4, scale = 2)
    private BigDecimal rating;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "destination", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Activity> activities;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "destination_categories",
            joinColumns = @JoinColumn(name = "destination_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private List<Category> categories = new ArrayList<>();
}
```

- [ ] **Step 2: Add `destinations` inverse to Category.java**

Add the `destinations` field and update `@ToString` to exclude it:

```java
package com.myhive.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"activities", "destinations"})
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(unique = true, length = 120)
    private String slug;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany(mappedBy = "categories")
    private Set<Activity> activities = new HashSet<>();

    @ManyToMany(mappedBy = "categories")
    private List<Destination> destinations = new ArrayList<>();
}
```

- [ ] **Step 3: Verify build compiles**

```bash
cd myhive-backend && ./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/entity/Destination.java
git add myhive-backend/src/main/java/com/myhive/backend/entity/Category.java
git commit -m "feat: add destination_categories join table via ManyToMany"
```

---

## Task 2: Add `assignedCategories` to DestinationDTO

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/dto/DestinationDTO.java`

- [ ] **Step 1: Add field**

```java
package com.myhive.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DestinationDTO {
    private UUID id;
    @Size(max = 280, message = "Slug must be at most 280 characters")
    private String slug;
    @NotBlank(message = "Name is required")
    private String name;
    private String description;
    private String country;
    private String city;
    private String imageUrl;
    private BigDecimal rating;
    private int activityCount;
    private List<CategoryDTO> assignedCategories;
}
```

- [ ] **Step 2: Verify build compiles**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/DestinationDTO.java
git commit -m "feat: add assignedCategories field to DestinationDTO"
```

---

## Task 3: TDD — getCategoriesForDestination in DestinationService

**Files:**
- Modify: `myhive-backend/src/test/java/com/myhive/backend/service/DestinationServiceTest.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/DestinationService.java`

- [ ] **Step 1: Add `@Mock CategoryRepository` to the test class**

In `DestinationServiceTest.java`, add after the existing `@Mock DestinationRepository` line:

```java
@Mock
private CategoryRepository categoryRepository;
```

Add import at the top of the file:

```java
import com.myhive.backend.repository.CategoryRepository;
```

- [ ] **Step 2: Write 4 failing tests for `getCategoriesForDestination`**

Add these methods to `DestinationServiceTest`:

```java
@Test
void getCategoriesForDestination_withExplicitCategories_returnsSortedByName() {
    Destination dest = TestDataFactory.destination();
    Category catB = TestDataFactory.category("Wellness");
    Category catA = TestDataFactory.category("Adventure");
    dest.setCategories(List.of(catB, catA));
    when(destinationRepository.findById(dest.getId())).thenReturn(Optional.of(dest));

    List<CategoryDTO> result = destinationService.getCategoriesForDestination(dest.getId());

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getName()).isEqualTo("Adventure");
    assertThat(result.get(1).getName()).isEqualTo("Wellness");
}

@Test
void getCategoriesForDestination_noExplicitCategories_fallsBackToActivityCategories() {
    Destination dest = TestDataFactory.destination();
    Category cat = TestDataFactory.category("Water");
    Activity activity = TestDataFactory.activity(dest, cat);
    dest.setCategories(new ArrayList<>());
    dest.setActivities(List.of(activity));
    when(destinationRepository.findById(dest.getId())).thenReturn(Optional.of(dest));

    List<CategoryDTO> result = destinationService.getCategoriesForDestination(dest.getId());

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getName()).isEqualTo("Water");
}

@Test
void getCategoriesForDestination_noCategories_returnsEmpty() {
    Destination dest = TestDataFactory.destination();
    dest.setCategories(new ArrayList<>());
    dest.setActivities(new ArrayList<>());
    when(destinationRepository.findById(dest.getId())).thenReturn(Optional.of(dest));

    List<CategoryDTO> result = destinationService.getCategoriesForDestination(dest.getId());

    assertThat(result).isEmpty();
}

@Test
void getCategoriesForDestination_notFound_throwsResourceNotFound() {
    UUID id = UUID.randomUUID();
    when(destinationRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> destinationService.getCategoriesForDestination(id))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Destination");
}
```

Add these imports to `DestinationServiceTest.java`:

```java
import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.entity.Category;
import java.util.ArrayList;
```

- [ ] **Step 3: Run tests — confirm they fail**

```bash
./gradlew test --tests '*DestinationServiceTest.getCategoriesForDestination*'
```

Expected: FAILED — `getCategoriesForDestination` method does not exist yet.

- [ ] **Step 4: Implement `getCategoriesForDestination` in DestinationService**

Add `CategoryRepository` field (the `@RequiredArgsConstructor` will inject it):

```java
private final CategoryRepository categoryRepository;
```

Add the method and private helper:

```java
public List<CategoryDTO> getCategoriesForDestination(UUID id) {
    Destination destination = destinationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Destination", id));

    List<Category> explicit = destination.getCategories();
    if (!explicit.isEmpty()) {
        return explicit.stream()
                .sorted(Comparator.comparing(c -> c.getName().toLowerCase()))
                .map(this::categoryToDTO)
                .toList();
    }

    List<Activity> activities = destination.getActivities();
    if (activities == null || activities.isEmpty()) {
        return List.of();
    }

    return activities.stream()
            .flatMap(a -> a.getCategories().stream())
            .distinct()
            .sorted(Comparator.comparing(c -> c.getName().toLowerCase()))
            .map(this::categoryToDTO)
            .toList();
}

private CategoryDTO categoryToDTO(Category category) {
    CategoryDTO dto = new CategoryDTO();
    dto.setId(category.getId());
    dto.setName(category.getName());
    dto.setSlug(category.getSlug());
    return dto;
}
```

Add these imports to `DestinationService.java`:

```java
import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.repository.CategoryRepository;
import java.util.Comparator;
import java.util.List;
```

- [ ] **Step 5: Run tests — confirm they pass**

```bash
./gradlew test --tests '*DestinationServiceTest.getCategoriesForDestination*'
```

Expected: 4 tests PASSED

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/DestinationService.java
git add myhive-backend/src/test/java/com/myhive/backend/service/DestinationServiceTest.java
git commit -m "feat: add getCategoriesForDestination to DestinationService (TDD)"
```

---

## Task 4: TDD — updateDestinationCategories in DestinationService

**Files:**
- Modify: `myhive-backend/src/test/java/com/myhive/backend/service/DestinationServiceTest.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/DestinationService.java`

- [ ] **Step 1: Write 3 failing tests**

Add to `DestinationServiceTest`:

```java
@Test
void updateDestinationCategories_setsAndSavesCategories() {
    Destination dest = TestDataFactory.destination();
    dest.setCategories(new ArrayList<>());
    Category cat = TestDataFactory.category("Spa");
    when(destinationRepository.findById(dest.getId())).thenReturn(Optional.of(dest));
    when(categoryRepository.findAllById(List.of(cat.getId()))).thenReturn(List.of(cat));
    when(destinationRepository.save(dest)).thenReturn(dest);

    destinationService.updateDestinationCategories(dest.getId(), List.of(cat.getId()));

    assertThat(dest.getCategories()).containsExactly(cat);
    verify(destinationRepository).save(dest);
}

@Test
void updateDestinationCategories_emptyList_clearsCategories() {
    Destination dest = TestDataFactory.destination();
    Category existing = TestDataFactory.category("Sport");
    dest.setCategories(new ArrayList<>(List.of(existing)));
    when(destinationRepository.findById(dest.getId())).thenReturn(Optional.of(dest));
    when(categoryRepository.findAllById(List.of())).thenReturn(List.of());
    when(destinationRepository.save(dest)).thenReturn(dest);

    destinationService.updateDestinationCategories(dest.getId(), List.of());

    assertThat(dest.getCategories()).isEmpty();
    verify(destinationRepository).save(dest);
}

@Test
void updateDestinationCategories_notFound_throwsResourceNotFound() {
    UUID id = UUID.randomUUID();
    when(destinationRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> destinationService.updateDestinationCategories(id, List.of()))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Destination");
}
```

- [ ] **Step 2: Run tests — confirm they fail**

```bash
./gradlew test --tests '*DestinationServiceTest.updateDestinationCategories*'
```

Expected: FAILED — method does not exist.

- [ ] **Step 3: Implement `updateDestinationCategories` in DestinationService**

```java
@Transactional
public void updateDestinationCategories(UUID id, List<UUID> categoryIds) {
    Destination destination = destinationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Destination", id));
    List<Category> categories = categoryRepository.findAllById(categoryIds);
    destination.setCategories(new ArrayList<>(categories));
    destinationRepository.save(destination);
}
```

- [ ] **Step 4: Run tests — confirm they pass**

```bash
./gradlew test --tests '*DestinationServiceTest.updateDestinationCategories*'
```

Expected: 3 tests PASSED

- [ ] **Step 5: Run full test suite**

```bash
./gradlew test --tests '*DestinationServiceTest'
```

Expected: all tests PASSED

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/DestinationService.java
git add myhive-backend/src/test/java/com/myhive/backend/service/DestinationServiceTest.java
git commit -m "feat: add updateDestinationCategories to DestinationService (TDD)"
```

---

## Task 5: Populate `assignedCategories` in `getDestinationById`

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/DestinationService.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/service/DestinationServiceTest.java`

- [ ] **Step 1: Write a failing test**

Add to `DestinationServiceTest`:

```java
@Test
void getDestinationById_populatesAssignedCategories() {
    String expectedCategoryName = "Adventure";
    Destination dest = TestDataFactory.destination();
    Category cat = TestDataFactory.category(expectedCategoryName);
    dest.setCategories(List.of(cat));
    when(destinationRepository.findById(dest.getId())).thenReturn(Optional.of(dest));

    DestinationDTO result = destinationService.getDestinationById(dest.getId());

    assertThat(result.getAssignedCategories()).hasSize(1);
    assertThat(result.getAssignedCategories().getFirst().getName()).isEqualTo(expectedCategoryName);
}
```

- [ ] **Step 2: Run test — confirm it fails**

```bash
./gradlew test --tests '*DestinationServiceTest.getDestinationById_populatesAssignedCategories'
```

Expected: FAILED — `assignedCategories` is null.

- [ ] **Step 3: Update `getDestinationById` to populate `assignedCategories`**

In `DestinationService.getDestinationById`, after `convertToDTO`:

```java
public DestinationDTO getDestinationById(UUID id) {
    Destination destination = destinationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Destination", id));
    DestinationDTO dto = convertToDTO(destination);
    dto.setAssignedCategories(
            destination.getCategories().stream()
                    .map(this::categoryToDTO)
                    .toList()
    );
    return dto;
}
```

- [ ] **Step 4: Run tests — confirm all pass**

```bash
./gradlew test --tests '*DestinationServiceTest'
```

Expected: all tests PASSED

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/DestinationService.java
git add myhive-backend/src/test/java/com/myhive/backend/service/DestinationServiceTest.java
git commit -m "feat: populate assignedCategories in getDestinationById"
```

---

## Task 6: Add public endpoint GET /destinations/{id}/categories

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/controller/DestinationController.java`
- Create: `myhive-backend/src/test/java/com/myhive/backend/controller/DestinationCategoryControllerTest.java`

- [ ] **Step 1: Add endpoint to DestinationController**

```java
package com.myhive.backend.controller;

import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.dto.DestinationDTO;
import com.myhive.backend.service.DestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/destinations")
@RequiredArgsConstructor
public class DestinationController {

    private final DestinationService destinationService;

    @GetMapping
    public ResponseEntity<List<DestinationDTO>> getAllDestinations() {
        return ResponseEntity.ok(destinationService.getAllDestinations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DestinationDTO> getDestinationById(@PathVariable UUID id) {
        return ResponseEntity.ok(destinationService.getDestinationById(id));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<DestinationDTO> getDestinationBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(destinationService.getDestinationBySlug(slug));
    }

    @GetMapping("/{id}/categories")
    public ResponseEntity<List<CategoryDTO>> getCategoriesForDestination(@PathVariable UUID id) {
        return ResponseEntity.ok(destinationService.getCategoriesForDestination(id));
    }
}
```

- [ ] **Step 2: Write the controller test — public endpoint**

Create `myhive-backend/src/test/java/com/myhive/backend/controller/DestinationCategoryControllerTest.java`:

```java
package com.myhive.backend.controller;

import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.service.DestinationService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, DestinationCategoryControllerTest.MockConfig.class})
class DestinationCategoryControllerTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        public DestinationService destinationService() {
            return mock(DestinationService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DestinationService destinationService;

    @BeforeEach
    void setUp() {
        reset(destinationService);
    }

    @Test
    void getCategoriesForDestination_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        CategoryDTO category = new CategoryDTO(UUID.randomUUID(), "Adventure", "adventure");
        when(destinationService.getCategoriesForDestination(id)).thenReturn(List.of(category));

        mockMvc.perform(get("/destinations/{id}/categories", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Adventure"))
                .andExpect(jsonPath("$[0].slug").value("adventure"));
    }

    @Test
    void getCategoriesForDestination_emptyResult_returnsEmptyArray() throws Exception {
        UUID id = UUID.randomUUID();
        when(destinationService.getCategoriesForDestination(id)).thenReturn(List.of());

        mockMvc.perform(get("/destinations/{id}/categories", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
```

- [ ] **Step 3: Run controller test**

```bash
./gradlew test --tests '*DestinationCategoryControllerTest.getCategoriesForDestination*'
```

Expected: 2 tests PASSED

- [ ] **Step 4: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/controller/DestinationController.java
git add myhive-backend/src/test/java/com/myhive/backend/controller/DestinationCategoryControllerTest.java
git commit -m "feat: add GET /destinations/{id}/categories public endpoint"
```

---

## Task 7: Add admin endpoint PUT /admin/destinations/{id}/categories

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/controller/AdminController.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/controller/DestinationCategoryControllerTest.java`

- [ ] **Step 1: Add endpoint to AdminController**

In `AdminController.java`, add after the existing `deleteDestination` method:

```java
@PutMapping("/destinations/{id}/categories")
public ResponseEntity<Void> updateDestinationCategories(
        @PathVariable UUID id,
        @RequestBody List<UUID> categoryIds) {
    destinationService.updateDestinationCategories(id, categoryIds);
    return ResponseEntity.noContent().build();
}
```

The `List` import is already present in AdminController.

- [ ] **Step 2: Write admin endpoint tests**

Add to `DestinationCategoryControllerTest`:

```java
@Test
void updateDestinationCategories_noAuth_returnsUnauthorized() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc.perform(put("/admin/destinations/{id}/categories", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("[]"))
            .andExpect(status().isUnauthorized());
}

@Test
void updateDestinationCategories_asAdmin_returnsNoContent() throws Exception {
    UUID id = UUID.randomUUID();
    doNothing().when(destinationService).updateDestinationCategories(eq(id), any());

    mockMvc.perform(put("/admin/destinations/{id}/categories", id)
                    .with(jwt().authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")
                    ))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("[]"))
            .andExpect(status().isNoContent());
}
```

- [ ] **Step 3: Run all controller tests**

```bash
./gradlew test --tests '*DestinationCategoryControllerTest'
```

Expected: 4 tests PASSED

- [ ] **Step 4: Run full backend test suite**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/controller/AdminController.java
git add myhive-backend/src/test/java/com/myhive/backend/controller/DestinationCategoryControllerTest.java
git commit -m "feat: add PUT /admin/destinations/{id}/categories admin endpoint"
```

---

## Task 8: Frontend — api.js and adminApi.js

**Files:**
- Modify: `myhive-react-app/src/services/api.js`
- Modify: `myhive-react-app/src/services/adminApi.js`

- [ ] **Step 1: Add `getCategoriesForDestination` to api.js**

In `api.js`, add after the existing `getCategories` method:

```js
async getCategoriesForDestination(destinationId) {
    const response = await fetch(`${API_BASE_URL}/destinations/${destinationId}/categories`);
    if (!response.ok) throw new Error('Failed to fetch categories for destination');
    return response.json();
},
```

- [ ] **Step 2: Add `updateDestinationCategories` to adminApi.js**

In `adminApi.js`, add after the existing `deleteDestination` method (before `getPackages`):

```js
async updateDestinationCategories(id, categoryIds) {
    const headers = await authHeaders();
    const response = await fetch(`${API_BASE_URL}/admin/destinations/${id}/categories`, {
        method: 'PUT',
        headers,
        body: JSON.stringify(categoryIds),
    });
    await handleError(response, 'Failed to update destination categories');
},
```

- [ ] **Step 3: Start the backend dev server and verify the new endpoint responds**

```bash
cd myhive-backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
```

In another terminal, get any destination ID:
```bash
curl -s http://localhost:8080/destinations
```

Then fetch its categories using an `id` from the response:
```bash
curl -s http://localhost:8080/destinations/<ID>/categories
```

Expected: HTTP 200 with a JSON array (may be empty if no categories are assigned yet).

- [ ] **Step 4: Commit**

```bash
git add myhive-react-app/src/services/api.js
git add myhive-react-app/src/services/adminApi.js
git commit -m "feat: add getCategoriesForDestination and updateDestinationCategories to frontend API clients"
```

---

## Task 9: DestinationPage.js — use destination-scoped categories

**Files:**
- Modify: `myhive-react-app/src/pages/DestinationPage.js`

- [ ] **Step 1: Replace `api.getCategories()` with `api.getCategoriesForDestination()`**

In the `fetchDestinationData` async function, change:

```js
const [destData, categoriesData] = await Promise.all([
    api.getDestinationBySlug(slug),
    api.getCategories(),
]);
```

to:

```js
const destData = await api.getDestinationBySlug(slug);
const categoriesData = await api.getCategoriesForDestination(destData.id);
```

(Split into two awaits — `getCategoriesForDestination` needs `destData.id`, so they cannot run in parallel.)

- [ ] **Step 2: Pass `destinationId` prop to TripBuilder**

In the JSX at the bottom of `DestinationPage.js`, change:

```jsx
<TripBuilder />
```

to:

```jsx
<TripBuilder destinationId={destination?.id} />
```

- [ ] **Step 3: Commit**

```bash
git add myhive-react-app/src/pages/DestinationPage.js
git commit -m "feat: use destination-scoped categories in DestinationPage"
```

---

## Task 10: TripBuilder.js — use destination-scoped categories

**Files:**
- Modify: `myhive-react-app/src/components/TripBuilder.js`

- [ ] **Step 1: Accept `destinationId` prop**

Change the function signature from:

```js
function TripBuilder() {
```

to:

```js
function TripBuilder({ destinationId }) {
```

- [ ] **Step 2: Replace `api.getCategories()` with destination-scoped fetch**

Change the existing `useEffect` that fetches categories:

```js
useEffect(() => {
    api.getCategories().then(setCategories).catch(() => {
    });
}, []);
```

to:

```js
useEffect(() => {
    if (!destinationId) {
        return;
    }
    api.getCategoriesForDestination(destinationId).then(setCategories).catch(() => {});
}, [destinationId]);
```

- [ ] **Step 3: Commit**

```bash
git add myhive-react-app/src/components/TripBuilder.js
git commit -m "feat: use destination-scoped categories in TripBuilder"
```

---

## Task 11: AdminDestinations.js — category multiselect in edit form

**Files:**
- Modify: `myhive-react-app/src/pages/AdminDestinations.js`

- [ ] **Step 1: Add `api` import**

At the top of `AdminDestinations.js`, add:

```js
import api from '../services/api';
```

- [ ] **Step 2: Add category state**

After the `useAdminCrud` block, add:

```js
const [allCategories, setAllCategories] = useState([]);
const [selectedCategoryIds, setSelectedCategoryIds] = useState([]);
```

Update the React import at the very top of the file to include `useEffect` and `useState` (currently only `useCallback` is imported):

```js
import {useCallback, useEffect, useState} from 'react';
```

- [ ] **Step 3: Load all categories on mount**

After the `useAdminCrud` call, add:

```js
useEffect(() => {
    adminApi.getCategories().then(setAllCategories).catch(() => {});
}, []); // eslint-disable-line react-hooks/exhaustive-deps
```

- [ ] **Step 4: Load assigned categories when editing opens**

```js
useEffect(() => {
    if (!editing?.id) {
        setSelectedCategoryIds([]);
        return;
    }
    api.getDestination(editing.id)
        .then(dest => setSelectedCategoryIds((dest.assignedCategories || []).map(c => c.id)))
        .catch(() => setSelectedCategoryIds([]));
}, [editing?.id]); // eslint-disable-line react-hooks/exhaustive-deps
```

- [ ] **Step 5: Update `updateFn` to also save categories**

In the `useAdminCrud` call, change `updateFn`:

```js
updateFn: async (api, id, payload) => {
    const result = await api.updateDestination(id, payload);
    await api.updateDestinationCategories(id, selectedCategoryIds);
    return result;
},
```

- [ ] **Step 6: Add category multiselect to the edit form**

In the `<Modal.Body>` section, after the `<ImageUploadField>` component and before the closing `</Form>`, add:

```jsx
{editing && (
    <Form.Group className="mb-3">
        <Form.Label className="small fw-semibold text-white">Categories</Form.Label>
        <div style={{
            maxHeight: 180,
            overflowY: 'auto',
            border: '1px solid #495057',
            borderRadius: 4,
            padding: '8px 12px',
        }}>
            {allCategories.map(cat => (
                <Form.Check
                    key={cat.id}
                    type="checkbox"
                    id={`cat-${cat.id}`}
                    label={cat.name}
                    checked={selectedCategoryIds.includes(cat.id)}
                    onChange={e => {
                        if (e.target.checked) {
                            setSelectedCategoryIds(prev => [...prev, cat.id]);
                        } else {
                            setSelectedCategoryIds(prev => prev.filter(id => id !== cat.id));
                        }
                    }}
                    className="mb-1"
                />
            ))}
        </div>
        <Form.Text className="text-muted">
            If none selected, categories are derived automatically from activities.
        </Form.Text>
    </Form.Group>
)}
```

- [ ] **Step 7: Start frontend dev server and verify the edit form**

```bash
cd myhive-react-app && npm start
```

Open `http://localhost:3000`. Log in as admin. Navigate to Admin → Destinations. Click "Edit" on a destination. Verify:
- The category multiselect appears with checkboxes.
- Previously assigned categories are pre-checked (initially none).
- Selecting categories and saving calls `PUT /admin/destinations/{id}/categories`.
- After save, opening the destination page shows only selected categories in the filter bar.
- TripBuilder tab also shows only selected categories.

- [ ] **Step 8: Commit**

```bash
git add myhive-react-app/src/pages/AdminDestinations.js
git commit -m "feat: add category multiselect to destination edit form"
```

---

## Final Verification

- [ ] **Run full backend test suite**

```bash
cd myhive-backend && ./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **End-to-end smoke test**

1. Start backend: `./gradlew bootRun --args='--spring.profiles.active=dev'`
2. Start frontend: `cd myhive-react-app && npm start`
3. Admin: assign "Adventure" and "Water" to Tenerife destination.
4. Public: open Tenerife destination page — verify only "Adventure" and "Water" filter buttons appear.
5. Public: clear Tenerife's categories (set to none) — verify filter buttons auto-derive from activity categories.
6. Trip Builder tab: verify same filtered categories appear.
