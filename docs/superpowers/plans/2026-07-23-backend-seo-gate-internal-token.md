# Backend: SEO Readiness Gate + Internal API Token Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-record `seoIndexable` flag to Destination/Activity/Package/BlogPost and a server-to-server rate-limit exemption via a shared `X-Internal-Token` header.

**Architecture:** The flag is stored raw on each entity (`boolean`, default `false`) and exposed/accepted through the existing manual DTO mapping in the services; the parent-child rule ("a child cannot be indexable when its destination is not") is enforced consumer-side in the Next app, NOT here — the backend stores editorial intent only. The rate-limit exemption is a constant-time header comparison in `RateLimitFilter`, active only when the token property is configured non-blank.

**Tech Stack:** Spring Boot (Gradle), JPA/Hibernate (`ddl-auto=update` in prod — new columns self-apply on deploy, no migration files exist in this repo), Lombok, JUnit 5 + Mockito + `@DataJpaTest` (H2).

## Global Constraints

- Branch: `feat/seo-gate-internal-token` off `main` (this PR must merge and deploy BEFORE the Next.js cutover PR relies on it).
- Flag name is exactly `seoIndexable` (JSON), column `seo_indexable`, default `false` for new AND existing records.
- Entity field pattern (copy of `Activity.featured`): `@Column(nullable = false, columnDefinition = "boolean default false") private boolean seoIndexable = false;`
- DTO field is the nullable wrapper `Boolean seoIndexable`; entity write uses `Boolean.TRUE.equals(dto.getSeoIndexable())`.
- Header name is exactly `X-Internal-Token`; property `internal.api.token` (Spring maps the Render env var `INTERNAL_API_TOKEN` to it). Blank/unset property = feature off, header ignored.
- All commands run from `/Users/olga/PycharmProjects/myhive-travel-app/myhive-backend`; tests via `./gradlew test`.

---

### Task 1: Rate-limit exemption for internal traffic

**Files:**
- Modify: `src/main/java/com/myhive/backend/config/RateLimitFilter.java`
- Test: `src/test/java/com/myhive/backend/config/RateLimitFilterTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `RateLimitFilter(String internalToken)` constructor (`@Value("${internal.api.token:}")`); requests carrying header `X-Internal-Token: <token>` bypass the per-IP counter when the property is configured.

- [ ] **Step 1: Write the failing tests**

In `RateLimitFilterTest.java`, change setup to pass a token and add three tests. The existing `setUp` creates `filter = new RateLimitFilter();` — change it to:

```java
    private static final String INTERNAL_TOKEN = "test-internal-token";

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(INTERNAL_TOKEN);
    }
