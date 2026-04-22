# Package Entity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `Package` entity that bundles activities into a discounted, bookable tour with its own admin UI and public page.

**Architecture:** New JPA entity `Package` mirroring `Activity`'s patterns (slug, R2 image, m2m categories) with an ordered `package_activities` join. Hybrid booking — each booking creates one `BookingItem` per activity in the package, all linked by a snapshot `package_id` + `package_discount_pct`. Frontend gets new admin CRUD page and public `PackageDetailPage`; existing `DestinationPage` legacy `PackageCard` is replaced.

**Tech Stack:** Spring Boot 4.0, Java 25, JPA, JUnit 5, MockMvc, React 19, Bootstrap 5, `@dnd-kit/core`.

**Spec:** `docs/superpowers/specs/2026-04-22-package-entity-design.md`

**Existing legacy to replace:** `myhive-react-app/src/components/PackageCard.js` and the empty `state.packages` array in `AppContext` are dead code. We replace `PackageCard` with a real-data version and remove `state.packages` (the new code consumes packages from API responses, not global state).

---

## Phase 1 — Backend: Package entity, repository, DTOs, service, controller

### Task 1.1: Package entity

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/Package.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/PackageActivity.java`

- [ ] **Step 1: Create `Package` entity**

```java
package com.myhive.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"destination", "categories", "packageActivities"})
public class Package {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id", nullable = false)
    private Destination destination;

    @Column(unique = true, length = 300)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String includes;

    private Integer duration;