```

Add tests (follow the file's existing mock style — `@Mock HttpServletRequest request` etc., `when(...)`, `verify(...)`):

```java
    @Test
    void doFilter_matchingInternalToken_bypassesRateLimit() throws Exception {
        when(request.getHeader("X-Internal-Token")).thenReturn(INTERNAL_TOKEN);
        for (int i = 0; i < 150; i++) {
            filter.doFilter(request, response, chain);
        }
        verify(chain, times(150)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void doFilter_wrongInternalToken_isRateLimitedNormally() throws Exception {
        when(request.getHeader("X-Internal-Token")).thenReturn("wrong-token");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        for (int i = 0; i < 101; i++) {
            filter.doFilter(request, response, chain);
        }
        verify(chain, times(100)).doFilter(request, response);
        verify(response).setStatus(429);
    }

    @Test
    void doFilter_blankConfiguredToken_ignoresHeader() throws Exception {
        RateLimitFilter unconfigured = new RateLimitFilter("");
        when(request.getHeader("X-Internal-Token")).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");
        for (int i = 0; i < 101; i++) {
            unconfigured.doFilter(request, response, chain);
        }
        verify(chain, times(100)).doFilter(request, response);
        verify(response).setStatus(429);
    }
```

Note: existing tests use whatever mock-field names the file already declares (`request`, `response`, `chain` or similar) and may stub `getServletPath()`/`getMethod()` — mirror the file's existing stubbing so the new tests reach the rate-limit branch (method must not be the webhook POST). Adjust stub names to match; do not restructure existing tests beyond the constructor call.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests '*RateLimitFilterTest*'`
Expected: COMPILE FAILURE — `RateLimitFilter()` has no such constructor / new tests fail.

- [ ] **Step 3: Implement the exemption**

In `RateLimitFilter.java` add imports `java.nio.charset.StandardCharsets`, `java.security.MessageDigest`, `org.springframework.beans.factory.annotation.Value`, then:

```java
    /** Shared secret for server-to-server callers (the Next SSR service). Cold ISR fills render
     *  the whole catalog from one egress IP and would exhaust the per-IP bucket; a matching
     *  X-Internal-Token bypasses the counter. Blank (default) disables the exemption. */
    private final byte[] internalToken;

    public RateLimitFilter(@Value("${internal.api.token:}") String internalToken) {
        this.internalToken = internalToken == null || internalToken.isBlank()
                ? null
                : internalToken.getBytes(StandardCharsets.UTF_8);
    }

    private boolean isInternalRequest(HttpServletRequest request) {
        if (internalToken == null) {
            return false;
        }
        String header = request.getHeader("X-Internal-Token");
        return header != null
                && MessageDigest.isEqual(internalToken, header.getBytes(StandardCharsets.UTF_8));
    }
```

In `doFilter`, immediately after the webhook exemption block (after its `return`), add:

```java
        if (isInternalRequest(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }
```

- [ ] **Step 4: Run the test class, verify PASS**

Run: `./gradlew test --tests '*RateLimitFilterTest*'`
Expected: BUILD SUCCESSFUL, all tests (old + 3 new) pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/myhive/backend/config/RateLimitFilter.java src/test/java/com/myhive/backend/config/RateLimitFilterTest.java
git commit -m "feat(backend): X-Internal-Token rate-limit exemption for SSR traffic"
```

---

### Task 2: `seoIndexable` on BlogPost

**Files:**
- Modify: `src/main/java/com/myhive/backend/entity/BlogPost.java`
- Modify: `src/main/java/com/myhive/backend/dto/BlogPostDTO.java`
- Modify: `src/main/java/com/myhive/backend/service/BlogPostService.java` (`convertToDTO` ~line 82, `applyDtoToEntity` ~line 73)
- Test: create `src/test/java/com/myhive/backend/service/BlogPostServiceSeoFlagTest.java`

**Interfaces:**
- Produces: `BlogPostDTO.getSeoIndexable(): Boolean` on all read endpoints; admin create/update accept `seoIndexable` in the JSON body.

- [ ] **Step 1: Write the failing test**

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.BlogPostDTO;
import com.myhive.backend.entity.BlogPost;
import com.myhive.backend.repository.BlogPostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(BlogPostService.class)
class BlogPostServiceSeoFlagTest {

    @Autowired
    private BlogPostService service;
    @Autowired
    private BlogPostRepository repository;

    private BlogPost savedPost() {
        BlogPost post = new BlogPost();
        post.setSlug("seo-flag-post");
        post.setTitle("Seo flag post");
        post.setContent("Body");
        return repository.save(post);
    }

    @Test
    void defaultsToNotIndexableAndRoundTripsThroughUpdate() {
        BlogPost post = savedPost();

        BlogPostDTO dto = service.getBlogPostBySlug("seo-flag-post");
        assertThat(dto.getSeoIndexable()).isFalse();

        dto.setSeoIndexable(true);
        BlogPostDTO updated = service.updateBlogPost(post.getId(), dto);
        assertThat(updated.getSeoIndexable()).isTrue();
        assertThat(repository.findById(post.getId()).orElseThrow().isSeoIndexable()).isTrue();

        updated.setSeoIndexable(null); // absent flag in payload must mean false, not "keep"
        assertThat(service.updateBlogPost(post.getId(), updated).getSeoIndexable()).isFalse();
    }
}
```

(If `BlogPost` requires other non-null fields at insert, set them in `savedPost()` the same way — check entity annotations; per current entity only `slug`/`title` are constrained, `content` is TEXT nullable.)

- [ ] **Step 2: Run it, verify FAIL** — `./gradlew test --tests '*BlogPostServiceSeoFlagTest*'` → compile error (no `seoIndexable` members).

- [ ] **Step 3: Implement**

`BlogPost.java` — next to the other columns:

```java
    @Column(name = "seo_indexable", nullable = false, columnDefinition = "boolean default false")
    private boolean seoIndexable = false;
```

`BlogPostDTO.java`:

```java
    private Boolean seoIndexable;
```

`BlogPostService.java` — in `applyDtoToEntity` add `blogPost.setSeoIndexable(Boolean.TRUE.equals(dto.getSeoIndexable()));` and in `convertToDTO` add `dto.setSeoIndexable(blogPost.isSeoIndexable());`.

- [ ] **Step 4: Run it, verify PASS** — `./gradlew test --tests '*BlogPostServiceSeoFlagTest*'`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/myhive/backend/entity/BlogPost.java src/main/java/com/myhive/backend/dto/BlogPostDTO.java src/main/java/com/myhive/backend/service/BlogPostService.java src/test/java/com/myhive/backend/service/BlogPostServiceSeoFlagTest.java
git commit -m "feat(backend): seoIndexable flag on blog posts"
```

---

### Task 3: `seoIndexable` on Destination

**Files:**
- Modify: `src/main/java/com/myhive/backend/entity/Destination.java`, `dto/DestinationDTO.java`, `service/DestinationService.java`
- Test: create `src/test/java/com/myhive/backend/service/DestinationServiceSeoFlagTest.java`

**Interfaces:**
- Produces: `DestinationDTO.getSeoIndexable(): Boolean` (public `/destinations*` reads + admin writes).

- [ ] **Step 1: Write the failing test** — same shape as Task 2, adapted:

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.DestinationDTO;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(DestinationService.class)
class DestinationServiceSeoFlagTest {

    @Autowired
    private DestinationService service;
    @Autowired
    private DestinationRepository repository;

    @Test
    void defaultsToNotIndexableAndRoundTripsThroughUpdate() {
        Destination dest = new Destination();
        dest.setSlug("seo-city");
        dest.setName("Seo City");
        repository.save(dest);

        DestinationDTO dto = service.getDestinationBySlug("seo-city");
        assertThat(dto.getSeoIndexable()).isFalse();

        dto.setSeoIndexable(true);
        DestinationDTO updated = service.updateDestination(dest.getId(), dto);
        assertThat(updated.getSeoIndexable()).isTrue();
        assertThat(repository.findById(dest.getId()).orElseThrow().isSeoIndexable()).isTrue();
    }
}
```

(Set any additional non-null Destination fields the entity requires — check `@Column(nullable = false)` annotations and fill accordingly, e.g. `country`/`city` if constrained. If `DestinationService` has more constructor repos, `@DataJpaTest` provides them; if it depends on non-repository beans, add them to `@Import`.)

- [ ] **Step 2: Run, verify FAIL** — `./gradlew test --tests '*DestinationServiceSeoFlagTest*'`

- [ ] **Step 3: Implement** — same three edits as Task 2, on Destination entity (`isSeoIndexable`), `DestinationDTO` (`Boolean seoIndexable`), and `DestinationService`'s `convertToDTO`/`applyDtoToEntity` (if the service inlines mapping in create/update instead of a shared `applyDtoToEntity`, add the setter line in BOTH create and update paths).

- [ ] **Step 4: Run, verify PASS.**

- [ ] **Step 5: Commit** — `git commit -m "feat(backend): seoIndexable flag on destinations"` (add the four files).

---

### Task 4: `seoIndexable` on Activity

**Files:**
- Modify: `entity/Activity.java`, `dto/ActivityDTO.java`, `service/ActivityService.java` (`convertToDTO` ~line 158, `applyDtoToEntity` ~line 144)
- Test: create `src/test/java/com/myhive/backend/service/ActivityServiceSeoFlagTest.java`

**Interfaces:**
- Produces: `ActivityDTO.getSeoIndexable(): Boolean`.

- [ ] **Step 1: Write the failing test**

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.ActivityDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ActivityService.class)
class ActivityServiceSeoFlagTest {

    @Autowired
    private ActivityService service;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private DestinationRepository destinationRepository;

    @Test
    void defaultsToNotIndexableAndRoundTripsThroughUpdate() {
        Destination dest = new Destination();
        dest.setSlug("seo-city");
        dest.setName("Seo City");
        destinationRepository.save(dest);

        Activity activity = new Activity();
        activity.setDestination(dest);
        activity.setSlug("seo-activity");
        activity.setName("Seo Activity");
        activity.setPrice(BigDecimal.TEN);
        activityRepository.save(activity);

        ActivityDTO dto = service.getActivityBySlug("seo-activity");
        assertThat(dto.getSeoIndexable()).isFalse();

        dto.setSeoIndexable(true);
        ActivityDTO updated = service.updateActivity(activity.getId(), dto);
        assertThat(updated.getSeoIndexable()).isTrue();
        assertThat(activityRepository.findById(activity.getId()).orElseThrow().isSeoIndexable()).isTrue();
    }
}
```

- [ ] **Step 2: Run, verify FAIL.** `./gradlew test --tests '*ActivityServiceSeoFlagTest*'`
- [ ] **Step 3: Implement** — entity column (pattern from Global Constraints, placed next to `featured`), DTO `Boolean seoIndexable`, `convertToDTO` + `applyDtoToEntity` lines as in Task 2.
- [ ] **Step 4: Run, verify PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(backend): seoIndexable flag on activities"`.

---

### Task 5: `seoIndexable` on Package

**Files:**
- Modify: `entity/Package.java`, `dto/PackageDTO.java`, `service/PackageService.java`
- Test: create `src/test/java/com/myhive/backend/service/PackageServiceSeoFlagTest.java`

**Interfaces:**
- Produces: `PackageDTO.getSeoIndexable(): Boolean`.

- [ ] **Step 1: Write the failing test** — same get→flip→update pattern; build the entity directly to avoid hand-assembling the activities/discount DTO graph:

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.PackageDTO;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.Package;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.PackageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PackageService.class)
class PackageServiceSeoFlagTest {