    @Column(name = "discount_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPct;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "package_categories",
            joinColumns = @JoinColumn(name = "package_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

    @OneToMany(mappedBy = "pkg", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<PackageActivity> packageActivities = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: Create `PackageActivity` join entity**

```java
package com.myhive.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "package_activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(PackageActivity.PackageActivityId.class)
@ToString(exclude = {"pkg", "activity"})
public class PackageActivity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_id")
    private Package pkg;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @Column(nullable = false)
    private Integer position;

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackageActivityId implements Serializable {
        private UUID pkg;
        private UUID activity;
    }
}
```

- [ ] **Step 3: Run app to verify schema generates**

Run: `cd myhive-backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/entity/Package.java \
        myhive-backend/src/main/java/com/myhive/backend/entity/PackageActivity.java
git commit -m "feat: add Package and PackageActivity entities"
```

---

### Task 1.2: Package repository

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/repository/PackageRepository.java`
- Create: `myhive-backend/src/test/java/com/myhive/backend/repository/PackageRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.myhive.backend.repository;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.Package;
import com.myhive.backend.entity.PackageActivity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PackageRepositoryTest {

    @Autowired private PackageRepository packageRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Destination destination;
    private Activity activity;
    private Category category;

    @BeforeEach
    void setUp() {
        destination = destinationRepository.save(TestDataFactory.destination());
        activity = activityRepository.save(TestDataFactory.activity(destination));
        category = categoryRepository.save(TestDataFactory.category("Beach"));
    }

    @Test
    void findBySlugReturnsSavedPackage() {
        Package saved = packageRepository.save(buildPackage("honeymoon-bali"));

        Optional<Package> found = packageRepository.findBySlug("honeymoon-bali");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void existsBySlugIsTrueAfterSave() {
        packageRepository.save(buildPackage("adventure-tour"));

        assertThat(packageRepository.existsBySlug("adventure-tour")).isTrue();
        assertThat(packageRepository.existsBySlug("missing")).isFalse();
    }

    @Test
    void findByDestinationIdReturnsMatching() {
        packageRepository.save(buildPackage("p1"));
        packageRepository.save(buildPackage("p2"));

        List<Package> result = packageRepository.findByDestinationId(destination.getId());

        assertThat(result).hasSize(2);
    }

    @Test
    void findByCategoriesSlugReturnsMatching() {
        Package pkg = buildPackage("with-cat");
        pkg.getCategories().add(category);
        packageRepository.save(pkg);

        List<Package> result = packageRepository.findByCategoriesSlug("beach");

        assertThat(result).hasSize(1);
    }

    @Test
    void findActivityIdsUsedInPackagesReturnsUsedIds() {
        Package pkg = buildPackage("with-act");
        PackageActivity pa = new PackageActivity(pkg, activity, 0);
        pkg.getPackageActivities().add(pa);
        packageRepository.save(pkg);

        List<String> packageNames = packageRepository.findPackageNamesByActivityId(activity.getId());

        assertThat(packageNames).containsExactly("Honeymoon Bali");
    }

    private Package buildPackage(String slug) {
        Package p = new Package();
        p.setDestination(destination);
        p.setSlug(slug);
        p.setName("Honeymoon Bali");
        p.setDiscountPct(new BigDecimal("15.00"));
        return p;
    }
}
```

- [ ] **Step 2: Run test, expect compilation failure**

Run: `cd myhive-backend && ./gradlew test --tests '*PackageRepositoryTest'`
Expected: FAIL with "cannot find symbol PackageRepository".

- [ ] **Step 3: Create repository**

```java
package com.myhive.backend.repository;

import com.myhive.backend.entity.Package;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PackageRepository extends JpaRepository<Package, UUID> {

    Optional<Package> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Package> findByDestinationId(UUID destinationId);

    List<Package> findByCategoriesSlug(String categorySlug);

    List<Package> findByDestinationIdAndCategoriesSlug(UUID destinationId, String categorySlug);

    Page<Package> findAll(Pageable pageable);

    @Query("SELECT p.name FROM Package p JOIN p.packageActivities pa WHERE pa.activity.id = :activityId")
    List<String> findPackageNamesByActivityId(@Param("activityId") UUID activityId);
}
```

- [ ] **Step 4: Run tests, expect pass**

Run: `cd myhive-backend && ./gradlew test --tests '*PackageRepositoryTest'`
Expected: PASS, all 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/repository/PackageRepository.java \
        myhive-backend/src/test/java/com/myhive/backend/repository/PackageRepositoryTest.java
git commit -m "feat: add PackageRepository with slug, destination and category queries"
```

---

### Task 1.3: Package DTOs

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/PackageDTO.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/PackageActivityRefDTO.java`

- [ ] **Step 1: Create `PackageActivityRefDTO`** (lightweight ref used inside package responses and admin requests)

```java
package com.myhive.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageActivityRefDTO {
    @NotNull
    private UUID activityId;

    @NotNull
    @PositiveOrZero
    private Integer position;

    private String slug;
    private String name;
    private BigDecimal price;
    private Integer duration;
    private String imageUrl;
}
```

- [ ] **Step 2: Create `PackageDTO`** (one DTO used for both public response and admin create/update; admin-only fields like `duration`/`discountPct` are always populated server-side)

```java
package com.myhive.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageDTO {
    private UUID id;

    @Size(max = 280)
    private String slug;

    @NotNull(message = "Destination ID is required")
    private UUID destinationId;
    private String destinationName;
    private String destinationSlug;

    @NotBlank(message = "Package name is required")
    @Size(max = 255)
    private String name;

    private String description;

    @Size(max = 500)
    private String imageUrl;

    private String includes;

    private Integer duration;

    @NotNull(message = "Discount percent is required")
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private BigDecimal discountPct;

    @Valid
    private List<PackageActivityRefDTO> activities = new ArrayList<>();

    private List<CategoryDTO> categories = new ArrayList<>();
    private List<UUID> categoryIds = new ArrayList<>();

    private BigDecimal originalPrice;
    private BigDecimal discountedPrice;
    private BigDecimal savings;
}
```

- [ ] **Step 3: Compile**

Run: `cd myhive-backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/PackageDTO.java \
        myhive-backend/src/main/java/com/myhive/backend/dto/PackageActivityRefDTO.java
git commit -m "feat: add PackageDTO and PackageActivityRefDTO"
```

---

### Task 1.4: PackageService — read operations + price calc

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/PackageService.java`
- Create: `myhive-backend/src/test/java/com/myhive/backend/service/PackageServiceTest.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/TestDataFactory.java`

- [ ] **Step 1: Add factory helper**

In `TestDataFactory.java` add:

```java
public static Package pkg(Destination destination) {
    Package p = new Package();
    p.setId(UUID.randomUUID());
    p.setSlug("test-package");
    p.setDestination(destination);
    p.setName("Test Package");
    p.setDescription("Test package description");
    p.setImageUrl("https://example.com/pkg.jpg");
    p.setIncludes("Hotel, transfers");
    p.setDuration(72);
    p.setDiscountPct(new BigDecimal("15.00"));
    p.setCreatedAt(LocalDateTime.now());
    return p;
}
```

Add import: `import com.myhive.backend.entity.Package;`

- [ ] **Step 2: Write failing service test**

```java
package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.dto.PackageDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.Package;
import com.myhive.backend.entity.PackageActivity;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.PackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageServiceTest {

    @Mock private PackageRepository packageRepository;
    @Mock private com.myhive.backend.repository.DestinationRepository destinationRepository;
    @Mock private com.myhive.backend.repository.ActivityRepository activityRepository;
    @Mock private com.myhive.backend.repository.CategoryRepository categoryRepository;

    @InjectMocks private PackageService packageService;

    private Destination destination;
    private Activity activity1;
    private Activity activity2;
    private Package pkg;

    @BeforeEach
    void setUp() {
        destination = TestDataFactory.destination();
        activity1 = TestDataFactory.activity(destination);
        activity1.setPrice(new BigDecimal("100.00"));
        activity2 = TestDataFactory.activity(destination);
        activity2.setPrice(new BigDecimal("200.00"));
        pkg = TestDataFactory.pkg(destination);
        pkg.getPackageActivities().add(new PackageActivity(pkg, activity1, 0));
        pkg.getPackageActivities().add(new PackageActivity(pkg, activity2, 1));
    }

    @Test
    void getBySlugReturnsDtoWithComputedPrices() {
        when(packageRepository.findBySlug("test-package")).thenReturn(Optional.of(pkg));

        PackageDTO dto = packageService.getPackageBySlug("test-package");

        BigDecimal expectedOriginal = new BigDecimal("300.00");
        BigDecimal expectedDiscounted = new BigDecimal("255.00");
        BigDecimal expectedSavings = new BigDecimal("45.00");
        assertThat(dto.getOriginalPrice()).isEqualByComparingTo(expectedOriginal);
        assertThat(dto.getDiscountedPrice()).isEqualByComparingTo(expectedDiscounted);
        assertThat(dto.getSavings()).isEqualByComparingTo(expectedSavings);
        assertThat(dto.getActivities()).hasSize(2);
        assertThat(dto.getActivities().get(0).getPosition()).isEqualTo(0);
    }

    @Test
    void getBySlugThrowsWhenMissing() {
        when(packageRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> packageService.getPackageBySlug("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(packageRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> packageService.getPackageById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 3: Run test, expect compilation failure**

Run: `cd myhive-backend && ./gradlew test --tests '*PackageServiceTest'`
Expected: FAIL with "cannot find symbol PackageService".

- [ ] **Step 4: Create `PackageService` (read operations only for now)**

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.dto.PackageActivityRefDTO;
import com.myhive.backend.dto.PackageDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Package;
import com.myhive.backend.entity.PackageActivity;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.PackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PackageService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PackageRepository packageRepository;
    private final DestinationRepository destinationRepository;
    private final ActivityRepository activityRepository;
    private final CategoryRepository categoryRepository;

    public List<PackageDTO> getAllPackages() {
        return packageRepository.findAll().stream().map(this::toDTO).toList();
    }

    public Page<PackageDTO> getPackagesPaged(Pageable pageable) {
        return packageRepository.findAll(pageable).map(this::toDTO);
    }

    public PackageDTO getPackageById(UUID id) {
        Package p = packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package", id));
        return toDTO(p);
    }

    public PackageDTO getPackageBySlug(String slug) {
        Package p = packageRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));
        return toDTO(p);
    }

    public List<PackageDTO> getPackagesByDestination(UUID destinationId) {
        return packageRepository.findByDestinationId(destinationId).stream().map(this::toDTO).toList();
    }

    public List<PackageDTO> getPackagesByDestinationAndCategorySlug(UUID destinationId, String categorySlug) {
        return packageRepository.findByDestinationIdAndCategoriesSlug(destinationId, categorySlug).stream()
                .map(this::toDTO).toList();
    }

    public List<PackageDTO> getPackagesByCategorySlug(String categorySlug) {
        return packageRepository.findByCategoriesSlug(categorySlug).stream().map(this::toDTO).toList();
    }

    PackageDTO toDTO(Package p) {
        PackageDTO dto = new PackageDTO();
        dto.setId(p.getId());
        dto.setSlug(p.getSlug());
        dto.setDestinationId(p.getDestination().getId());
        dto.setDestinationName(p.getDestination().getName());
        dto.setDestinationSlug(p.getDestination().getSlug());
        dto.setName(p.getName());
        dto.setDescription(p.getDescription());
        dto.setImageUrl(p.getImageUrl());
        dto.setIncludes(p.getIncludes());
        dto.setDuration(p.getDuration());
        dto.setDiscountPct(p.getDiscountPct());

        List<PackageActivityRefDTO> refs = new ArrayList<>();
        for (PackageActivity pa : p.getPackageActivities()) {
            Activity a = pa.getActivity();
            refs.add(new PackageActivityRefDTO(
                    a.getId(), pa.getPosition(),
                    a.getSlug(), a.getName(), a.getPrice(), a.getDuration(), a.getImageUrl()));
        }
        dto.setActivities(refs);

        BigDecimal original = refs.stream()
                .map(PackageActivityRefDTO::getPrice)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discounted = original
                .multiply(HUNDRED.subtract(p.getDiscountPct()))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal savings = original.subtract(discounted);
        dto.setOriginalPrice(original);
        dto.setDiscountedPrice(discounted);
        dto.setSavings(savings);

        List<CategoryDTO> cats = p.getCategories() == null ? new ArrayList<>()
                : p.getCategories().stream()
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .map(c -> new CategoryDTO(c.getId(), c.getName(), c.getSlug()))
                .toList();
        dto.setCategories(cats);
        dto.setCategoryIds(cats.stream().map(CategoryDTO::getId).toList());
        return dto;
    }
}
```

- [ ] **Step 5: Run tests, expect pass**

Run: `cd myhive-backend && ./gradlew test --tests '*PackageServiceTest'`
Expected: PASS, 3 tests green.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/PackageService.java \
        myhive-backend/src/test/java/com/myhive/backend/service/PackageServiceTest.java \
        myhive-backend/src/test/java/com/myhive/backend/TestDataFactory.java
git commit -m "feat: add PackageService read operations with computed prices"
```

---

### Task 1.5: PackageService — create / update / delete

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/PackageService.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/service/PackageServiceTest.java`

- [ ] **Step 1: Add failing test for create with destination-mismatch validation**

Append to `PackageServiceTest`:

```java
    @Test
    void createRejectsActivitiesFromOtherDestination() {
        Destination other = TestDataFactory.destination();
        other.setId(UUID.randomUUID());
        Activity foreign = TestDataFactory.activity(other);

        when(destinationRepository.findById(destination.getId())).thenReturn(Optional.of(destination));
        when(activityRepository.findAllById(List.of(foreign.getId()))).thenReturn(List.of(foreign));

        PackageDTO dto = new PackageDTO();
        dto.setDestinationId(destination.getId());
        dto.setName("New");
        dto.setDiscountPct(new BigDecimal("10.00"));
        dto.setActivities(List.of(new PackageActivityRefDTO(
                foreign.getId(), 0, null, null, null, null, null)));

        assertThatThrownBy(() -> packageService.createPackage(dto))
                .isInstanceOf(com.myhive.backend.exception.BadRequestException.class)
                .hasMessageContaining("destination");
    }

    @Test
    void deleteThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(packageRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> packageService.deletePackage(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
```

- [ ] **Step 2: Run, expect compile failure**

Run: `cd myhive-backend && ./gradlew test --tests '*PackageServiceTest'`
Expected: FAIL — `createPackage` / `deletePackage` not found.

- [ ] **Step 3: Implement create / update / delete in `PackageService`**

Append to the existing class:

```java
    @Transactional
    public PackageDTO createPackage(PackageDTO dto) {
        com.myhive.backend.entity.Destination destination = destinationRepository.findById(dto.getDestinationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination", dto.getDestinationId()));
        Package p = new Package();
        p.setDestination(destination);
        applyDtoToEntity(dto, p);
        p.setSlug(com.myhive.backend.util.SlugUtils.resolveSlug(
                dto.getSlug(), dto.getName(), packageRepository::existsBySlug));
        try {
            return toDTO(packageRepository.save(p));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            p.setSlug(com.myhive.backend.util.SlugUtils.resolveSlug(
                    dto.getSlug(), dto.getName(), packageRepository::existsBySlug));
            return toDTO(packageRepository.save(p));
        }
    }

    @Transactional
    public PackageDTO updatePackage(UUID id, PackageDTO dto) {
        Package p = packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package", id));
        if (dto.getDestinationId() != null && !dto.getDestinationId().equals(p.getDestination().getId())) {
            com.myhive.backend.entity.Destination destination = destinationRepository.findById(dto.getDestinationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination", dto.getDestinationId()));
            p.setDestination(destination);
        }
        boolean updateSlug = com.myhive.backend.util.SlugUtils.needsUpdate(
                dto.getSlug(), p.getSlug(), dto.getName(), p.getName());
        applyDtoToEntity(dto, p);
        if (updateSlug) {
            UUID currentId = id;
            p.setSlug(com.myhive.backend.util.SlugUtils.resolveForUpdate(
                    dto.getSlug(), dto.getName(), p.getSlug(),
                    slug -> packageRepository.findBySlug(slug)
                            .filter(x -> !x.getId().equals(currentId))
                            .isPresent()));
        }
        return toDTO(packageRepository.save(p));
    }

    @Transactional
    public void deletePackage(UUID id) {
        if (!packageRepository.existsById(id)) {
            throw new ResourceNotFoundException("Package", id);
        }
        packageRepository.deleteById(id);
    }

    private void applyDtoToEntity(PackageDTO dto, Package p) {
        p.setName(dto.getName());
        p.setDescription(dto.getDescription());
        p.setImageUrl(dto.getImageUrl());
        p.setIncludes(dto.getIncludes());
        p.setDuration(dto.getDuration());
        p.setDiscountPct(dto.getDiscountPct());
        p.setCategories(resolveCategories(dto.getCategoryIds()));
        applyActivities(dto.getActivities(), p);
    }

    private void applyActivities(List<PackageActivityRefDTO> refs, Package p) {
        if (refs == null) {
            refs = List.of();
        }
        List<UUID> ids = refs.stream().map(PackageActivityRefDTO::getActivityId).toList();
        java.util.Map<UUID, Activity> byId = new java.util.HashMap<>();
        for (Activity a : activityRepository.findAllById(ids)) {
            byId.put(a.getId(), a);
        }
        for (UUID id : ids) {
            Activity a = byId.get(id);
            if (a == null) {
                throw new ResourceNotFoundException("Activity", id);
            }
            if (!a.getDestination().getId().equals(p.getDestination().getId())) {
                throw new com.myhive.backend.exception.BadRequestException(
                        "Activity " + a.getName() + " belongs to another destination than the package");
            }
        }
        p.getPackageActivities().clear();
        for (PackageActivityRefDTO ref : refs) {
            p.getPackageActivities().add(new PackageActivity(p, byId.get(ref.getActivityId()), ref.getPosition()));
        }
    }

    private java.util.Set<Category> resolveCategories(List<UUID> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return new java.util.HashSet<>();
        }
        List<Category> found = categoryRepository.findAllById(categoryIds);
        if (found.size() != categoryIds.size()) {
            java.util.Set<UUID> foundIds = new java.util.HashSet<>();
            for (Category c : found) { foundIds.add(c.getId()); }
            UUID missing = categoryIds.stream().filter(i -> !foundIds.contains(i)).findFirst().orElseThrow();
            throw new ResourceNotFoundException("Category", missing);
        }
        return new java.util.HashSet<>(found);
    }
```

- [ ] **Step 4: Run tests, expect pass**

Run: `cd myhive-backend && ./gradlew test --tests '*PackageServiceTest'`
Expected: PASS, 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/PackageService.java \
        myhive-backend/src/test/java/com/myhive/backend/service/PackageServiceTest.java
git commit -m "feat: add PackageService create/update/delete with destination validation"
```

---

### Task 1.6: Activity deletion guard

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/ActivityService.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/exception/ActivityInUseException.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/exception/GlobalExceptionHandler.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/service/ActivityServiceTest.java`

- [ ] **Step 1: Create new exception**

```java
package com.myhive.backend.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class ActivityInUseException extends RuntimeException {

    private final List<String> packageNames;

    public ActivityInUseException(List<String> packageNames) {
        super("Activity is used in packages: " + String.join(", ", packageNames));
        this.packageNames = packageNames;
    }
}
```

- [ ] **Step 2: Add failing test in `ActivityServiceTest`**

```java
    @Test
    void deleteActivityThrowsWhenUsedInPackages() {
        UUID id = UUID.randomUUID();
        when(activityRepository.existsById(id)).thenReturn(true);
        when(packageRepository.findPackageNamesByActivityId(id))
                .thenReturn(java.util.List.of("Honeymoon Bali", "Adventure Java"));

        assertThatThrownBy(() -> activityService.deleteActivity(id))
                .isInstanceOf(com.myhive.backend.exception.ActivityInUseException.class);
    }
```

Add `@Mock private PackageRepository packageRepository;` to the test class. Adjust `@InjectMocks` if needed (Mockito auto-injects).

- [ ] **Step 3: Run, expect failure**

Run: `cd myhive-backend && ./gradlew test --tests '*ActivityServiceTest.deleteActivityThrowsWhenUsedInPackages'`
Expected: FAIL.

- [ ] **Step 4: Inject `PackageRepository` into `ActivityService` and update `deleteActivity`**

In `ActivityService.java` add field: `private final PackageRepository packageRepository;` (Lombok `@RequiredArgsConstructor` handles injection). Add import.

Change `deleteActivity`:

```java
    @Transactional
    public void deleteActivity(UUID id) {
        if (!activityRepository.existsById(id)) {
            throw new ResourceNotFoundException("Activity", id);
        }
        List<String> usedIn = packageRepository.findPackageNamesByActivityId(id);
        if (!usedIn.isEmpty()) {
            throw new com.myhive.backend.exception.ActivityInUseException(usedIn);
        }
        activityRepository.deleteById(id);
    }
```

- [ ] **Step 5: Add 409 handler in `GlobalExceptionHandler`**

```java
    @ExceptionHandler(ActivityInUseException.class)
    public ResponseEntity<Map<String, Object>> handleActivityInUse(ActivityInUseException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Activity in use");
        body.put("packageNames", ex.getPackageNames());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
```

(Add imports for `ActivityInUseException`, `HashMap`, `Map`, `HttpStatus`, `ResponseEntity` if not already present.)

- [ ] **Step 6: Run tests, expect pass**

Run: `cd myhive-backend && ./gradlew test --tests '*ActivityServiceTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/exception/ActivityInUseException.java \
        myhive-backend/src/main/java/com/myhive/backend/exception/GlobalExceptionHandler.java \
        myhive-backend/src/main/java/com/myhive/backend/service/ActivityService.java \
        myhive-backend/src/test/java/com/myhive/backend/service/ActivityServiceTest.java
git commit -m "feat: forbid activity deletion when used in packages (409)"
```

---

### Task 1.7: PackageController (public endpoints)

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/controller/PackageController.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/controller/PublicControllerIntegrationTest.java`

- [ ] **Step 1: Add failing integration test**

In `PublicControllerIntegrationTest` add (mirroring existing activity tests):

```java
    @Test
    void getPackageBySlugReturnsPackage() throws Exception {
        Destination dest = destinationRepository.save(TestDataFactory.destination());
        Activity act = activityRepository.save(TestDataFactory.activity(dest));
        Package pkg = TestDataFactory.pkg(dest);
        pkg.setSlug("public-pkg");
        pkg.getPackageActivities().add(new PackageActivity(pkg, act, 0));
        packageRepository.save(pkg);

        mockMvc.perform(get("/packages/slug/public-pkg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("public-pkg"))
                .andExpect(jsonPath("$.activities", hasSize(1)));
    }
```

Add `@Autowired private PackageRepository packageRepository;` and necessary imports (`Package`, `PackageActivity`, `hasSize`).

- [ ] **Step 2: Run, expect 404 / failure**

Run: `cd myhive-backend && ./gradlew test --tests '*PublicControllerIntegrationTest.getPackageBySlugReturnsPackage'`
Expected: FAIL.

- [ ] **Step 3: Create controller**

```java
package com.myhive.backend.controller;

import com.myhive.backend.dto.PackageDTO;
import com.myhive.backend.service.PackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/packages")
@RequiredArgsConstructor
public class PackageController {

    private final PackageService packageService;

    @GetMapping
    public ResponseEntity<List<PackageDTO>> getAllPackages(
            @RequestParam(required = false) UUID destinationId,
            @RequestParam(required = false) String categorySlug) {
        if (destinationId != null && categorySlug != null) {
            return ResponseEntity.ok(packageService.getPackagesByDestinationAndCategorySlug(destinationId, categorySlug));
        } else if (destinationId != null) {
            return ResponseEntity.ok(packageService.getPackagesByDestination(destinationId));
        } else if (categorySlug != null) {
            return ResponseEntity.ok(packageService.getPackagesByCategorySlug(categorySlug));
        }
        return ResponseEntity.ok(packageService.getAllPackages());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PackageDTO> getPackageById(@PathVariable UUID id) {
        return ResponseEntity.ok(packageService.getPackageById(id));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<PackageDTO> getPackageBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(packageService.getPackageBySlug(slug));
    }
}
```

- [ ] **Step 4: Add public endpoint to security whitelist**

In `myhive-backend/src/main/java/com/myhive/backend/config/SecurityConfig.java` find the `/activities/**` permitAll block and add `/packages/**` next to it.

- [ ] **Step 5: Run test, expect pass**

Run: `cd myhive-backend && ./gradlew test --tests '*PublicControllerIntegrationTest.getPackageBySlugReturnsPackage'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/controller/PackageController.java \
        myhive-backend/src/main/java/com/myhive/backend/config/SecurityConfig.java \
        myhive-backend/src/test/java/com/myhive/backend/controller/PublicControllerIntegrationTest.java
git commit -m "feat: add public PackageController endpoints"
```

---

### Task 1.8: Admin endpoints for packages

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/controller/AdminController.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/controller/AdminControllerIntegrationTest.java`

- [ ] **Step 1: Add failing tests**

In `AdminControllerIntegrationTest` add tests mirroring activity admin tests:

```java
    @Test
    void adminCreatePackage() throws Exception {
        Destination dest = destinationRepository.save(TestDataFactory.destination());
        Activity act = activityRepository.save(TestDataFactory.activity(dest));
        String body = String.format("""
                {"destinationId":"%s","name":"New Pkg","discountPct":15.00,
                 "activities":[{"activityId":"%s","position":0}]}""",
                dest.getId(), act.getId());

        mockMvc.perform(post("/admin/packages")
                        .with(jwt().jwt(JwtTestHelper.adminJwt()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("new-pkg"));
    }

    @Test
    void adminListPackagesPaged() throws Exception {
        mockMvc.perform(get("/admin/packages/paged")
                        .with(jwt().jwt(JwtTestHelper.adminJwt())))
                .andExpect(status().isOk());
    }
```

- [ ] **Step 2: Add admin endpoints to `AdminController`**

Inject `PackageService packageService` (add to constructor params via existing Lombok approach).

```java
    @GetMapping("/packages")
    public ResponseEntity<List<PackageDTO>> getAllPackages() {
        return ResponseEntity.ok(packageService.getAllPackages());
    }

    @GetMapping("/packages/paged")
    public ResponseEntity<Page<PackageDTO>> getPackagesPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 50), Sort.by("name").ascending());
        return ResponseEntity.ok(packageService.getPackagesPaged(pageRequest));
    }

    @PostMapping("/packages")
    public ResponseEntity<PackageDTO> createPackage(@Valid @RequestBody PackageDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(packageService.createPackage(dto));
    }

    @PutMapping("/packages/{id}")
    public ResponseEntity<PackageDTO> updatePackage(@PathVariable UUID id, @Valid @RequestBody PackageDTO dto) {
        return ResponseEntity.ok(packageService.updatePackage(id, dto));
    }

    @DeleteMapping("/packages/{id}")
    public ResponseEntity<Void> deletePackage(@PathVariable UUID id) {
        packageService.deletePackage(id);
        return ResponseEntity.noContent().build();
    }
```

Add import `com.myhive.backend.dto.PackageDTO` and `com.myhive.backend.service.PackageService`.

- [ ] **Step 3: Run tests**

Run: `cd myhive-backend && ./gradlew test --tests '*AdminControllerIntegrationTest'`
Expected: PASS for the new tests.

- [ ] **Step 4: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/controller/AdminController.java \
        myhive-backend/src/test/java/com/myhive/backend/controller/AdminControllerIntegrationTest.java
git commit -m "feat: add admin CRUD endpoints for packages"
```

---

### Task 1.9: Sitemap + DestinationDTO inclusion

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/controller/SitemapController.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/dto/DestinationDTO.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/DestinationService.java`

- [ ] **Step 1: Add `packages` field to `DestinationDTO`**

```java
    private List<PackageDTO> packages = new ArrayList<>();
```

(Add import.)

- [ ] **Step 2: Update `DestinationService.getDestinationBySlug` (and any "single destination" returns)** to fetch packages and set them on the DTO.

Inject `PackageService packageService` (add to constructor field). In the conversion method that returns the slug-based fetch:

```java
    dto.setPackages(packageService.getPackagesByDestination(d.getId()));
```

Apply only to the single-destination read paths (not the list/page) to avoid N+1 in admin listing.

- [ ] **Step 3: Add packages to sitemap**

In `SitemapController` add a section building `/destination/{destSlug}/package/{slug}` URLs. Inject `PackageService`. Iterate `getAllPackages()` and emit a `<url>` entry for each.

- [ ] **Step 4: Add a sitemap test**

In `myhive-backend/src/test/java/com/myhive/backend/controller/PublicControllerIntegrationTest.java` add a test that saves a destination + a package and asserts the URL `/destination/{destSlug}/package/{slug}` appears in `GET /sitemap.xml`.

- [ ] **Step 5: Run sitemap test**

Run: `cd myhive-backend && ./gradlew test --tests '*Sitemap*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/controller/SitemapController.java \
        myhive-backend/src/main/java/com/myhive/backend/dto/DestinationDTO.java \
        myhive-backend/src/main/java/com/myhive/backend/service/DestinationService.java
git commit -m "feat: include packages in destination DTO and sitemap"
```

---

## Phase 2 — Backend: Booking integration

### Task 2.1: Schema additions on `BookingItem`

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/entity/BookingItem.java`

- [ ] **Step 1: Add fields**

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id")
    private Package pkg;

    @Column(name = "package_name")
    private String packageName;

    @Column(name = "package_discount_pct", precision = 5, scale = 2)
    private java.math.BigDecimal packageDiscountPct;
```

Add `Package` import. Update `@ToString(exclude = ...)` to include `"pkg"`.

- [ ] **Step 2: Compile**

Run: `cd myhive-backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/entity/BookingItem.java
git commit -m "feat: add package snapshot fields to BookingItem"
```

---

### Task 2.2: BookingItemDTO and BookingDTO surface package fields

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/dto/BookingItemDTO.java`

- [ ] **Step 1: Add fields**

```java
    private UUID packageId;
    private String packageName;
    private java.math.BigDecimal packageDiscountPct;
```

- [ ] **Step 2: Update `BookingService.convertItemToDTO` to set them**

```java
        dto.setPackageId(item.getPkg() != null ? item.getPkg().getId() : null);
        dto.setPackageName(item.getPackageName());
        dto.setPackageDiscountPct(item.getPackageDiscountPct());
```

- [ ] **Step 3: Compile + run BookingServiceTest**

Run: `cd myhive-backend && ./gradlew test --tests '*BookingServiceTest'`
Expected: existing tests still PASS.

- [ ] **Step 4: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/BookingItemDTO.java \
        myhive-backend/src/main/java/com/myhive/backend/service/BookingService.java
git commit -m "feat: expose package snapshot fields in BookingItemDTO"
```

---

### Task 2.3: TripExportRequest passthrough for packages

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/dto/TripExportRequest.java`

- [ ] **Step 1: Add fields to `ActivityExport`** (the inner DTO)

```java
        private UUID packageId;
        private String packageName;
        private java.math.BigDecimal packageDiscountPct;
```

- [ ] **Step 2: Compile**

Run: `cd myhive-backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/TripExportRequest.java
git commit -m "feat: TripExportRequest accepts package context per activity"
```

---

### Task 2.4: BookingService stores package snapshot + computes total with discount

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/BookingService.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/service/BookingServiceTest.java`

- [ ] **Step 1: Write failing test for package booking**

Add `@MockBean PackageRepository packageRepository;` if not yet injected, and:

```java
    @Test
    void packageBookingAppliesDiscountToTotal() {
        Destination dest = TestDataFactory.destination();
        Activity a1 = TestDataFactory.activity(dest); a1.setPrice(new BigDecimal("100.00"));
        Activity a2 = TestDataFactory.activity(dest); a2.setPrice(new BigDecimal("200.00"));
        Package pkg = TestDataFactory.pkg(dest);
        pkg.setDiscountPct(new BigDecimal("10.00"));

        when(activityRepository.findById(a1.getId())).thenReturn(Optional.of(a1));
        when(activityRepository.findById(a2.getId())).thenReturn(Optional.of(a2));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        TripExportRequest req = new TripExportRequest();
        req.setUserEmail("a@b.com");
        req.setNumberOfTravelers(1);
        TripExportRequest.DestinationExport de = new TripExportRequest.DestinationExport();
        de.setDestinationName(dest.getName());
        TripExportRequest.ActivityExport ae1 = new TripExportRequest.ActivityExport();
        ae1.setActivityId(a1.getId()); ae1.setActivityName(a1.getName()); ae1.setPrice(100.0);
        ae1.setPackageId(pkg.getId()); ae1.setPackageName(pkg.getName());
        ae1.setPackageDiscountPct(new BigDecimal("10.00"));
        TripExportRequest.ActivityExport ae2 = new TripExportRequest.ActivityExport();
        ae2.setActivityId(a2.getId()); ae2.setActivityName(a2.getName()); ae2.setPrice(200.0);
        ae2.setPackageId(pkg.getId()); ae2.setPackageName(pkg.getName());
        ae2.setPackageDiscountPct(new BigDecimal("10.00"));
        de.setActivities(List.of(ae1, ae2));
        req.setDestinations(List.of(de));

        BookingDTO dto = bookingService.createBookingFromExport(req);

        BigDecimal expectedTotal = new BigDecimal("270.00");
        assertThat(dto.getTotalAmount()).isEqualByComparingTo(expectedTotal);
        assertThat(dto.getItems()).allMatch(i -> pkg.getId().equals(i.getPackageId()));
    }
```

- [ ] **Step 2: Run, expect failure (total wrong / fields not set)**

Run: `cd myhive-backend && ./gradlew test --tests '*BookingServiceTest.packageBookingAppliesDiscountToTotal'`
Expected: FAIL.

- [ ] **Step 3: Update `createBookingFromExport`**

Inject `PackageRepository`. Change the per-item construction to read package fields off the export DTO:

```java
                if (act.getPackageId() != null) {
                    item.setPkg(packageRepository.findById(act.getPackageId()).orElse(null));
                    item.setPackageName(act.getPackageName());
                    item.setPackageDiscountPct(act.getPackageDiscountPct());
                }
```

Replace the in-loop `totalAmount = totalAmount.add(...)` with: defer total calculation until after the loop. Then compute total via grouping:

```java
        booking.setBookingItems(items);
        booking.setTotalAmount(calculateTotal(items));
```

Add helper:

```java
    private BigDecimal calculateTotal(List<BookingItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        java.util.Map<UUID, java.util.List<BookingItem>> grouped = new java.util.LinkedHashMap<>();
        for (BookingItem it : items) {
            UUID key = it.getPkg() != null ? it.getPkg().getId() : null;
            grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(it);
        }
        for (var e : grouped.entrySet()) {
            BigDecimal groupTotal = BigDecimal.ZERO;
            for (BookingItem it : e.getValue()) {
                BigDecimal qty = BigDecimal.valueOf(it.getQuantity() == null ? 1 : it.getQuantity());
                groupTotal = groupTotal.add(it.getPrice().multiply(qty));
            }
            if (e.getKey() != null) {
                BigDecimal pct = e.getValue().get(0).getPackageDiscountPct();
                if (pct == null) { pct = BigDecimal.ZERO; }
                groupTotal = groupTotal.multiply(new BigDecimal("100").subtract(pct))
                        .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
            }
            total = total.add(groupTotal);
        }
        return total;
    }
```

- [ ] **Step 4: Run tests**

Run: `cd myhive-backend && ./gradlew test --tests '*BookingServiceTest'`
Expected: PASS — both new package test and existing tests.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/BookingService.java \
        myhive-backend/src/test/java/com/myhive/backend/service/BookingServiceTest.java
git commit -m "feat: BookingService snapshots package context and applies group discounts"
```

---

### Task 2.5: Email rendering grouped by package

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java`
- Modify: `myhive-backend/src/main/resources/templates/email/itinerary-confirmation.html`

- [ ] **Step 1: Update template to render grouped sections**

Group items by `packageId`. For each non-null package group, render a heading with the package name, list its items, then a discount line and subtotal. Items with `packageId == null` go into a "Standalone activities" section.

Build the grouping in `EmailService` (do not rely on Thymeleaf grouping helpers — keep the template dumb). Add a model attribute like `packageGroups` (list of `{packageName, items, subtotal, discountPct, discountedSubtotal}`) and `standaloneItems`. Update the template to iterate both.

- [ ] **Step 2: Add a unit test**

Test that `EmailService` (or the helper) returns the grouped structure expected by the template. Use `TestDataFactory.pkg(...)` plus snapshot-fields-set `BookingItem` rows.

- [ ] **Step 3: Run all tests**

Run: `cd myhive-backend && ./gradlew test`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java \
        myhive-backend/src/main/resources/templates/email/itinerary-confirmation.html \
        myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java
git commit -m "feat: render booking emails with package grouping and discount line"
```

---

## Phase 3 — Frontend: Admin

### Task 3.1: Admin API client methods for packages

**Files:**
- Modify: `myhive-react-app/src/services/adminApi.js`

- [ ] **Step 1: Add functions** (mirror existing `getActivitiesPaged`, `createActivity`, etc.)

```javascript
async getPackages() { return this._request('GET', '/admin/packages'); },
async getPackagesPaged(page = 0, size = 10) {
    return this._request('GET', `/admin/packages/paged?page=${page}&size=${size}`);
},
async createPackage(payload) { return this._request('POST', '/admin/packages', payload); },
async updatePackage(id, payload) { return this._request('PUT', `/admin/packages/${id}`, payload); },
async deletePackage(id) { return this._request('DELETE', `/admin/packages/${id}`); },
```

(Adapt to the existing pattern in `adminApi.js` — read it before pasting; the field name/structure may differ.)

- [ ] **Step 2: Commit**

```bash
git add myhive-react-app/src/services/adminApi.js
git commit -m "feat(admin): API client for packages"
```

---

### Task 3.2: Install `@dnd-kit/core` for drag-and-drop activity ordering

**Files:**
- Modify: `myhive-react-app/package.json`

- [ ] **Step 1: Install**

Run: `cd myhive-react-app && npm install @dnd-kit/core @dnd-kit/sortable @dnd-kit/utilities`
Expected: dependencies added to `package.json`.

- [ ] **Step 2: Commit**

```bash
git add myhive-react-app/package.json myhive-react-app/package-lock.json
git commit -m "chore(deps): add @dnd-kit for package activity ordering"
```

---

### Task 3.3: PackageActivityPicker component

**Files:**
- Create: `myhive-react-app/src/components/admin/PackageActivityPicker.js`
- Create: `myhive-react-app/src/components/admin/PackageActivityPicker.css`

- [ ] **Step 1: Implement component**

The component takes:
- `value` — array of `{activityId, position, name, price, imageUrl}`
- `onChange(newArray)` — fires with reordered/added/removed list (positions auto-recomputed by index)
- `availableActivities` — array of activities for the chosen destination
- `disabled` — when no destination selected

Render:
- Header with count and "+ Add activity" button
- `<DndContext>` + `<SortableContext>` from `@dnd-kit/core`/`@dnd-kit/sortable` wrapping the rows
- Each row uses `useSortable` and shows: drag handle (☰), thumbnail, name, price, ✕ button
- "Add activity" opens a Bootstrap modal with a search input filtering `availableActivities` (excluding already-added ones); clicking adds and closes

Reference `@dnd-kit/sortable` docs for the standard `SortableContext` + `useSortable` pattern.

- [ ] **Step 2: Manually verify** by running `npm start` and rendering the component on a scratch route — out of scope of this step, but the component is exercised in Task 3.4 below.

- [ ] **Step 3: Commit**

```bash
git add myhive-react-app/src/components/admin/PackageActivityPicker.js \
        myhive-react-app/src/components/admin/PackageActivityPicker.css
git commit -m "feat(admin): PackageActivityPicker with drag-and-drop ordering"
```

---

### Task 3.4: AdminPackages page

**Files:**
- Create: `myhive-react-app/src/pages/AdminPackages.js`

- [ ] **Step 1: Implement page** (mirror `AdminActivities.js` heavily)

- Use the existing `useAdminCrud` hook with package-specific `fetchFn`/`createFn`/`updateFn`/`deleteFn`
- `EMPTY_FORM` includes: `name`, `slug`, `description`, `imageUrl`, `includes`, `duration`, `discountPct`, `categoryIds`, `destinationId`, `activities` (empty array)
- `mapItemToForm` populates `activities` from `item.activities`
- `buildPayload` converts `discountPct` and `duration` to numbers, ensures `activities` has correct shape
- COLUMNS: name, slug, destination, # activities, price ($X / $Y), discount, actions
- Embed `PackageActivityPicker` in the modal form, fed by activities for the selected destination (fetched on destination change via `adminApi.getActivities()` filtered client-side or `getActivitiesByDestination` — pick whichever matches existing API)
- When destination changes and there are activities in the picker, show a confirm dialog using `window.confirm` ("This will clear the activity list. Continue?"); if cancelled, revert select.
- Live price preview block under the picker:
  ```
  Activities total: $X
  Discount (Y%):   −$Z
  Final price:     $T
  ```

- [ ] **Step 2: Add route + nav**

Modify `myhive-react-app/src/App.js`: add `<Route path="packages" element={<AdminPackages/>}/>` after activities.
Modify `myhive-react-app/src/components/AdminLayout.js`: insert `<Nav.Link as={NavLink} to="/admin/packages">Packages</Nav.Link>` between Activities and Categories.

- [ ] **Step 3: Smoke-test**

Start backend (`./gradlew bootRun --args='--spring.profiles.active=dev'`) and frontend (`npm start`). In the browser:
1. Log in as admin
2. Navigate to `/admin/packages`
3. Create a package with 2 activities, drag to reorder, save
4. Reload page; verify ordering persisted
5. Edit and change discount; verify price preview updates
6. Delete the package

If anything fails, fix before committing.

- [ ] **Step 4: Commit**

```bash
git add myhive-react-app/src/pages/AdminPackages.js \
        myhive-react-app/src/App.js \
        myhive-react-app/src/components/AdminLayout.js
git commit -m "feat(admin): AdminPackages CRUD page"
```

---

### Task 3.5: AdminActivities — handle 409 from delete

**Files:**
- Modify: `myhive-react-app/src/pages/AdminActivities.js`
- Modify: `myhive-react-app/src/services/adminApi.js` (if needed for response body parsing)

- [ ] **Step 1: Update delete error handling**

In `AdminActivities.js`, the `useAdminCrud`'s `deleteFn` swallows errors. Override `handleDelete` so a 409 with `packageNames` shows a toast:

```javascript
const onDeleteAttempt = async (id) => {
    try {
        await adminApi.deleteActivity(id);
        await fetchData();
        setDeleteId(null);
    } catch (e) {
        if (e?.status === 409 && Array.isArray(e?.body?.packageNames)) {
            setError(`Cannot delete: used in packages: ${e.body.packageNames.join(', ')}`);
        } else {
            setError(e.message || 'Failed to delete activity');
        }
        setDeleteId(null);
    }
};
```

If the existing `adminApi` request helper doesn't expose `status`/`body` on errors, modify it to attach them on non-2xx responses (small, surgical change in `_request`).

- [ ] **Step 2: Smoke-test**

Try to delete an activity that's used in a package; verify the error message appears.

- [ ] **Step 3: Commit**

```bash
git add myhive-react-app/src/pages/AdminActivities.js myhive-react-app/src/services/adminApi.js
git commit -m "feat(admin): show 409 packageNames when activity delete blocked"
```

---

## Phase 4 — Frontend: Public

### Task 4.1: Public API client methods

**Files:**
- Modify: `myhive-react-app/src/services/api.js`

- [ ] **Step 1: Add methods**

```javascript
async getPackagesByDestination(destinationId) {
    return this._request('GET', `/packages?destinationId=${destinationId}`);
},
async getPackageBySlug(slug) {
    return this._request('GET', `/packages/slug/${slug}`);
},
```

(Adapt to the actual pattern used in `api.js`.)

- [ ] **Step 2: Commit**

```bash
git add myhive-react-app/src/services/api.js
git commit -m "feat: public API client for packages"
```

---

### Task 4.2: Replace legacy PackageCard with real-data version

**Files:**
- Modify: `myhive-react-app/src/components/PackageCard.js`
- Modify: `myhive-react-app/src/components/PackageCard.css`
- Modify: `myhive-react-app/src/context/AppContext.js` (remove `packages` state and `SELECT_PACKAGE` reducer case if dead)

- [ ] **Step 1: Rewrite `PackageCard`**

```javascript
import { Link } from 'react-router-dom';
import './PackageCard.css';

function PackageCard({ pkg }) {
    return (
        <Link to={`/destination/${pkg.destinationSlug}/package/${pkg.slug}`} className="card package-card">
            {pkg.imageUrl && (
                <img src={pkg.imageUrl} alt={pkg.name} className="package-image" loading="lazy" />
            )}
            <div className="package-content">
                <h3 className="package-title">{pkg.name}</h3>
                {pkg.description && <p className="package-description">{pkg.description}</p>}
                <div className="package-pricing">
                    <span className="package-original">${pkg.originalPrice}</span>
                    <span className="package-discounted">${pkg.discountedPrice}</span>
                    {Number(pkg.savings) > 0 && (
                        <span className="package-savings">Save ${pkg.savings}</span>
                    )}
                </div>
            </div>
        </Link>
    );
}

export default PackageCard;
```

- [ ] **Step 2: Update CSS** to style `.package-original` (line-through), `.package-discounted` (bold), `.package-savings` (green badge).

- [ ] **Step 3: Remove dead state from `AppContext.js`**

Delete `packages: []` from initial state and the `case 'SET_PACKAGES'` / `case 'SELECT_PACKAGE'` branches (verify they're truly unused first via Grep — only `PackageCard` was using `SELECT_PACKAGE` and we just replaced it).

- [ ] **Step 4: Smoke-test by visiting a destination page** (will fully integrate in 4.3 below).

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/components/PackageCard.js \
        myhive-react-app/src/components/PackageCard.css \
        myhive-react-app/src/context/AppContext.js
git commit -m "refactor(frontend): replace legacy PackageCard with real-data version"
```

---

### Task 4.3: DestinationPage — render real packages

**Files:**
- Modify: `myhive-react-app/src/pages/DestinationPage.js`

- [ ] **Step 1: Fetch packages** alongside the existing destination/activities fetch (use new `getPackagesByDestination` or read `destination.packages` if backend includes them in `GET /destinations/slug/{slug}` per Task 1.9).

- [ ] **Step 2: Replace the empty `state.packages` reference** in the Packages tab with the new local `packages` state, mapping each through `PackageCard`.

- [ ] **Step 3: Hide the Packages tab** when `packages.length === 0`.

- [ ] **Step 4: Smoke-test**

Create a package via admin, visit the destination page on the public site, verify the Packages tab shows the card with correct discounted price and "Save $X" badge.

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/pages/DestinationPage.js
git commit -m "feat: DestinationPage renders real packages from API"
```

---

### Task 4.4: PackageDetailPage and route

**Files:**
- Create: `myhive-react-app/src/pages/PackageDetailPage.js`
- Create: `myhive-react-app/src/pages/PackageDetailPage.css`
- Modify: `myhive-react-app/src/components/Layout.js` (or wherever public routes are defined)

- [ ] **Step 1: Find public route definitions**

Run: Grep for `Routes` or `Route path` in `myhive-react-app/src/components/Layout.js` to confirm the file. If routes live elsewhere, locate them.

- [ ] **Step 2: Add route**

```jsx
<Route path="/destination/:destSlug/package/:slug" element={<PackageDetailPage/>}/>
```

- [ ] **Step 3: Implement `PackageDetailPage`**

Structure:
- `useParams()` → `{destSlug, slug}`
- `useEffect` fetches via `api.getPackageBySlug(slug)`
- Render hero (image + name), description, breadcrumbs (Home › destinationName › packageName)
- Section "What's included" — `pkg.activities` rendered as cards (use existing `ActivityCard` if its props match, otherwise inline minimal card with image/name/duration/price). Each card links to `/destination/{destSlug}/activity/{activity.slug}`
- "Includes" text block (if `pkg.includes`)
- Sticky right-side price card:
  ```
  Original price:  ${pkg.originalPrice}
  You save:        ${pkg.savings}  [green badge]
  Package price:   ${pkg.discountedPrice}
  [ Add to trip ]
  ```
- "Add to trip" button dispatches `ADD_PACKAGE_TO_TRIP` (added in 4.5)
- `<Helmet>` for SEO: title `${pkg.name} — ${destinationName} Package | Trivlu`, meta description (first 160 chars of `description`), canonical URL using `REACT_APP_SITE_URL`

- [ ] **Step 4: Smoke-test**

Visit `/destination/{slug}/package/{slug}` on the public site; verify all sections render correctly.

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/pages/PackageDetailPage.js \
        myhive-react-app/src/pages/PackageDetailPage.css \
        myhive-react-app/src/components/Layout.js
git commit -m "feat: public PackageDetailPage and route"
```

---

### Task 4.5: TripBuilder — package support

**Files:**
- Modify: `myhive-react-app/src/context/AppContext.js`
- Modify: `myhive-react-app/src/components/TripBuilder.js`

- [ ] **Step 1: Add reducer cases**

```javascript
case 'ADD_PACKAGE_TO_TRIP': {
    const pkg = action.pkg;
    const newItems = pkg.activities.map(a => ({
        id: a.activityId,
        name: a.name,
        price: a.price,
        imageUrl: a.imageUrl,
        duration: a.duration,
        packageId: pkg.id,
        packageName: pkg.name,
        packageDiscountPct: pkg.discountPct,
    }));
    const without = state.tripItems.filter(i => !newItems.some(n => n.id === i.id));
    return { ...state, tripItems: [...without, ...newItems] };
}
case 'REMOVE_PACKAGE_FROM_TRIP':
    return {
        ...state,
        tripItems: state.tripItems.filter(i => i.packageId !== action.packageId),
    };
```

- [ ] **Step 2: Update TripBuilder rendering** to group items by `packageId` (null = standalone). Each package group shows:
- A header bar with the package name and one ✕ button (dispatches `REMOVE_PACKAGE_FROM_TRIP`)
- Indented activity rows that *cannot* be removed individually (hide the per-item ✕ when `item.packageId` is set)
- Standalone items render as today

- [ ] **Step 3: Update total** to apply package discount per group (mirror backend logic):

```javascript
const totalPrice = (() => {
    const groups = new Map();
    state.tripItems.forEach(it => {
        const key = it.packageId || null;
        if (!groups.has(key)) { groups.set(key, []); }
        groups.get(key).push(it);
    });
    let total = 0;
    for (const [key, items] of groups) {
        let sub = items.reduce((s, it) => s + (Number(it.price) || 0) * travelers, 0);
        if (key) {
            const pct = Number(items[0].packageDiscountPct) || 0;
            sub = sub * (100 - pct) / 100;
        }
        total += sub;
    }
    return Math.round(total * 100) / 100;
})();
```

- [ ] **Step 4: Pass package fields through booking submission**

In `handleContactSubmit`'s `bookingData.destinations[0].activities` mapping:

```javascript
activities: state.tripItems.map(item => ({
    activityId: item.id,
    activityName: item.name,
    // ... existing fields ...
    packageId: item.packageId || null,
    packageName: item.packageName || null,
    packageDiscountPct: item.packageDiscountPct || null,
})),
```

- [ ] **Step 5: Add "Add Package" button in TripBuilder** that opens a modal listing packages (use `api.getPackagesByDestination` — but only if a destination is selected; if not, list all via `api.getAllPackages` if you add it). On click, dispatches `ADD_PACKAGE_TO_TRIP`.

For the simplest path: keep this scoped — if there's no clean "active destination" in TripBuilder, defer this button to the package detail page only (where "Add to trip" is already implemented in 4.4). Skip the in-TripBuilder modal in this iteration; YAGNI.

- [ ] **Step 6: Smoke-test end-to-end**

1. Browse to a package detail page
2. Click "Add to trip"
3. Open trip builder, verify package group shows correctly with discount applied
4. Click ✕ on the package group, verify all activities removed at once
5. Add the package again, confirm a booking, check email/admin for grouped display

- [ ] **Step 7: Commit**

```bash
git add myhive-react-app/src/context/AppContext.js myhive-react-app/src/components/TripBuilder.js
git commit -m "feat: TripBuilder supports packages with group discount and remove-as-unit"
```

---

### Task 4.6: Update CLAUDE.md and memory

**Files:**
- Modify: `CLAUDE.md`
- Modify: `C:\Users\dijtb\.claude\projects\C--Users-dijtb-IdeaProjects-myhive-travel-app\memory\project_overview.md`

- [ ] **Step 1: Add package endpoints / entity to CLAUDE.md** under the architecture section (Backend Structure → entity, Public vs Admin endpoints).

- [ ] **Step 2: Update memory** with the new entity / endpoints info.

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: document Package entity and endpoints"
```

---

## Final verification

- [ ] **Run all backend tests**

Run: `cd myhive-backend && ./gradlew test`
Expected: ALL tests PASS.

- [ ] **Run frontend tests**

Run: `cd myhive-react-app && npm test -- --watchAll=false`
Expected: ALL tests PASS.

- [ ] **Run frontend production build**

Run: `cd myhive-react-app && npm run build`
Expected: build succeeds without warnings introduced by this work.

- [ ] **Code review**

Per CLAUDE.md, perform a code review of the entire change set before considering done. Use the `superpowers:requesting-code-review` skill.