    @Autowired
    private PackageService service;
    @Autowired
    private PackageRepository packageRepository;
    @Autowired
    private DestinationRepository destinationRepository;

    @Test
    void defaultsToNotIndexableAndRoundTripsThroughUpdate() {
        Destination dest = new Destination();
        dest.setSlug("seo-city");
        dest.setName("Seo City");
        destinationRepository.save(dest);

        Package pkg = new Package();
        pkg.setDestination(dest);
        pkg.setSlug("seo-package");
        pkg.setName("Seo Package");
        pkg.setDiscountPct(BigDecimal.TEN);
        packageRepository.save(pkg);

        PackageDTO dto = service.getPackageBySlug("seo-package");
        assertThat(dto.getSeoIndexable()).isFalse();

        dto.setSeoIndexable(true);
        PackageDTO updated = service.updatePackage(pkg.getId(), dto);
        assertThat(updated.getSeoIndexable()).isTrue();
        assertThat(packageRepository.findById(pkg.getId()).orElseThrow().isSeoIndexable()).isTrue();
    }
}
```

(If `PackageService` imports additional service/repo beans, extend `@Import` accordingly. If `updatePackage` rejects an empty `activities` list, add one activity ref to the DTO exactly as `convertToDTO` returned it — the round-trip DTO already carries whatever update needs.)

- [ ] **Step 2: Run, verify FAIL.** `./gradlew test --tests '*PackageServiceSeoFlagTest*'`
- [ ] **Step 3: Implement** — entity column, DTO field, `convertToDTO`/`applyDtoToEntity` lines, same as previous tasks.
- [ ] **Step 4: Run, verify PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(backend): seoIndexable flag on packages"`.

---

### Task 6: Full verification + PR

**Files:** none new.

- [ ] **Step 1: Full test suite** — `./gradlew test` → BUILD SUCCESSFUL, zero failures.
- [ ] **Step 2: Boot check** — `./gradlew bootRun --args='--spring.profiles.active=dev'`, then in another shell: `curl -s http://localhost:8080/blog | head -c 400` → JSON items contain `"seoIndexable":false`. Stop bootRun.
- [ ] **Step 3: Push and open PR**

```bash
git push -u origin feat/seo-gate-internal-token
gh pr create --base main --title "Backend: seoIndexable gate + internal-token rate-limit exemption" --body "$(cat <<'EOF'
- Per-record seoIndexable flag (default false) on destinations, activities, packages, blog posts; exposed on public reads, writable via admin CRUD. Prod ddl-auto=update adds the columns on deploy.
- X-Internal-Token rate-limit exemption for the Next SSR service (INTERNAL_API_TOKEN env, blank = off, constant-time compare).
- Parent-child indexability rule is enforced by the Next consumer, not stored here.

Deploy notes: set INTERNAL_API_TOKEN on the backend Render service (same value as the Next service).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

