# Vote Organizer Email Screen + Progress Emails — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Collect the organizer's email as step 2 of the "Create vote" modal (session is created only with a valid address), then email the organizer at the halfway mark, 12 h before close, and at close.

**Architecture:** The CRA `StartGroupVoteModal` (single session-creation call site for QUIZ and CART) gains an email step before the create request. The backend stores `email_captured_at` plus two one-shot markers on `vote_sessions`; a new `VoteProgressNotifier` service, driven by the existing 5-minute `VoteSessionScheduler` tick, sends the two progress emails idempotently. The existing result email gets the ticket's subject and CTA. The About page gets the legal-entity block.

**Tech Stack:** Spring Boot 4 / Java 25 / Gradle (JUnit 5, Mockito, H2, Thymeleaf, `messages*.properties`), React 19 CRA (Jest + RTL, hand-rolled `useT` i18n with `en.json`/`de.json`).

**Spec:** `docs/superpowers/specs/2026-09-02-vote-organizer-email-screen-design.md`

## Global Constraints

- Work on branch `feat/vote-organizer-email` (create it from `main` before Task 1; use the `superpowers:using-git-worktrees` skill if isolation is wanted).
- Google Java Style per `CLAUDE.md`: no wildcard imports, `@Override` always, one variable per declaration, braces on every `if`/`for`, constants `UPPER_SNAKE_CASE`, modifiers in standard order.
- Test style per `CLAUDE.md`: `expected`-prefixed variables for values that appear in both arrange and assert; DTOs built inline when exact values matter.
- `initiatorEmail` stays **optional** on the backend (`@Email`, nullable). The UI is the only gate.
- New DB columns are nullable and added by Hibernate `ddl-auto=update`; **no Flyway migration**.
- Every user-facing string goes through i18n: `en.json` + `de.json` for the CRA, `messages.properties` + `messages_de.properties` for emails. Never hardcode copy in JSX or templates.
- `messages*.properties` values that take `{n}` arguments go through `MessageFormat`: a literal apostrophe in such a value must be doubled (`haven''t`).
- Analytics: `pushEvent` from `utils/analytics.js` only; no PII in event params. `vote_launched` payload must not change (GTM trigger).
- CRA Jest config has `resetMocks: true`: set `mockResolvedValue`/`mockRejectedValue` inside each test, never at module level.
- Commands: backend from `myhive-backend` (`./gradlew test --tests '<pattern>'`), frontend from `myhive-react-app` (`npm test -- --watchAll=false <path>`).
- End every commit message with:
  ```
  Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_015w8uPKzE2EWW7ZFBfByfG1
  ```

---

## File Structure

| Path | Responsibility |
|---|---|
| `myhive-backend/src/main/java/com/myhive/backend/entity/VoteSession.java` | + `emailCapturedAt`, `halfwayEmailSentAt`, `reminderEmailSentAt` |
| `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java` | `newSession` normalises the email and stamps `emailCapturedAt`; ranking comparator moves out to `VoteRanking` |
| `myhive-backend/src/main/java/com/myhive/backend/service/VoteRanking.java` (new) | Package-private like-count comparator shared by tally, frozen result and the halfway email |
| `myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java` | + `sendVoteHalfway`, `sendVoteReminder`, `VoteStandingView`, URL helpers |
| `myhive-backend/src/main/resources/templates/email/vote-halfway.html` (new) | Email 1 |
| `myhive-backend/src/main/resources/templates/email/vote-reminder.html` (new) | Email 2 |
| `myhive-backend/src/main/resources/messages.properties`, `messages_de.properties` | New keys + result subject/CTA change |
| `myhive-backend/src/main/java/com/myhive/backend/repository/VoteSessionRepository.java` | Two candidate queries |
| `myhive-backend/src/main/java/com/myhive/backend/service/VoteProgressNotifier.java` (new) | Rules + idempotent sending of emails 1 and 2 |
| `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionScheduler.java` | New 5-minute tick, gating flags |
| `myhive-backend/src/main/resources/application.properties` | `app.vote.organizer-emails-enabled` |
| `myhive-backend/src/test/java/com/myhive/backend/config/MockEmailServiceConfig.java` (new) | Shared `@Primary` mocked `EmailService` for Spring tests |
| `myhive-backend/src/test/java/com/myhive/backend/service/EmailTemplateTestSupport.java` (new) | Shared Thymeleaf engine for template render tests |
| `myhive-react-app/src/utils/validators.js` | + `emailFormat` (regex moved from `useEmailLeadCapture`) |
| `myhive-react-app/src/components/vote/StartGroupVoteModal.js`, `.css` | Two-step modal |
| `myhive-react-app/src/i18n/messages/en.json`, `de.json` | `voteComponents.start.email.*`, `about.company.*` |
| `myhive-react-app/src/pages/AboutPage.js`, `.css` | Company block |

---

### Task 1: `VoteSession` columns + `emailCapturedAt` on creation

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/entity/VoteSession.java` (after the `expiresAt` field)
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java:157-174` (`newSession`)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionCreateSessionTest.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionCartCreateTest.java`

**Interfaces:**
- Produces: `VoteSession.getEmailCapturedAt()`, `getHalfwayEmailSentAt()`/`setHalfwayEmailSentAt(LocalDateTime)`, `getReminderEmailSentAt()`/`setReminderEmailSentAt(LocalDateTime)` (Lombok accessors). `VoteSessionService` stores a trimmed email, blank → `null`.

- [ ] **Step 1: Write the failing tests**

Add to `VoteSessionCreateSessionTest` (the file's existing helpers `baseRequest`, `newDestination`, `newCategory`, `attachCategory`, `newActivity` are at lines 224-266):

```java
    @Test
    void createSession_storesTrimmedEmailAndCaptureTime() {
        String expectedEmail = "organizer@example.com";
        Destination destination = newDestination("Prague");
        Category nightlife = newCategory("Nightlife", "nightlife");
        attachCategory(destination, nightlife);
        Activity activity = newActivity(destination, "Tank Driving", new BigDecimal("150.00"), Set.of(nightlife));
        VoteSessionCreateRequest request = baseRequest(destination.getId());
        request.setInitiatorEmail("  " + expectedEmail + "  ");
        request.setActivityIds(List.of(activity.getId()));

        VoteSessionResponse response = voteSessionService.createSession(request);

        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        assertThat(session.getInitiatorEmail()).isEqualTo(expectedEmail);
        assertThat(session.getEmailCapturedAt()).isNotNull();
    }

    @Test
    void createSession_blankEmailIsStoredAsNullWithoutCaptureTime() {
        Destination destination = newDestination("Prague");
        Category nightlife = newCategory("Nightlife", "nightlife");
        attachCategory(destination, nightlife);
        Activity activity = newActivity(destination, "Tank Driving", new BigDecimal("150.00"), Set.of(nightlife));
        VoteSessionCreateRequest request = baseRequest(destination.getId());
        request.setInitiatorEmail("   ");
        request.setActivityIds(List.of(activity.getId()));

        VoteSessionResponse response = voteSessionService.createSession(request);

        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        assertThat(session.getInitiatorEmail()).isNull();
        assertThat(session.getEmailCapturedAt()).isNull();
    }
```

Add to `VoteSessionCartCreateTest` (its helper `cartRequest(UUID destinationId, List<UUID> activityIds)` is at line 143; add `import com.myhive.backend.TestDataFactory;` if the file lacks it):

```java
    @Test
    void createCartSession_stampsEmailCaptureTimeWhenEmailPresent() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));
        VoteSessionCartCreateRequest request = cartRequest(prague.getId(), List.of(barCrawl.getId()));
        request.setInitiatorEmail("organizer@example.com");

        VoteSessionResponse response = voteSessionService.createCartSession(request);

        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        assertThat(session.getEmailCapturedAt()).isNotNull();
    }

    @Test
    void createCartSession_leavesCaptureTimeNullWithoutEmail() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));
        VoteSessionCartCreateRequest request = cartRequest(prague.getId(), List.of(barCrawl.getId()));
        request.setInitiatorEmail(null);

        VoteSessionResponse response = voteSessionService.createCartSession(request);

        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        assertThat(session.getInitiatorEmail()).isNull();
        assertThat(session.getEmailCapturedAt()).isNull();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionCreateSessionTest' --tests '*VoteSessionCartCreateTest'`
Expected: compilation FAILS with `cannot find symbol: method getEmailCapturedAt()`.

- [ ] **Step 3: Add the entity columns**

In `VoteSession.java`, directly after the `expiresAt` field:

```java
    /** When the organizer's email was stored; null when no email was ever captured. */
    @Column(name = "email_captured_at")
    private LocalDateTime emailCapturedAt;

    /** One-shot marker for the "half the group has voted" organizer email. */
    @Column(name = "halfway_email_sent_at")
    private LocalDateTime halfwayEmailSentAt;

    /** One-shot marker for the "N people have not voted yet" organizer reminder. */
    @Column(name = "reminder_email_sent_at")
    private LocalDateTime reminderEmailSentAt;
```

Replace the comment above `initiatorEmail` (`// Nullable: collected on the booking page, not at vote creation.`) with:

```java
    // Nullable on the API; the Create-vote modal's email step fills it since 2026-09.
```

- [ ] **Step 4: Normalise the email in `newSession`**

In `VoteSessionService.newSession`, replace `session.setInitiatorEmail(initiatorEmail);` with:

```java
        String email = normalizeEmail(initiatorEmail);
        session.setInitiatorEmail(email);
        if (email != null) {
            session.setEmailCapturedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
```

Add below `newSession`:

```java
    /** Trimmed address, or null for null/blank — a blank email must never read as "captured". */
    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
```

Update the comment in `sendVoteCreatedConfirmationQuietly` from `// organizer email is collected on the booking page, not at creation` to `// no organizer email on this session (API-created or legacy) — nothing to confirm to`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionCreateSessionTest' --tests '*VoteSessionCartCreateTest'`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/entity/VoteSession.java myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionCreateSessionTest.java myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionCartCreateTest.java
git commit -m "feat(vote): stamp email_captured_at and add organizer-email markers on vote_sessions"
```

---

### Task 2: Extract `VoteRanking` (shared like-count comparator)

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/VoteRanking.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java` (`getTally` ~line 509, `freezeCartRanking` ~line 619, delete `cartRankingOrder`/`likeCountOf` ~lines 630-642)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteRankingTest.java`

**Interfaces:**
- Produces: `static Comparator<VoteSessionActivity> VoteRanking.byLikes(Map<UUID, ActivityVoteCount> counts)`, `static long VoteRanking.likeCountOf(Map<UUID, ActivityVoteCount> counts, VoteSessionActivity row)`.

- [ ] **Step 1: Write the failing test**

```java
package com.myhive.backend.service;

import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.repository.ActivityVoteCount;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class VoteRankingTest {

    private static VoteSessionActivity row(UUID activityId, int sortOrder) {
        Activity activity = new Activity();
        activity.setId(activityId);
        VoteSessionActivity row = new VoteSessionActivity();
        row.setActivity(activity);
        row.setSortOrder(sortOrder);
        return row;
    }

    private static ActivityVoteCount count(UUID activityId, long likes) {
        return new ActivityVoteCount() {
            @Override
            public UUID getActivityId() {
                return activityId;
            }

            @Override
            public long getLikeCount() {
                return likes;
            }

            @Override
            public long getSkipCount() {
                return 0;
            }
        };
    }

    @Test
    void byLikes_ordersByLikesDescendingThenBallotOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        Map<UUID, ActivityVoteCount> counts = Map.of(first, count(first, 1), third, count(third, 3));

        List<VoteSessionActivity> sorted = Stream.of(row(first, 0), row(second, 1), row(third, 2))
                .sorted(VoteRanking.byLikes(counts))
                .toList();

        assertThat(sorted).extracting(r -> r.getActivity().getId()).containsExactly(third, first, second);
    }

    @Test
    void likeCountOf_isZeroForAnActivityNobodyVotedOn() {
        assertThat(VoteRanking.likeCountOf(Map.of(), row(UUID.randomUUID(), 0))).isZero();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteRankingTest'`
Expected: compilation FAILS with `cannot find symbol: class VoteRanking`.

- [ ] **Step 3: Create `VoteRanking` and switch `VoteSessionService` to it**

```java
package com.myhive.backend.service;

import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.repository.ActivityVoteCount;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/**
 * Ranking shared by the frozen CART result, the live tally and the organizer's halfway email:
 * like count descending, ties broken by the organizer's original ballot order.
 */
final class VoteRanking {

    private VoteRanking() {
    }

    static Comparator<VoteSessionActivity> byLikes(Map<UUID, ActivityVoteCount> counts) {
        return Comparator
                .comparingLong((VoteSessionActivity row) -> likeCountOf(counts, row)).reversed()
                .thenComparingInt(VoteSessionActivity::getSortOrder);
    }

    static long likeCountOf(Map<UUID, ActivityVoteCount> counts, VoteSessionActivity row) {
        ActivityVoteCount count = counts.get(row.getActivity().getId());
        return count == null ? 0 : count.getLikeCount();
    }
}
```

In `VoteSessionService`:
- `getTally`: `.sorted(cartRankingOrder(counts))` → `.sorted(VoteRanking.byLikes(counts))`; `likeCountOf(counts, row)` → `VoteRanking.likeCountOf(counts, row)`.
- `freezeCartRanking`: `.sorted(cartRankingOrder(counts))` → `.sorted(VoteRanking.byLikes(counts))`.
- Delete the private `cartRankingOrder` method and its Javadoc, and the private `likeCountOf` method.
- Search the file for `Comparator`; if that was its last use, delete `import java.util.Comparator;`.

- [ ] **Step 4: Run the new test plus the ranking characterization tests**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteRankingTest' --tests '*VoteSessionTallyTest' --tests '*VoteSessionCartProcessTest'`
Expected: BUILD SUCCESSFUL, all pass (the existing two pin the order semantics).

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/VoteRanking.java myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java myhive-backend/src/test/java/com/myhive/backend/service/VoteRankingTest.java
git commit -m "refactor(vote): extract like-count ranking into VoteRanking for reuse"
```

---

### Task 3: Halfway email (`vote-halfway.html` + `EmailService.sendVoteHalfway`)

**Files:**
- Create: `myhive-backend/src/main/resources/templates/email/vote-halfway.html`
- Create: `myhive-backend/src/test/java/com/myhive/backend/service/EmailTemplateTestSupport.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java` (near `sendVoteCreatedConfirmation`, line 257)
- Modify: `myhive-backend/src/main/resources/messages.properties`, `messages_de.properties`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteHalfwayTemplateRenderTest.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java`

**Interfaces:**
- Produces: `public static class EmailService.VoteStandingView { public final String name; public final long likes; public VoteStandingView(String name, long likes) }`; `public void EmailService.sendVoteHalfway(VoteSession session, long voters, List<VoteStandingView> standings, String frontendUrl)`; private static `inviteUrlFor(VoteSession, String base)` and `dashboardUrlFor(VoteSession, String base)` (used again in Task 4).

- [ ] **Step 1: Write the failing render test and service tests**

Create `EmailTemplateTestSupport.java`:

```java
package com.myhive.backend.service;

import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** The app's email template setup, for render tests: classpath templates + messages*.properties keys. */
final class EmailTemplateTestSupport {

    private EmailTemplateTestSupport() {
    }

    static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/templates/email/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        templateEngine.setTemplateEngineMessageSource(messages);
        return templateEngine;
    }
}
```

Create `VoteHalfwayTemplateRenderTest.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class VoteHalfwayTemplateRenderTest {

    private static Context context(Locale locale) {
        Destination destination = new Destination();
        destination.setName("Prague");
        VoteSession session = new VoteSession();
        session.setDestination(destination);
        session.setNumberOfTravelers(12);

        Context context = locale == null ? new Context() : new Context(locale);
        context.setVariable("session", session);
        context.setVariable("voters", 6L);
        context.setVariable("travelers", 12);
        context.setVariable("standings", List.of(
                new EmailService.VoteStandingView("Bar Crawl", 4),
                new EmailService.VoteStandingView("Karting", 1)));
        context.setVariable("dashboardUrl", "https://trivlu.com/vote/tok/waiting?manager=mgr-9");
        context.setVariable("expiresAt", "August 2, 2026 at 12:00 UTC");
        context.setVariable("supportEmail", "support@trivlu.com");
        return context;
    }

    @Test
    void rendersCountStandingsAndDashboardLink() {
        String html = EmailTemplateTestSupport.engine().process("vote-halfway", context(null));

        assertThat(html)
                .contains("6 of 12 have voted")
                .contains("Prague")
                .contains("Bar Crawl")
                .contains("4 ♥")
                .contains("Karting")
                .contains("1 ♥")
                .contains("https://trivlu.com/vote/tok/waiting?manager=mgr-9")
                .contains("See live results")
                .contains("August 2, 2026 at 12:00 UTC")
                .contains("mailto:support@trivlu.com")
                .doesNotContain("??");
        assertThat(html.indexOf("Bar Crawl")).isLessThan(html.indexOf("Karting"));
    }

    @Test
    void rendersGermanCopyForGermanLocale() {
        String html = EmailTemplateTestSupport.engine().process("vote-halfway", context(Locale.GERMAN));

        assertThat(html)
                .contains("6 von 12 haben abgestimmt")
                .contains("Live-Ergebnisse ansehen")
                .doesNotContain("??");
    }
}
```

Add to `EmailServiceTest` (imports already present: `Session`, `MimeMessage`, `ArgumentCaptor`, `Context`, `LocalDateTime`, `List`, `UUID`):

```java
    @Test
    void sendVoteHalfway_subjectCountsVotersAgainstTravelers() throws Exception {
        String expectedSubject = "6 of 12 have voted";
        VoteSession session = halfwaySession("alice@example.com");

        MimeMessage realMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(realMessage);
        when(templateEngine.process(eq("vote-halfway"), any())).thenReturn("<html>halfway</html>");

        emailService.sendVoteHalfway(session, 6, List.of(), "https://trivlu.com");

        assertThat(realMessage.getSubject()).isEqualTo(expectedSubject);
        assertThat(realMessage.getAllRecipients()[0].toString()).isEqualTo("alice@example.com");
        verify(asyncMailSender).send(eq(realMessage), anyString());
    }

    @Test
    void sendVoteHalfway_passesManagerDashboardUrlAndStandings() throws Exception {
        VoteSession session = halfwaySession("alice@example.com");
        String expectedDashboardUrl = "https://trivlu.com/vote/" + session.getShareToken()
                + "/waiting?manager=" + session.getManagerToken();
        List<EmailService.VoteStandingView> expectedStandings =
                List.of(new EmailService.VoteStandingView("Bar Crawl", 4));

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("vote-halfway"), contextCaptor.capture())).thenReturn("<html>x</html>");

        emailService.sendVoteHalfway(session, 6, expectedStandings, "https://trivlu.com");

        Context context = contextCaptor.getValue();
        assertThat(context.getVariable("dashboardUrl")).isEqualTo(expectedDashboardUrl);
        assertThat(context.getVariable("standings")).isSameAs(expectedStandings);
        assertThat(context.getVariable("voters")).isEqualTo(6L);
        assertThat(context.getVariable("travelers")).isEqualTo(12);
    }

    @Test
    void sendVoteHalfway_germanSessionUsesGermanSubjectAndLocalePrefix() throws Exception {
        VoteSession session = halfwaySession("alice@example.com");
        session.setLocale("de");
        String expectedDashboardUrl = "https://trivlu.com/de/vote/" + session.getShareToken()
                + "/waiting?manager=" + session.getManagerToken();

        MimeMessage realMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(realMessage);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("vote-halfway"), contextCaptor.capture())).thenReturn("<html>x</html>");

        emailService.sendVoteHalfway(session, 6, List.of(), "https://trivlu.com");

        assertThat(realMessage.getSubject()).isEqualTo("6 von 12 haben abgestimmt");
        assertThat(contextCaptor.getValue().getVariable("dashboardUrl")).isEqualTo(expectedDashboardUrl);
    }

    private static VoteSession halfwaySession(String email) {
        Destination destination = new Destination();
        destination.setName("Prague");
        destination.setSlug("prague");
        VoteSession session = new VoteSession();
        session.setShareToken(UUID.randomUUID());
        session.setManagerToken(UUID.randomUUID());
        session.setDestination(destination);
        session.setInitiatorEmail(email);
        session.setNumberOfTravelers(12);
        session.setExpiresAt(LocalDateTime.of(2026, 8, 2, 12, 0));
        return session;
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteHalfwayTemplateRenderTest' --tests '*EmailServiceTest'`
Expected: compilation FAILS with `cannot find symbol: class VoteStandingView` / `method sendVoteHalfway`.

- [ ] **Step 3: Add the message keys**

Append to `messages.properties` after the `email.voteResult.*` block:

```properties
# Organizer progress email 1 — half the group has voted
email.voteHalfway.subject={0} of {1} have voted
email.voteHalfway.title=Half your group has voted
email.voteHalfway.header.title={0} of {1} have voted
email.voteHalfway.intro=Half your group has voted for <strong>{0}</strong>. Here is where things stand right now — the final results come when the vote closes.
email.voteHalfway.standings.title=Current standings
email.voteHalfway.standings.likes={0} ♥
email.voteHalfway.cta.results=See live results
email.voteHalfway.closes=Voting closes automatically on {0}.
```

Append to `messages_de.properties` at the same position:

```properties
# Organizer progress email 1 — half the group has voted
email.voteHalfway.subject={0} von {1} haben abgestimmt
email.voteHalfway.title=Die Hälfte deiner Gruppe hat abgestimmt
email.voteHalfway.header.title={0} von {1} haben abgestimmt
email.voteHalfway.intro=Die Hälfte deiner Gruppe hat für <strong>{0}</strong> abgestimmt. So steht es gerade — das Endergebnis kommt, wenn das Voting endet.
email.voteHalfway.standings.title=Aktueller Stand
email.voteHalfway.standings.likes={0} ♥
email.voteHalfway.cta.results=Live-Ergebnisse ansehen
email.voteHalfway.closes=Das Voting endet automatisch am {0}.
```

- [ ] **Step 4: Create the template**

`vote-halfway.html` (house style copied from `vote-created.html`):

```html
<!DOCTYPE html>
<html lang="en" th:lang="${#locale.language}" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title th:text="#{email.voteHalfway.title}">Half your group has voted</title>
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; background: #f0f0f0; margin: 0; padding: 20px 0; }
        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 8px; overflow: hidden; }
        .header { background: #6A1B9A; color: white; padding: 32px 30px; text-align: center; border-bottom: 3px solid #4A148C; }
        .header h1 { margin: 12px 0 8px; font-size: 22px; font-weight: 700; color: #f5f5f5; }
        .header p { margin: 0; color: rgba(245,245,245,0.75); font-size: 14px; }
        .content { padding: 30px; }
        .section { margin: 20px 0; padding: 18px 20px; border-left: 4px solid #6A1B9A; background: #f8f9fa; border-radius: 0 6px 6px 0; }
        .section h2 { margin: 0 0 12px; font-size: 16px; color: #1f2121; }
        .standings { width: 100%; border-collapse: collapse; }
        .standings td { padding: 6px 0; font-size: 14px; border-bottom: 1px solid #e0e0e0; }
        .standings td.likes { text-align: right; color: #6A1B9A; font-weight: 600; white-space: nowrap; }
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
        <img src="https://trivlu.com/logo-white.png" alt="Trivlu Travel" th:alt="#{email.common.logoAlt}" style="max-height: 56px;">
        <h1 th:text="#{email.voteHalfway.header.title(${voters}, ${travelers})}">6 of 12 have voted</h1>
        <p th:text="#{email.common.tripTo(${session.destination.name})}">Trip to Prague</p>
    </div>
    <div class="content">
        <p th:utext="#{email.voteHalfway.intro(${session.destination.name})}">Half your group has voted for <strong>Prague</strong>.</p>

        <div class="section">
            <h2 th:text="#{email.voteHalfway.standings.title}">Current standings</h2>
            <table class="standings">
                <tr th:each="row : ${standings}">
                    <td th:text="${row.name}">Bar Crawl</td>
                    <td class="likes" th:text="#{email.voteHalfway.standings.likes(${row.likes})}">4 ♥</td>
                </tr>
            </table>
        </div>

        <a th:href="${dashboardUrl}" class="cta-button" style="color: #ffffff !important;" th:text="#{email.voteHalfway.cta.results}">See live results</a>

        <p class="muted" th:text="#{email.voteHalfway.closes(${expiresAt})}">Voting closes automatically on August 2, 2026 at 12:00 UTC.</p>
        <p class="muted">
            <span th:text="#{email.common.questions}">Questions? Email us at</span>
            <a th:href="'mailto:' + ${supportEmail}" th:text="${supportEmail}">support@trivlu.com</a>.
        </p>
    </div>
    <div class="footer">
        <img src="https://trivlu.com/logo-white.png" alt="Trivlu Travel" th:alt="#{email.common.logoAlt}" style="max-height: 36px; margin-bottom: 10px;">
        <p th:text="#{email.common.footer.tagline}">Creating unforgettable travel experiences</p>
        <p th:text="#{email.common.footer.automated}">This is an automated message. Please do not reply to this email.</p>
        <p th:text="#{email.common.footer.support}">For support, contact us at support@trivlu.com</p>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 5: Add `VoteStandingView`, the URL helpers and `sendVoteHalfway`**

In `EmailService`, next to the other view classes (after `ActivityLineView`):

```java
    /** One ranked line of the halfway email's standings table. */
    public static class VoteStandingView {
        public final String name;
        public final long likes;

        public VoteStandingView(String name, long likes) {
            this.name = name;
            this.likes = likes;
        }
    }
```

Replace the two inline URL concatenations in `sendVoteCreatedConfirmation` with helpers and add the new method right after it:

```java
        variables.put("inviteUrl", inviteUrlFor(session, base));
        variables.put("dashboardUrl", dashboardUrlFor(session, base));
```

```java
    public void sendVoteHalfway(VoteSession session, long voters, List<VoteStandingView> standings, String frontendUrl) {
        Locale locale = localeOf(session.getLocale());
        String base = frontendUrl + localePrefix(session.getLocale());
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("session", session);
        variables.put("voters", voters);
        variables.put("travelers", session.getNumberOfTravelers());
        variables.put("standings", standings);
        variables.put("dashboardUrl", dashboardUrlFor(session, base));
        variables.put("expiresAt", formatDateTime(session.getExpiresAt(), locale));
        variables.put("supportEmail", SUPPORT_EMAIL);

        send(EmailSpec.builder()
                .to(session.getInitiatorEmail())
                .subject(msg(locale, "email.voteHalfway.subject", voters, session.getNumberOfTravelers()))
                .template("vote-halfway")
                .variables(variables)
                .locale(locale)
                .description("vote halfway update to " + maskEmail(session.getInitiatorEmail()))
                .build());
    }

    /** Participant swipe deck; ?ref=invite marks shared-link arrivals in analytics. */
    private static String inviteUrlFor(VoteSession session, String base) {
        return base + "/vote/" + session.getShareToken() + "/activities?ref=invite";
    }

    /** Waiting page with the manager token, which the page adopts so the organizer can manage from any device. */
    private static String dashboardUrlFor(VoteSession session, String base) {
        return base + "/vote/" + session.getShareToken() + "/waiting?manager=" + session.getManagerToken();
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteHalfwayTemplateRenderTest' --tests '*EmailServiceTest' --tests '*VoteCreatedTemplateRenderTest'`
Expected: BUILD SUCCESSFUL, all pass (the vote-created test guards the URL-helper refactor).

- [ ] **Step 7: Commit**

```bash
git add myhive-backend/src/main/resources/templates/email/vote-halfway.html myhive-backend/src/main/resources/messages.properties myhive-backend/src/main/resources/messages_de.properties myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java myhive-backend/src/test/java/com/myhive/backend/service/EmailTemplateTestSupport.java myhive-backend/src/test/java/com/myhive/backend/service/VoteHalfwayTemplateRenderTest.java myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java
git commit -m "feat(email): organizer halfway email with live standings"
```

---

### Task 4: Reminder email (`vote-reminder.html` + `EmailService.sendVoteReminder`)

**Files:**
- Create: `myhive-backend/src/main/resources/templates/email/vote-reminder.html`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java` (after `sendVoteHalfway`)
- Modify: `myhive-backend/src/main/resources/messages.properties`, `messages_de.properties`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteReminderTemplateRenderTest.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java`

**Interfaces:**
- Consumes: `inviteUrlFor`, `dashboardUrlFor`, `halfwaySession(...)` test helper from Task 3.
- Produces: `public void EmailService.sendVoteReminder(VoteSession session, long missing, String frontendUrl)`.

- [ ] **Step 1: Write the failing tests**

Create `VoteReminderTemplateRenderTest.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class VoteReminderTemplateRenderTest {

    private static Context context(Locale locale, String pasteText) {
        Destination destination = new Destination();
        destination.setName("Prague");
        VoteSession session = new VoteSession();
        session.setDestination(destination);
        session.setNumberOfTravelers(12);

        Context context = locale == null ? new Context() : new Context(locale);
        context.setVariable("session", session);
        context.setVariable("missing", 5L);
        context.setVariable("travelers", 12);
        context.setVariable("pasteText", pasteText);
        context.setVariable("inviteUrl", "https://trivlu.com/vote/tok/activities?ref=invite");
        context.setVariable("dashboardUrl", "https://trivlu.com/vote/tok/waiting?manager=mgr-9");
        context.setVariable("supportEmail", "support@trivlu.com");
        return context;
    }

    @Test
    void rendersMissingCountPasteTextAndLinks() {
        String html = EmailTemplateTestSupport.engine().process("vote-reminder",
                context(null, "Hey, 5 of you still haven't voted: https://trivlu.com/vote/tok/activities?ref=invite"));

        assertThat(html)
                .contains("5 of 12 have not voted yet")
                .contains("closes in about 12 hours")
                .contains("Hey, 5 of you still haven&#39;t voted: https://trivlu.com/vote/tok/activities?ref=invite")
                .contains("https://trivlu.com/vote/tok/waiting?manager=mgr-9")
                .contains("Open your vote dashboard")
                .doesNotContain("??");
    }

    @Test
    void rendersGermanCopyForGermanLocale() {
        String html = EmailTemplateTestSupport.engine().process("vote-reminder", context(Locale.GERMAN, "Hey"));

        assertThat(html)
                .contains("5 von 12 haben noch nicht abgestimmt")
                .contains("Voting-Dashboard öffnen")
                .doesNotContain("??");
    }
}
```

Add to `EmailServiceTest`:

```java
    @Test
    void sendVoteReminder_pluralSubjectNamesTheMissingCount() throws Exception {
        String expectedSubject = "5 people have not voted yet";
        VoteSession session = halfwaySession("alice@example.com");

        MimeMessage realMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(realMessage);
        when(templateEngine.process(eq("vote-reminder"), any())).thenReturn("<html>x</html>");

        emailService.sendVoteReminder(session, 5, "https://trivlu.com");

        assertThat(realMessage.getSubject()).isEqualTo(expectedSubject);
        verify(asyncMailSender).send(eq(realMessage), anyString());
    }

    @Test
    void sendVoteReminder_singularSubjectForOneMissingVoter() throws Exception {
        String expectedSubject = "1 person has not voted yet";
        VoteSession session = halfwaySession("alice@example.com");

        MimeMessage realMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(realMessage);
        when(templateEngine.process(eq("vote-reminder"), any())).thenReturn("<html>x</html>");

        emailService.sendVoteReminder(session, 1, "https://trivlu.com");

        assertThat(realMessage.getSubject()).isEqualTo(expectedSubject);
    }

    @Test
    void sendVoteReminder_pasteTextCarriesCountDestinationAndInviteLink() throws Exception {
        VoteSession session = halfwaySession("alice@example.com");
        String expectedInviteUrl = "https://trivlu.com/vote/" + session.getShareToken() + "/activities?ref=invite";

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("vote-reminder"), contextCaptor.capture())).thenReturn("<html>x</html>");

        emailService.sendVoteReminder(session, 3, "https://trivlu.com");

        Context context = contextCaptor.getValue();
        assertThat((String) context.getVariable("pasteText"))
                .startsWith("Hey, 3 of you still haven't voted for our Prague trip.")
                .endsWith(expectedInviteUrl);
        assertThat(context.getVariable("inviteUrl")).isEqualTo(expectedInviteUrl);
        assertThat(context.getVariable("missing")).isEqualTo(3L);
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteReminderTemplateRenderTest' --tests '*EmailServiceTest'`
Expected: compilation FAILS with `cannot find symbol: method sendVoteReminder`.

- [ ] **Step 3: Add the message keys**

`messages.properties` (after the `email.voteHalfway.*` block; note the doubled apostrophe in `paste` — the value takes arguments, so `MessageFormat` applies):

```properties
# Organizer progress email 2 — 12 h left, people missing; paste text for the group chat
email.voteReminder.subject={0} people have not voted yet
email.voteReminder.subject.one=1 person has not voted yet
email.voteReminder.title=Your group needs a nudge
email.voteReminder.header.title={0} of {1} have not voted yet
email.voteReminder.intro=Voting for <strong>{0}</strong> closes in about 12 hours and {1} of {2} people have not voted yet. Paste this into your group chat:
email.voteReminder.paste=Hey, {0} of you still haven''t voted for our {1} trip. It takes a minute and voting closes soon: {2}
email.voteReminder.paste.label=Ready to paste:
email.voteReminder.cta.dashboard=Open your vote dashboard
```

`messages_de.properties`:

```properties
# Organizer progress email 2 — 12 h left, people missing; paste text for the group chat
email.voteReminder.subject={0} Leute haben noch nicht abgestimmt
email.voteReminder.subject.one=1 Person hat noch nicht abgestimmt
email.voteReminder.title=Deine Gruppe braucht einen Schubs
email.voteReminder.header.title={0} von {1} haben noch nicht abgestimmt
email.voteReminder.intro=Das Voting für <strong>{0}</strong> endet in etwa 12 Stunden und {1} von {2} haben noch nicht abgestimmt. Kopier das in euren Gruppenchat:
email.voteReminder.paste=Hey, {0} von euch haben noch nicht für unseren {1}-Trip abgestimmt. Dauert eine Minute, das Voting endet bald: {2}
email.voteReminder.paste.label=Zum Kopieren:
email.voteReminder.cta.dashboard=Voting-Dashboard öffnen
```

- [ ] **Step 4: Create the template**

`vote-reminder.html` — same `<head>`/`<style>` block as `vote-halfway.html` (copy it, drop the `.standings` rules, add `.invite-box`):

```html
        .invite-box { word-break: break-word; font-size: 14px; color: #1f2121; background: #f8f9fa; border: 1px solid #e0e0e0; border-radius: 6px; padding: 12px 14px; margin: 8px 0 0; }
        .invite-label { margin-bottom: 4px; font-weight: 600; }
```

Body:

```html
<body>
<div class="container">
    <div class="header">
        <img src="https://trivlu.com/logo-white.png" alt="Trivlu Travel" th:alt="#{email.common.logoAlt}" style="max-height: 56px;">
        <h1 th:text="#{email.voteReminder.header.title(${missing}, ${travelers})}">5 of 12 have not voted yet</h1>
        <p th:text="#{email.common.tripTo(${session.destination.name})}">Trip to Prague</p>
    </div>
    <div class="content">
        <p th:utext="#{email.voteReminder.intro(${session.destination.name}, ${missing}, ${travelers})}">Voting for <strong>Prague</strong> closes in about 12 hours and 5 of 12 people have not voted yet. Paste this into your group chat:</p>

        <p class="invite-label" th:text="#{email.voteReminder.paste.label}">Ready to paste:</p>
        <p class="invite-box" th:text="${pasteText}">Hey, 5 of you still haven't voted for our Prague trip. It takes a minute and voting closes soon: https://trivlu.com/vote/tok/activities?ref=invite</p>

        <a th:href="${dashboardUrl}" class="cta-button" style="color: #ffffff !important;" th:text="#{email.voteReminder.cta.dashboard}">Open your vote dashboard</a>

        <p class="muted">
            <span th:text="#{email.common.questions}">Questions? Email us at</span>
            <a th:href="'mailto:' + ${supportEmail}" th:text="${supportEmail}">support@trivlu.com</a>.
        </p>
    </div>
    <div class="footer">
        <img src="https://trivlu.com/logo-white.png" alt="Trivlu Travel" th:alt="#{email.common.logoAlt}" style="max-height: 36px; margin-bottom: 10px;">
        <p th:text="#{email.common.footer.tagline}">Creating unforgettable travel experiences</p>
        <p th:text="#{email.common.footer.automated}">This is an automated message. Please do not reply to this email.</p>
        <p th:text="#{email.common.footer.support}">For support, contact us at support@trivlu.com</p>
    </div>
</div>
</body>
```

(`<title>` in the head: `th:text="#{email.voteReminder.title}"`.)

- [ ] **Step 5: Add `sendVoteReminder`**

In `EmailService`, after `sendVoteHalfway`:

```java
    public void sendVoteReminder(VoteSession session, long missing, String frontendUrl) {
        Locale locale = localeOf(session.getLocale());
        String base = frontendUrl + localePrefix(session.getLocale());
        String destinationName = session.getDestination().getName();
        String inviteUrl = inviteUrlFor(session, base);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("session", session);
        variables.put("missing", missing);
        variables.put("travelers", session.getNumberOfTravelers());
        variables.put("pasteText", msg(locale, "email.voteReminder.paste", missing, destinationName, inviteUrl));
        variables.put("inviteUrl", inviteUrl);
        variables.put("dashboardUrl", dashboardUrlFor(session, base));
        variables.put("supportEmail", SUPPORT_EMAIL);

        String subjectKey = missing == 1 ? "email.voteReminder.subject.one" : "email.voteReminder.subject";
        send(EmailSpec.builder()
                .to(session.getInitiatorEmail())
                .subject(msg(locale, subjectKey, missing))
                .template("vote-reminder")
                .variables(variables)
                .locale(locale)
                .description("vote reminder to " + maskEmail(session.getInitiatorEmail()))
                .build());
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteReminderTemplateRenderTest' --tests '*EmailServiceTest'`
Expected: BUILD SUCCESSFUL. If the EN render assertion on `haven&#39;t` fails because Thymeleaf escaped the apostrophe differently, adjust the assertion to the actual escape (`&#39;` vs `&apos;`) — the paste text is escaped by `th:text` on purpose.

- [ ] **Step 7: Commit**

```bash
git add myhive-backend/src/main/resources/templates/email/vote-reminder.html myhive-backend/src/main/resources/messages.properties myhive-backend/src/main/resources/messages_de.properties myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java myhive-backend/src/test/java/com/myhive/backend/service/VoteReminderTemplateRenderTest.java myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java
git commit -m "feat(email): organizer reminder email with ready-to-paste chat text"
```

---

### Task 5: Result email subject "Results are ready" and CTA "Book it"

**Files:**
- Modify: `myhive-backend/src/main/resources/messages.properties:70` and `:78`
- Modify: `myhive-backend/src/main/resources/messages_de.properties:68` and `:76`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteResultTemplateRenderTest.java` (new)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Create `VoteResultTemplateRenderTest.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class VoteResultTemplateRenderTest {

    private static Context context(Locale locale) {
        Destination destination = new Destination();
        destination.setName("Prague");
        VoteSession session = new VoteSession();
        session.setDestination(destination);

        Context context = locale == null ? new Context() : new Context(locale);
        context.setVariable("session", session);
        context.setVariable("resultActivities", List.of());
        context.setVariable("resultUrl", "https://trivlu.com/vote/tok/result");
        return context;
    }

    @Test
    void ctaSaysBookIt() {
        String html = EmailTemplateTestSupport.engine().process("vote-result", context(null));

        assertThat(html)
                .contains(">Book it<")
                .contains("https://trivlu.com/vote/tok/result")
                .doesNotContain("Open in Trip Builder")
                .doesNotContain("??");
    }

    @Test
    void germanCtaSaysJetztBuchen() {
        String html = EmailTemplateTestSupport.engine().process("vote-result", context(Locale.GERMAN));

        assertThat(html).contains(">Jetzt buchen<").doesNotContain("??");
    }
}
```

Add to `EmailServiceTest`:

```java
    @Test
    void sendVoteResult_subjectIsResultsAreReady() throws Exception {
        String expectedSubject = "Results are ready";
        VoteSession session = halfwaySession("alice@example.com");

        MimeMessage realMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(realMessage);
        when(templateEngine.process(eq("vote-result"), any())).thenReturn("<html>x</html>");

        emailService.sendVoteResult(session, List.of(), "https://trivlu.com");

        assertThat(realMessage.getSubject()).isEqualTo(expectedSubject);
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteResultTemplateRenderTest' --tests '*EmailServiceTest.sendVoteResult_subjectIsResultsAreReady'`
Expected: FAIL — subject is `Your group trip to Prague is ready!`, CTA is `Open in Trip Builder`.

- [ ] **Step 3: Change the four values**

`messages.properties`:
```properties
email.voteResult.subject=Results are ready
email.voteResult.cta.open=Book it
```
`messages_de.properties`:
```properties
email.voteResult.subject=Die Ergebnisse sind da
email.voteResult.cta.open=Jetzt buchen
```
Leave every other `email.voteResult.*` key untouched.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteResultTemplateRenderTest' --tests '*EmailServiceTest'`
Expected: BUILD SUCCESSFUL. If any existing test asserted the old subject verbatim, update that assertion to `Results are ready` — the change is the ticket's requirement.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/resources/messages.properties myhive-backend/src/main/resources/messages_de.properties myhive-backend/src/test/java/com/myhive/backend/service/VoteResultTemplateRenderTest.java myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java
git commit -m "feat(email): vote result subject 'Results are ready', CTA 'Book it'"
```

---

### Task 6: `VoteProgressNotifier` + repository queries + kill switch property

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/service/VoteProgressNotifier.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/repository/VoteSessionRepository.java`
- Modify: `myhive-backend/src/main/resources/application.properties:37-38`
- Create: `myhive-backend/src/test/java/com/myhive/backend/config/MockEmailServiceConfig.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/service/TripLeadReminderServiceTest.java` (use the shared config)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteProgressNotifierTest.java`

**Interfaces:**
- Consumes: Task 1 markers, Task 2 `VoteRanking`, Task 3/4 `EmailService.sendVoteHalfway`/`sendVoteReminder`/`VoteStandingView`.
- Produces: `List<UUID> halfwayCandidateIds()`, `List<UUID> reminderCandidateIds()`, `void sendHalfwayIfDue(UUID sessionId)`, `void sendReminderIfDue(UUID sessionId)`, `static Duration REMINDER_LEAD`, `static int halfOf(int travelers)`.
- Repository: `List<VoteSession> findByStatusAndInitiatorEmailIsNotNullAndHalfwayEmailSentAtIsNull(VoteSessionStatus status)`, `List<VoteSession> findByStatusAndInitiatorEmailIsNotNullAndReminderEmailSentAtIsNullAndExpiresAtBefore(VoteSessionStatus status, LocalDateTime cutoff)`.

- [ ] **Step 1: Create the shared mocked-EmailService config and switch the existing test to it**

`myhive-backend/src/test/java/com/myhive/backend/config/MockEmailServiceConfig.java`:

```java
package com.myhive.backend.config;

import com.myhive.backend.service.EmailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * Replaces the real mailer in Spring tests that verify *which* emails a flow hands off.
 * Shared so those tests hit the same cached context; call {@code reset(emailService)}
 * in {@code @BeforeEach} because the mock is a context-wide singleton.
 */
@TestConfiguration
public class MockEmailServiceConfig {

    @Bean
    @Primary
    public EmailService emailService() {
        return mock(EmailService.class);
    }
}
```

In `TripLeadReminderServiceTest`: change `@Import({TestSecurityConfig.class, TripLeadReminderServiceTest.MockConfig.class})` to `@Import({TestSecurityConfig.class, MockEmailServiceConfig.class})`, add `import com.myhive.backend.config.MockEmailServiceConfig;`, delete the nested `MockConfig` class and the now-unused imports (`TestConfiguration`, `Bean`, `Primary`, `mock`).

Run: `cd myhive-backend && ./gradlew test --tests '*TripLeadReminderServiceTest'` — Expected: still green.

- [ ] **Step 2: Write the failing notifier test**

```java
package com.myhive.backend.service;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.config.MockEmailServiceConfig;
import com.myhive.backend.config.TestSecurityConfig;
import com.myhive.backend.dto.VoteBatchRequest;
import com.myhive.backend.dto.VoteSessionCartCreateRequest;
import com.myhive.backend.dto.VoteSessionResponse;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
@Import({TestSecurityConfig.class, MockEmailServiceConfig.class})
class VoteProgressNotifierTest {

    @Autowired private VoteProgressNotifier notifier;
    @Autowired private VoteSessionService voteSessionService;
    @Autowired private VoteSessionRepository voteSessionRepository;
    @Autowired private DestinationRepository destinationRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private EmailService emailService;

    private Destination prague;
    private Activity barCrawl;
    private Activity karting;

    @BeforeEach
    void setUp() {
        reset(emailService);
        prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        barCrawl = activityRepository.saveAndFlush(TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));
        karting = activityRepository.saveAndFlush(TestDataFactory.activity(prague, "Karting", new BigDecimal("60.00")));
    }

    private static LocalDateTime hoursFromNow(int hours) {
        return LocalDateTime.now(ZoneOffset.UTC).plusHours(hours);
    }

    private VoteSession activeSession(int travelers, LocalDateTime expiresAt, String email) {
        VoteSessionCartCreateRequest request = new VoteSessionCartCreateRequest();
        request.setDestinationId(prague.getId());
        request.setInitiatorEmail(email);
        request.setNumberOfTravelers(travelers);
        request.setStartDate(LocalDate.of(2026, 8, 1));
        request.setEndDate(LocalDate.of(2026, 8, 3));
        request.setActivityIds(Arrays.asList(barCrawl.getId(), karting.getId()));
        VoteSessionResponse response = voteSessionService.createCartSession(request);
        VoteSession session = voteSessionRepository.findByShareToken(response.getShareToken()).orElseThrow();
        session.setExpiresAt(expiresAt);
        return voteSessionRepository.saveAndFlush(session);
    }

    /** One new participant likes the given activities and skips the rest of the ballot. */
    private void vote(VoteSession session, Activity... liked) {
        List<Activity> likedList = Arrays.asList(liked);
        VoteBatchRequest batch = new VoteBatchRequest();
        batch.setVoterToken(UUID.randomUUID());
        batch.setVotes(List.of(voteItem(barCrawl, likedList.contains(barCrawl)), voteItem(karting, likedList.contains(karting))));
        voteSessionService.castVotes(session.getShareToken(), batch);
    }

    private static VoteBatchRequest.VoteItem voteItem(Activity activity, boolean liked) {
        VoteBatchRequest.VoteItem item = new VoteBatchRequest.VoteItem();
        item.setActivityId(activity.getId());
        item.setLiked(liked);
        return item;
    }

    private VoteSession reload(VoteSession session) {
        return voteSessionRepository.findById(session.getId()).orElseThrow();
    }

    // ---- halfway ------------------------------------------------------------

    @Test
    void halfway_sentOnceWithRankedStandingsWhenHalfTheGroupVoted() {
        long expectedVoters = 2L;
        VoteSession session = activeSession(4, hoursFromNow(20), "organizer@example.com");
        vote(session, karting);
        vote(session, karting, barCrawl);

        notifier.sendHalfwayIfDue(session.getId());

        ArgumentCaptor<List<EmailService.VoteStandingView>> standingsCaptor = ArgumentCaptor.forClass(List.class);
        verify(emailService).sendVoteHalfway(any(), eq(expectedVoters), standingsCaptor.capture(), anyString());
        assertThat(standingsCaptor.getValue()).extracting(row -> row.name).containsExactly("Karting", "Bar Crawl");
        assertThat(standingsCaptor.getValue()).extracting(row -> row.likes).containsExactly(2L, 1L);
        assertThat(reload(session).getHalfwayEmailSentAt()).isNotNull();
    }

    @Test
    void halfway_notSentBelowTheHalfwayLine() {
        VoteSession session = activeSession(4, hoursFromNow(20), "organizer@example.com");
        vote(session, karting);

        notifier.sendHalfwayIfDue(session.getId());

        verify(emailService, never()).sendVoteHalfway(any(), anyLong(), anyList(), anyString());
        assertThat(reload(session).getHalfwayEmailSentAt()).isNull();
    }

    @Test
    void halfway_notSentOnceEveryoneVoted() {
        VoteSession session = activeSession(2, hoursFromNow(20), "organizer@example.com");
        vote(session, karting);
        vote(session, barCrawl);

        notifier.sendHalfwayIfDue(session.getId());

        verify(emailService, never()).sendVoteHalfway(any(), anyLong(), anyList(), anyString());
    }

    @Test
    void halfway_neverFiresForASoloTraveler() {
        VoteSession session = activeSession(1, hoursFromNow(20), "organizer@example.com");
        vote(session, karting);

        notifier.sendHalfwayIfDue(session.getId());

        verify(emailService, never()).sendVoteHalfway(any(), anyLong(), anyList(), anyString());
    }

    @Test
    void halfway_secondCallDoesNotResend() {
        VoteSession session = activeSession(2, hoursFromNow(20), "organizer@example.com");
        vote(session, karting);

        notifier.sendHalfwayIfDue(session.getId());
        notifier.sendHalfwayIfDue(session.getId());

        verify(emailService, times(1)).sendVoteHalfway(any(), anyLong(), anyList(), anyString());
    }

    @Test
    void halfway_markerStaysSetWhenTheSendThrows() {
        VoteSession session = activeSession(2, hoursFromNow(20), "organizer@example.com");
        vote(session, karting);
        doThrow(new RuntimeException("smtp down")).when(emailService)
                .sendVoteHalfway(any(), anyLong(), anyList(), anyString());

        notifier.sendHalfwayIfDue(session.getId());

        assertThat(reload(session).getHalfwayEmailSentAt()).isNotNull();
    }

    @Test
    void halfwayCandidates_excludeSessionsWithoutEmailCompletedOnesAndAlreadyNotified() {
        VoteSession due = activeSession(2, hoursFromNow(20), "organizer@example.com");
        VoteSession noEmail = activeSession(2, hoursFromNow(20), null);
        VoteSession completed = activeSession(2, hoursFromNow(20), "done@example.com");
        completed.setStatus(VoteSessionStatus.COMPLETED);
        voteSessionRepository.saveAndFlush(completed);
        VoteSession notified = activeSession(2, hoursFromNow(20), "seen@example.com");
        notified.setHalfwayEmailSentAt(LocalDateTime.now(ZoneOffset.UTC));
        voteSessionRepository.saveAndFlush(notified);

        List<UUID> ids = notifier.halfwayCandidateIds();

        assertThat(ids).contains(due.getId())
                .doesNotContain(noEmail.getId(), completed.getId(), notified.getId());
    }

    // ---- reminder -----------------------------------------------------------

    @Test
    void reminder_sentOnceWithMissingCountWhenTwelveHoursAreLeft() {
        long expectedMissing = 3L;
        VoteSession session = activeSession(4, hoursFromNow(11), "organizer@example.com");
        vote(session, karting);

        notifier.sendReminderIfDue(session.getId());

        verify(emailService).sendVoteReminder(any(), eq(expectedMissing), anyString());
        assertThat(reload(session).getReminderEmailSentAt()).isNotNull();
    }

    @Test
    void reminder_notDueWhileMoreThanTwelveHoursAreLeft() {
        VoteSession session = activeSession(4, hoursFromNow(13), "organizer@example.com");

        notifier.sendReminderIfDue(session.getId());

        verify(emailService, never()).sendVoteReminder(any(), anyLong(), anyString());
        assertThat(notifier.reminderCandidateIds()).doesNotContain(session.getId());
    }

    @Test
    void reminder_notSentWhenEveryoneVoted() {
        VoteSession session = activeSession(1, hoursFromNow(11), "organizer@example.com");
        vote(session, karting);

        notifier.sendReminderIfDue(session.getId());

        verify(emailService, never()).sendVoteReminder(any(), anyLong(), anyString());
        assertThat(reload(session).getReminderEmailSentAt()).isNull();
    }

    @Test
    void reminder_secondCallDoesNotResend() {
        VoteSession session = activeSession(4, hoursFromNow(11), "organizer@example.com");

        notifier.sendReminderIfDue(session.getId());
        notifier.sendReminderIfDue(session.getId());

        verify(emailService, times(1)).sendVoteReminder(any(), anyLong(), anyString());
    }

    @Test
    void reminderCandidates_includeOnlyDueEmailedActiveSessions() {
        VoteSession due = activeSession(4, hoursFromNow(11), "organizer@example.com");
        VoteSession early = activeSession(4, hoursFromNow(13), "early@example.com");
        VoteSession noEmail = activeSession(4, hoursFromNow(11), null);

        List<UUID> ids = notifier.reminderCandidateIds();

        assertThat(ids).contains(due.getId()).doesNotContain(early.getId(), noEmail.getId());
    }

    @Test
    void halfOf_roundsUp() {
        assertThat(VoteProgressNotifier.halfOf(12)).isEqualTo(6);
        assertThat(VoteProgressNotifier.halfOf(5)).isEqualTo(3);
        assertThat(VoteProgressNotifier.halfOf(1)).isEqualTo(1);
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteProgressNotifierTest'`
Expected: compilation FAILS with `cannot find symbol: class VoteProgressNotifier`.

- [ ] **Step 4: Add the repository queries and the property**

`VoteSessionRepository` — add after `findByStatusAndExpiresAtBefore`:

```java
    /** Organizer halfway email candidates: open, emailed, not yet notified — the count check happens in Java. */
    List<VoteSession> findByStatusAndInitiatorEmailIsNotNullAndHalfwayEmailSentAtIsNull(VoteSessionStatus status);

    /** Organizer reminder candidates: open, emailed, not yet reminded, closing before {@code cutoff}. */
    List<VoteSession> findByStatusAndInitiatorEmailIsNotNullAndReminderEmailSentAtIsNullAndExpiresAtBefore(
            VoteSessionStatus status, LocalDateTime cutoff);
```

`application.properties` — after the `app.leads.reminders-enabled` line:

```properties
# Organizer progress emails during a vote (halfway + 12h reminder) — kill switch
app.vote.organizer-emails-enabled=${VOTE_ORGANIZER_EMAILS_ENABLED:true}
```

- [ ] **Step 5: Create `VoteProgressNotifier`**

```java
package com.myhive.backend.service;

import com.myhive.backend.entity.VoteSession;
import com.myhive.backend.entity.VoteSessionActivity;
import com.myhive.backend.model.VoteSessionStatus;
import com.myhive.backend.repository.ActivityVoteCount;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionActivityRepository;
import com.myhive.backend.repository.VoteSessionRepository;
import com.myhive.backend.util.Translations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Organizer progress emails during an open vote, one shot each, idempotent through the
 * {@code *_sent_at} markers on the session. Driven by {@link VoteSessionScheduler}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoteProgressNotifier {

    /** The "people have not voted yet" reminder goes out once this much of the window is left. */
    static final Duration REMINDER_LEAD = Duration.ofHours(12);

    private final VoteSessionRepository voteSessionRepository;
    private final VoteSessionActivityRepository voteSessionActivityRepository;
    private final VoteActivityLikeRepository voteActivityLikeRepository;
    private final EmailService emailService;

    @Value("${app.frontend.url:https://trivlu.com}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public List<UUID> halfwayCandidateIds() {
        return idsOf(voteSessionRepository
                .findByStatusAndInitiatorEmailIsNotNullAndHalfwayEmailSentAtIsNull(VoteSessionStatus.ACTIVE));
    }

    @Transactional(readOnly = true)
    public List<UUID> reminderCandidateIds() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).plus(REMINDER_LEAD);
        return idsOf(voteSessionRepository
                .findByStatusAndInitiatorEmailIsNotNullAndReminderEmailSentAtIsNullAndExpiresAtBefore(
                        VoteSessionStatus.ACTIVE, cutoff));
    }

    /** Email 1: fires once when at least half the group (ceil) has voted and not everyone has. */
    @Transactional
    public void sendHalfwayIfDue(UUID sessionId) {
        VoteSession session = openSessionWithEmail(sessionId);
        if (session == null || session.getHalfwayEmailSentAt() != null) {
            return;
        }
        long voters = voterCount(session);
        int travelers = session.getNumberOfTravelers();
        if (voters < halfOf(travelers) || voters >= travelers) {
            return;
        }
        List<EmailService.VoteStandingView> standings = standingsOf(session);
        session.setHalfwayEmailSentAt(LocalDateTime.now(ZoneOffset.UTC));
        voteSessionRepository.saveAndFlush(session);
        sendQuietly("halfway", session,
                () -> emailService.sendVoteHalfway(session, voters, standings, frontendUrl));
    }

    /** Email 2: fires once when 12 h or less remain and somebody still has not voted. */
    @Transactional
    public void sendReminderIfDue(UUID sessionId) {
        VoteSession session = openSessionWithEmail(sessionId);
        if (session == null || session.getReminderEmailSentAt() != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (session.getExpiresAt().isAfter(now.plus(REMINDER_LEAD))) {
            return; // more than 12 h left — not due yet
        }
        long missing = session.getNumberOfTravelers() - voterCount(session);
        if (missing <= 0) {
            return;
        }
        session.setReminderEmailSentAt(now);
        voteSessionRepository.saveAndFlush(session);
        sendQuietly("reminder", session, () -> emailService.sendVoteReminder(session, missing, frontendUrl));
    }

    /** ceil(travelers / 2): the halfway line for a group of that size. */
    static int halfOf(int travelers) {
        return (travelers + 1) / 2;
    }

    private VoteSession openSessionWithEmail(UUID sessionId) {
        VoteSession session = voteSessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getStatus() != VoteSessionStatus.ACTIVE
                || session.getInitiatorEmail() == null || session.getInitiatorEmail().isBlank()) {
            return null;
        }
        return session;
    }

    private long voterCount(VoteSession session) {
        return voteActivityLikeRepository.countDistinctVoterTokensBySessionId(session.getId());
    }

    private List<EmailService.VoteStandingView> standingsOf(VoteSession session) {
        List<VoteSessionActivity> ballot =
                voteSessionActivityRepository.findBySessionIdOrderBySortOrder(session.getId());
        Map<UUID, ActivityVoteCount> counts = voteActivityLikeRepository
                .findVoteCountsBySessionId(session.getId()).stream()
                .collect(Collectors.toMap(ActivityVoteCount::getActivityId, c -> c));
        String lc = Translations.normalize(session.getLocale());
        return ballot.stream()
                .sorted(VoteRanking.byLikes(counts))
                .map(row -> new EmailService.VoteStandingView(
                        Translations.pick(row.getActivity().getTranslations(), lc, "name", row.getActivityName()),
                        VoteRanking.likeCountOf(counts, row)))
                .toList();
    }

    private void sendQuietly(String kind, VoteSession session, Runnable send) {
        try {
            send.run();
        } catch (Exception e) {
            // The marker above must commit even if the hand-off fails, or the next tick would resend forever.
            log.error("Failed to send vote {} email for session {}: {}", kind, session.getId(), e.getMessage(), e);
        }
    }

    private static List<UUID> idsOf(List<VoteSession> sessions) {
        return sessions.stream().map(VoteSession::getId).toList();
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteProgressNotifierTest' --tests '*TripLeadReminderServiceTest'`
Expected: BUILD SUCCESSFUL, all pass.

- [ ] **Step 7: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/VoteProgressNotifier.java myhive-backend/src/main/java/com/myhive/backend/repository/VoteSessionRepository.java myhive-backend/src/main/resources/application.properties myhive-backend/src/test/java/com/myhive/backend/config/MockEmailServiceConfig.java myhive-backend/src/test/java/com/myhive/backend/service/TripLeadReminderServiceTest.java myhive-backend/src/test/java/com/myhive/backend/service/VoteProgressNotifierTest.java
git commit -m "feat(vote): VoteProgressNotifier sends halfway and 12h reminder emails once per session"
```

---

### Task 7: Scheduler tick for organizer progress emails

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionScheduler.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionSchedulerTest.java`

**Interfaces:**
- Consumes: `VoteProgressNotifier` (Task 6).
- Produces: `@Scheduled(fixedDelay = 300_000) public void sendOrganizerProgressEmails()`; `@Value` fields `organizerEmailsEnabled` (`app.vote.organizer-emails-enabled`), `emailEnabled` (`app.email.enabled`).

- [ ] **Step 1: Write the failing tests**

In `VoteSessionSchedulerTest`, add `@Mock private VoteProgressNotifier voteProgressNotifier;` next to the other mocks, `import org.springframework.test.util.ReflectionTestUtils;`, `import java.util.function.Consumer;` is not needed. Add:

```java
    private void enableProgressEmails() {
        ReflectionTestUtils.setField(scheduler, "organizerEmailsEnabled", true);
        ReflectionTestUtils.setField(scheduler, "emailEnabled", true);
    }

    @Test
    void sendOrganizerProgressEmails_delegatesRemindersThenHalfwayPerCandidate() {
        enableProgressEmails();
        UUID reminderId = UUID.randomUUID();
        UUID halfwayId = UUID.randomUUID();
        when(voteProgressNotifier.reminderCandidateIds()).thenReturn(List.of(reminderId));
        when(voteProgressNotifier.halfwayCandidateIds()).thenReturn(List.of(halfwayId));

        scheduler.sendOrganizerProgressEmails();

        verify(voteProgressNotifier).sendReminderIfDue(reminderId);
        verify(voteProgressNotifier).sendHalfwayIfDue(halfwayId);
    }

    @Test
    void sendOrganizerProgressEmails_continuesOnError() {
        enableProgressEmails();
        UUID failing = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        when(voteProgressNotifier.reminderCandidateIds()).thenReturn(List.of());
        when(voteProgressNotifier.halfwayCandidateIds()).thenReturn(List.of(failing, healthy));
        doThrow(new RuntimeException("boom")).when(voteProgressNotifier).sendHalfwayIfDue(failing);

        scheduler.sendOrganizerProgressEmails();

        verify(voteProgressNotifier).sendHalfwayIfDue(healthy);
    }

    @Test
    void sendOrganizerProgressEmails_noopWhenKillSwitchOff() {
        ReflectionTestUtils.setField(scheduler, "organizerEmailsEnabled", false);
        ReflectionTestUtils.setField(scheduler, "emailEnabled", true);

        scheduler.sendOrganizerProgressEmails();

        verify(voteProgressNotifier, never()).reminderCandidateIds();
        verify(voteProgressNotifier, never()).halfwayCandidateIds();
    }

    @Test
    void sendOrganizerProgressEmails_noopWhenEmailDisabled() {
        // A disabled mailer must not burn the one-shot markers.
        ReflectionTestUtils.setField(scheduler, "organizerEmailsEnabled", true);
        ReflectionTestUtils.setField(scheduler, "emailEnabled", false);

        scheduler.sendOrganizerProgressEmails();

        verify(voteProgressNotifier, never()).reminderCandidateIds();
        verify(voteProgressNotifier, never()).halfwayCandidateIds();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionSchedulerTest'`
Expected: compilation FAILS with `cannot find symbol: method sendOrganizerProgressEmails()`.

- [ ] **Step 3: Wire the scheduler**

`VoteSessionScheduler` — add the field, flags and method (keep the two existing methods untouched):

```java
    private final VoteProgressNotifier voteProgressNotifier;

    /** Kill switch for the organizer progress emails only; creation/result emails are unaffected. */
    @Value("${app.vote.organizer-emails-enabled:true}")
    private boolean organizerEmailsEnabled;

    /** With the mailer off, ticking would silently burn the one-shot markers — skip instead. */
    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Scheduled(fixedDelay = 300_000)
    public void sendOrganizerProgressEmails() {
        if (!organizerEmailsEnabled || !emailEnabled) {
            return;
        }
        for (UUID sessionId : voteProgressNotifier.reminderCandidateIds()) {
            runQuietly("reminder", sessionId, voteProgressNotifier::sendReminderIfDue);
        }
        for (UUID sessionId : voteProgressNotifier.halfwayCandidateIds()) {
            runQuietly("halfway", sessionId, voteProgressNotifier::sendHalfwayIfDue);
        }
    }

    private static void runQuietly(String kind, UUID sessionId, Consumer<UUID> action) {
        try {
            action.accept(sessionId);
        } catch (Exception e) {
            log.error("Failed to process vote {} email for session {}: {}", kind, sessionId, e.getMessage(), e);
        }
    }
```

Imports to add: `org.springframework.beans.factory.annotation.Value`, `java.util.UUID`, `java.util.function.Consumer`. The method is deliberately **not** `@Transactional` — each notifier call owns its transaction.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionSchedulerTest'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the whole backend suite**

Run: `cd myhive-backend && ./gradlew test`
Expected: BUILD SUCCESSFUL. Fix anything the earlier tasks broke before moving on.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionScheduler.java myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionSchedulerTest.java
git commit -m "feat(vote): schedule organizer progress emails on the 5-minute vote tick"
```

---

### Task 8: `emailFormat` validator shared by checkout and the vote screen

**Files:**
- Modify: `myhive-react-app/src/utils/validators.js`
- Modify: `myhive-react-app/src/hooks/useEmailLeadCapture.js:6,33`
- Test: `myhive-react-app/src/utils/validators.test.js`

**Interfaces:**
- Produces: `export function emailFormat(value, message = 'Please check the email address.')` → `undefined` when valid, the message otherwise.

- [ ] **Step 1: Write the failing test**

Append to `validators.test.js` (extend the import to `{required, slugFormat, discountRange, emailFormat}`):

```js
describe('emailFormat', () => {
    it('accepts anything@anything.tld, ignoring surrounding whitespace', () => {
        expect(emailFormat('sam@example.com')).toBeUndefined();
        expect(emailFormat('  sam@example.co.uk  ')).toBeUndefined();
    });

    it('rejects empty, missing @, and missing dot after the @', () => {
        expect(emailFormat('')).toBe('Please check the email address.');
        expect(emailFormat('   ')).toBe('Please check the email address.');
        expect(emailFormat(null)).toBe('Please check the email address.');
        expect(emailFormat('sam.example.com')).toBe('Please check the email address.');
        expect(emailFormat('sam@nowhere')).toBe('Please check the email address.');
    });

    it('supports a custom message', () => {
        expect(emailFormat('nope', 'Bad email')).toBe('Bad email');
    });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd myhive-react-app && npm test -- --watchAll=false src/utils/validators.test.js`
Expected: FAIL — `emailFormat is not a function`.

- [ ] **Step 3: Implement and reuse in the lead-capture hook**

Append to `validators.js`:

```js
// Deliberately permissive (anything@anything.tld): the server's @Email is the
// authority; this only stops obvious typos before a request is made.
const EMAIL_RE = /\S+@\S+\.\S+/;

export function emailFormat(value, message = 'Please check the email address.') {
    const trimmed = typeof value === 'string' ? value.trim() : '';
    return EMAIL_RE.test(trimmed) ? undefined : message;
}
```

In `useEmailLeadCapture.js`: delete `const EMAIL_RE = /\S+@\S+\.\S+/;`, add `import {emailFormat} from '../utils/validators';`, and change the guard to:

```js
    if (emailFormat(trimmed) !== undefined || capturedRef.current === trimmed) {
```

- [ ] **Step 4: Run both test files**

Run: `cd myhive-react-app && npm test -- --watchAll=false src/utils/validators.test.js src/hooks/useEmailLeadCapture.test.js`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/utils/validators.js myhive-react-app/src/utils/validators.test.js myhive-react-app/src/hooks/useEmailLeadCapture.js
git commit -m "refactor(ui): share the email format rule via validators.emailFormat"
```

---

### Task 9: Two-step `StartGroupVoteModal` (email screen)

**Files:**
- Modify: `myhive-react-app/src/components/vote/StartGroupVoteModal.js` (full rewrite below)
- Modify: `myhive-react-app/src/components/vote/StartGroupVoteModal.css`
- Modify: `myhive-react-app/src/i18n/messages/en.json`, `de.json` (`voteComponents.start.email.*`)
- Test: `myhive-react-app/src/components/vote/StartGroupVoteModal.test.js` (full rewrite below)

**Interfaces:**
- Consumes: `emailFormat` (Task 8), `voteApi.createSession`/`createCartSession` (already accept `initiatorEmail`).
- Produces: unchanged component props. New events `organizer_voted`, `email_screen_view`, `email_invalid_attempt`, `contact_captured`, `link_revealed`; `modal_abandoned` gains `step`.

- [ ] **Step 1: Add the dictionary keys**

`en.json`, inside `voteComponents.start` (sibling of `errors`):

```json
"email": {
  "title": "Your vote is saved.",
  "sub": "Where should we send the results?",
  "label": "Email",
  "placeholder": "you@email.com",
  "helper": "We will email you when everyone has voted. If the group stops responding, we will send you a reminder message you can paste into the chat.",
  "submit": "Get the link for your group",
  "errors": {
    "invalid": "Please check the email address."
  }
}
```

`de.json`, same place (draft — flag for native review in the PR description, as in PR #22):

```json
"email": {
  "title": "Deine Stimme ist gespeichert.",
  "sub": "Wohin sollen wir die Ergebnisse schicken?",
  "label": "E-Mail",
  "placeholder": "du@email.com",
  "helper": "Wir mailen dir, sobald alle abgestimmt haben. Wenn die Gruppe nicht mehr reagiert, schicken wir dir eine Erinnerung, die du direkt in den Chat kopieren kannst.",
  "submit": "Link für deine Gruppe holen",
  "errors": {
    "invalid": "Bitte prüf die E-Mail-Adresse."
  }
}
```

- [ ] **Step 2: Replace the test file**

`StartGroupVoteModal.test.js`:

```js
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import StartGroupVoteModal from './StartGroupVoteModal';
import voteApi from '../../services/voteApi';
import { pushEvent } from '../../utils/analytics';

jest.mock('../../services/voteApi', () => ({
  __esModule: true,
  default: { createCartSession: jest.fn(), createSession: jest.fn() },
}));

jest.mock('../../utils/analytics', () => ({ pushEvent: jest.fn() }));

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));

const SUBMIT = 'Get the link for your group';

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

// Step 1 → step 2: the email screen only appears after "Create vote".
async function goToEmailStep() {
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));
  return screen.getByLabelText('Email');
}

async function launchWith(email) {
  const input = await goToEmailStep();
  await userEvent.type(input, email);
  await userEvent.click(screen.getByRole('button', { name: SUBMIT }));
}

afterEach(() => {
  localStorage.clear();
});

test('step 1 has no email input; "Create vote" reveals the one-input email screen', async () => {
  renderModal();
  expect(screen.queryByLabelText('Email')).not.toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));

  expect(screen.getByRole('heading', { name: 'Your vote is saved.' })).toBeInTheDocument();
  expect(screen.getByText('Where should we send the results?')).toBeInTheDocument();
  const input = screen.getByLabelText('Email');
  expect(input).toHaveAttribute('type', 'email');
  expect(input).toHaveAttribute('autocomplete', 'email');
  expect(input).toHaveFocus();
  expect(screen.getAllByRole('textbox')).toHaveLength(1);
  expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  expect(screen.getByText(/reminder message you can paste into the chat/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: SUBMIT })).toBeInTheDocument();
  expect(voteApi.createCartSession).not.toHaveBeenCalled();
  expect(pushEvent).toHaveBeenCalledWith('organizer_voted', { vote_mode: 'CART', selected_count: 2 });
  expect(pushEvent).toHaveBeenCalledWith('email_screen_view', { vote_mode: 'CART' });
});

test('empty email shows the error, keeps focus and never calls the API', async () => {
  renderModal();
  await goToEmailStep();

  await userEvent.click(screen.getByRole('button', { name: SUBMIT }));

  expect(screen.getByText('Please check the email address.')).toBeInTheDocument();
  expect(screen.getByLabelText('Email')).toHaveFocus();
  expect(voteApi.createCartSession).not.toHaveBeenCalled();
  expect(pushEvent).toHaveBeenCalledWith('email_invalid_attempt', { vote_mode: 'CART', reason: 'empty' });
});

test('malformed email keeps the typed value', async () => {
  renderModal();
  const input = await goToEmailStep();
  await userEvent.type(input, 'sam@nowhere');

  await userEvent.click(screen.getByRole('button', { name: SUBMIT }));

  expect(screen.getByText('Please check the email address.')).toBeInTheDocument();
  expect(input).toHaveValue('sam@nowhere');
  expect(pushEvent).toHaveBeenCalledWith('email_invalid_attempt', { vote_mode: 'CART', reason: 'format' });
  expect(voteApi.createCartSession).not.toHaveBeenCalled();
});

test('valid email creates the session with initiatorEmail, stores tokens, fires the funnel, navigates', async () => {
  voteApi.createCartSession.mockResolvedValue({ shareToken: 't-1', managerToken: 'm-1' });
  renderModal();

  await launchWith('sam@example.com');

  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/vote/t-1/waiting'));
  expect(voteApi.createCartSession).toHaveBeenCalledWith({
    destinationId: 'd-1',
    initiatorEmail: 'sam@example.com',
    numberOfTravelers: 4,
    startDate: '2026-08-01',
    endDate: '2026-08-03',
    activityIds: ['a-1', 'a-2'],
  });
  expect(localStorage.getItem('myhive-manager-t-1')).toBe('m-1');
  expect(localStorage.getItem('myhive-initiator-t-1')).toBe('true');
  expect(localStorage.getItem('myhive-trip-vote-session')).toBe('t-1');
  expect(pushEvent).toHaveBeenCalledWith('contact_captured', {
    trip_id: 't-1', vote_mode: 'CART', source: 'vote_email_screen',
  });
  expect(pushEvent).toHaveBeenCalledWith('vote_launched', {
    trip_id: 't-1', user_role: 'organizer', selected_count: 2,
  });
  expect(pushEvent).toHaveBeenCalledWith('link_revealed', { trip_id: 't-1', vote_mode: 'CART' });
  const order = pushEvent.mock.calls.map(([name]) => name);
  expect(order.indexOf('contact_captured')).toBeLessThan(order.indexOf('vote_launched'));
  expect(order.indexOf('vote_launched')).toBeLessThan(order.indexOf('link_revealed'));
});

test('API failure keeps the email, fires no launch events, and allows a retry', async () => {
  voteApi.createCartSession
    .mockRejectedValueOnce(new Error('activityId x does not exist'))
    .mockResolvedValueOnce({ shareToken: 't-3', managerToken: 'm-3' });
  renderModal();

  await launchWith('sam@example.com');

  expect(await screen.findByText('activityId x does not exist')).toBeInTheDocument();
  expect(screen.getByLabelText('Email')).toHaveValue('sam@example.com');
  expect(pushEvent).not.toHaveBeenCalledWith('vote_launched', expect.anything());
  expect(pushEvent).not.toHaveBeenCalledWith('link_revealed', expect.anything());

  await userEvent.click(screen.getByRole('button', { name: SUBMIT }));

  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/vote/t-3/waiting'));
  expect(voteApi.createCartSession).toHaveBeenCalledTimes(2);
});

test('missing trip dates block the email step', async () => {
  renderModal({ startDate: '', endDate: '' });

  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));

  expect(screen.getByText('Trip dates are required')).toBeInTheDocument();
  expect(screen.queryByLabelText('Email')).not.toBeInTheDocument();
  expect(voteApi.createCartSession).not.toHaveBeenCalled();
});

test('accepts dates typed into its own date inputs', async () => {
  voteApi.createCartSession.mockResolvedValue({ shareToken: 't-2', managerToken: 'm-2' });
  renderModal({ startDate: '', endDate: '' });
  fireEvent.change(screen.getByLabelText('Start date'), { target: { value: '2026-09-04' } });
  fireEvent.change(screen.getByLabelText('End date'), { target: { value: '2026-09-06' } });

  await launchWith('sam@example.com');

  await waitFor(() => expect(voteApi.createCartSession).toHaveBeenCalledWith(
    expect.objectContaining({ startDate: '2026-09-04', endDate: '2026-09-06', initiatorEmail: 'sam@example.com' }),
  ));
});

test('QUIZ mode creates a QUIZ session with quiz payload and email, and calls onLaunched', async () => {
  voteApi.createSession.mockResolvedValue({ shareToken: 'tok1', managerToken: 'mgr1' });
  const onLaunched = jest.fn();
  renderModal({
    voteMode: 'QUIZ', quizResponses: [{ questionId: 'q1', answerId: 'a1' }], budget: null, onLaunched,
  });

  await launchWith('sam@example.com');

  await waitFor(() => expect(voteApi.createSession).toHaveBeenCalledWith(
    expect.objectContaining({
      initiatorEmail: 'sam@example.com',
      quizResponses: [{ questionId: 'q1', answerId: 'a1' }],
      numberOfTravelers: 4,
      startDate: '2026-08-01',
      endDate: '2026-08-03',
      activityIds: ['a-1', 'a-2'],
    }),
  ));
  expect(voteApi.createCartSession).not.toHaveBeenCalled();
  expect(onLaunched).toHaveBeenCalled();
  expect(localStorage.getItem('myhive-trip-vote-session')).toBeNull();
  expect(mockNavigate).toHaveBeenCalledWith('/vote/tok1/waiting', { state: { managerToken: 'mgr1' } });
});

test('value-promise microcopy is shown on step 1', () => {
  renderModal();
  expect(screen.getByText(/share the link with your mates/i)).toBeInTheDocument();
});

test('closing on step 1 fires modal_abandoned without an email', async () => {
  const onClose = jest.fn();
  renderModal({ onClose });

  await userEvent.click(screen.getByRole('button', { name: 'Close' }));

  expect(pushEvent).toHaveBeenCalledWith('modal_abandoned', {
    modal: 'start_vote', vote_mode: 'CART', has_email: false, step: 'details',
  });
  expect(onClose).toHaveBeenCalled();
});

test('closing on the email step reports whether an address was typed', async () => {
  const onClose = jest.fn();
  renderModal({ onClose });
  const input = await goToEmailStep();
  await userEvent.type(input, 'sam@example.com');

  await userEvent.click(screen.getByRole('button', { name: 'Close' }));

  expect(pushEvent).toHaveBeenCalledWith('modal_abandoned', {
    modal: 'start_vote', vote_mode: 'CART', has_email: true, step: 'email',
  });
});

test('does not fire modal_abandoned when closed after a successful launch', async () => {
  voteApi.createCartSession.mockResolvedValue({ shareToken: 't-1', managerToken: 'm-1' });
  const onClose = jest.fn();
  renderModal({ onClose });

  await launchWith('sam@example.com');
  await waitFor(() => expect(mockNavigate).toHaveBeenCalled());

  pushEvent.mockClear();
  await userEvent.click(screen.getByRole('button', { name: 'Close' }));

  expect(pushEvent).not.toHaveBeenCalledWith('modal_abandoned', expect.anything());
});
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd myhive-react-app && npm test -- --watchAll=false src/components/vote/StartGroupVoteModal.test.js`
Expected: FAIL — no `Email` input after "Create vote", `createCartSession` called from step 1.

- [ ] **Step 4: Rewrite the component**

`StartGroupVoteModal.js`:

```js
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppModal from '../AppModal';
import voteApi from '../../services/voteApi';
import { pushEvent } from '../../utils/analytics';
import { clearTripLead } from '../../utils/tripLead';
import { emailFormat } from '../../utils/validators';
import { getOrCreateVoterToken } from '../../utils/voterToken';
import { useT } from '../../i18n';
import './StartGroupVoteModal.css';

const STEP_DETAILS = 'details';
const STEP_EMAIL = 'email';

// Pure so the validation rules can be reasoned about (and tested) independent
// of component state wiring.
function validate({ needsDates, voteStartDate, voteEndDate }, t) {
    const errors = {};

    if (needsDates) {
        if (!voteStartDate || !voteEndDate) {
            errors.dates = t('start.errors.datesRequired');
        } else if (voteEndDate < voteStartDate) {
            errors.dates = t('start.errors.endBeforeStart');
        }
    }

    return errors;
}

// Two-step modal that turns the current cart into a vote session.
// Step 1 ("details") confirms the trip and asks for dates only when the trip
// setup never captured them (vote_sessions requires them). Step 2 ("email")
// is the organizer email screen: the session is created only once a valid
// address is typed, so the invite link is never shown without one.
function StartGroupVoteModal({
    isOpen, onClose, destinationId, activityIds, numberOfTravelers, startDate, endDate,
    voteMode = 'CART', quizResponses = null, budget = null, onLaunched,
}) {
    const t = useT('voteComponents');
    const navigate = useNavigate();
    const [step, setStep] = useState(STEP_DETAILS);
    const [voteStartDate, setVoteStartDate] = useState(startDate || '');
    const [voteEndDate, setVoteEndDate] = useState(endDate || '');
    const [email, setEmail] = useState('');
    const [errors, setErrors] = useState({});
    const [apiError, setApiError] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const launchedRef = useRef(false);
    const emailInputRef = useRef(null);

    const needsDates = !startDate || !endDate;
    const isEmailStep = step === STEP_EMAIL;

    // useModalA11y focuses the first focusable element only when the modal
    // opens; the step change happens later, so the email field focuses itself.
    useEffect(() => {
        if (isEmailStep && emailInputRef.current) {
            emailInputRef.current.focus();
        }
    }, [isEmailStep]);

    const handleClose = () => {
        if (!launchedRef.current) {
            pushEvent('modal_abandoned', {
                modal: 'start_vote', vote_mode: voteMode, has_email: email.trim() !== '', step,
            });
        }
        onClose();
    };

    // Step 1 → step 2: validates the dates, never talks to the server.
    const handleContinue = () => {
        const nextErrors = validate({ needsDates, voteStartDate, voteEndDate }, t);
        setErrors(nextErrors);
        if (Object.keys(nextErrors).length > 0) {
            return;
        }
        pushEvent('organizer_voted', { vote_mode: voteMode, selected_count: activityIds.length });
        setStep(STEP_EMAIL);
        pushEvent('email_screen_view', { vote_mode: voteMode });
    };

    const handleCreate = async () => {
        if (submitting) {
            return;
        }
        const trimmedEmail = email.trim();
        const emailError = emailFormat(trimmedEmail, t('start.email.errors.invalid'));
        if (emailError) {
            setErrors({ email: emailError });
            pushEvent('email_invalid_attempt', {
                vote_mode: voteMode, reason: trimmedEmail === '' ? 'empty' : 'format',
            });
            if (emailInputRef.current) {
                emailInputRef.current.focus();
            }
            return;
        }
        setErrors({});
        setSubmitting(true);
        setApiError(null);
        try {
            const resolvedStart = needsDates ? voteStartDate : startDate;
            const resolvedEnd = needsDates ? voteEndDate : endDate;
            const session = voteMode === 'QUIZ'
                ? await voteApi.createSession({
                    destinationId,
                    initiatorEmail: trimmedEmail,
                    numberOfTravelers,
                    startDate: resolvedStart,
                    endDate: resolvedEnd,
                    budget,
                    voterToken: getOrCreateVoterToken(),
                    quizResponses,
                    activityIds,
                })
                : await voteApi.createCartSession({
                    destinationId,
                    initiatorEmail: trimmedEmail,
                    numberOfTravelers,
                    startDate: resolvedStart,
                    endDate: resolvedEnd,
                    activityIds,
                });
            localStorage.setItem(`myhive-initiator-${session.shareToken}`, 'true');
            if (session.managerToken) {
                localStorage.setItem(`myhive-manager-${session.shareToken}`, session.managerToken);
            }
            if (voteMode === 'CART') {
                // QUIZ parity: quiz sessions intentionally do not set this key.
                localStorage.setItem('myhive-trip-vote-session', session.shareToken);
            }
            clearTripLead();
            pushEvent('contact_captured', {
                trip_id: session.shareToken, vote_mode: voteMode, source: 'vote_email_screen',
            });
            // Mirrors CuratePage's A12 vote_launched (QUIZ) — same field names,
            // shareToken as trip_id, organizer is always the creator here.
            pushEvent('vote_launched', {
                trip_id: session.shareToken,
                user_role: 'organizer',
                selected_count: activityIds.length,
            });
            // The waiting page (the invite link) is the only next screen, and the
            // address is stored server-side by now — this is the reveal.
            pushEvent('link_revealed', { trip_id: session.shareToken, vote_mode: voteMode });
            launchedRef.current = true;
            if (onLaunched) onLaunched();
            if (voteMode === 'QUIZ') {
                navigate(`/vote/${session.shareToken}/waiting`, { state: { managerToken: session.managerToken } });
            } else {
                navigate(`/vote/${session.shareToken}/waiting`);
            }
        } catch (e) {
            setApiError(e.message || t('start.errors.createFailed'));
            setSubmitting(false);
        }
    };

    const footer = isEmailStep ? (
        <div className="start-vote-email-footer">
            <button
                type="button"
                className="btn btn--primary btn--full-width"
                onClick={handleCreate}
                disabled={submitting}
            >
                {submitting ? t('start.creating') : t('start.email.submit')}
            </button>
            {apiError && <p className="error-message">{apiError}</p>}
        </div>
    ) : (
        <button
            type="button"
            className="btn btn--primary btn--full-width"
            onClick={handleContinue}
        >
            {t('start.create')}
        </button>
    );

    return (
        <AppModal
            isOpen={isOpen}
            onClose={handleClose}
            closeOnBackdrop
            title={isEmailStep ? t('start.email.title') : t('start.title')}
            contentClassName={isEmailStep ? 'start-vote-modal start-vote-modal--email' : 'start-vote-modal'}
            footer={footer}
        >
            {isEmailStep ? (
                <>
                    <p className="start-vote-email-sub">{t('start.email.sub')}</p>
                    <input
                        ref={emailInputRef}
                        id="start-vote-email"
                        className={`start-vote-email-input${errors.email ? ' error' : ''}`}
                        type="email"
                        inputMode="email"
                        autoComplete="email"
                        autoCapitalize="none"
                        spellCheck={false}
                        aria-label={t('start.email.label')}
                        aria-invalid={Boolean(errors.email)}
                        placeholder={t('start.email.placeholder')}
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        onKeyDown={(e) => {
                            if (e.key === 'Enter') {
                                handleCreate();
                            }
                        }}
                    />
                    {errors.email && <span className="error-message" role="alert">{errors.email}</span>}
                    <p className="start-vote-email-helper">{t('start.email.helper')}</p>
                </>
            ) : (
                <>
                    <p className="start-vote-modal-sub">
                        {t('start.sub')}
                    </p>
                    {needsDates && (
                        <>
                            <label htmlFor="start-vote-start-date">{t('start.tripDates')}</label>
                            <div className="start-vote-modal-dates">
                                <input
                                    id="start-vote-start-date"
                                    aria-label={t('start.startDate')}
                                    type="date"
                                    value={voteStartDate}
                                    onChange={(e) => setVoteStartDate(e.target.value)}
                                />
                                <input
                                    id="start-vote-end-date"
                                    aria-label={t('start.endDate')}
                                    type="date"
                                    value={voteEndDate}
                                    onChange={(e) => setVoteEndDate(e.target.value)}
                                />
                            </div>
                            {errors.dates && <span className="error-message">{errors.dates}</span>}
                        </>
                    )}
                </>
            )}
        </AppModal>
    );
}

export default StartGroupVoteModal;
```

Append to `StartGroupVoteModal.css`:

```css
/* Email step — the one-input screen: heading 28px/700, sub 17px muted,
   input 56px, helper 14px muted, full-width primary button, nothing else. */
.start-vote-modal--email .app-modal-header {
    border-bottom: none;
    padding-bottom: 0;
}

.start-vote-modal--email .app-modal-header h2 {
    font-size: 1.75rem;
    font-weight: 700;
    line-height: 1.2;
}

.start-vote-email-sub {
    margin: 0 0 1rem;
    font-size: 1.0625rem;
    color: var(--text-muted);
}

.start-vote-modal--email input.start-vote-email-input {
    height: 56px;
    padding: 0 1rem;
    font-size: 1rem; /* ≥16px so iOS Safari does not zoom the field */
    border-radius: 10px;
}

.start-vote-email-helper {
    margin: 0.75rem 0 0;
    font-size: 0.875rem;
    line-height: 1.5;
    color: var(--text-muted);
}

.start-vote-email-footer {
    width: 100%;
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
}
```

- [ ] **Step 5: Run the modal tests, then the whole CRA suite**

Run: `cd myhive-react-app && npm test -- --watchAll=false src/components/vote/StartGroupVoteModal.test.js`
Expected: PASS (12 tests). If `toHaveFocus()` fails on the first test, wrap the assertion in `await waitFor(() => expect(input).toHaveFocus())` — the focus effect runs after commit.

Run: `cd myhive-react-app && npm test -- --watchAll=false`
Expected: PASS. `TripBuilder.test.js` only asserts the "Create vote" button exists on step 1, so it stays green.

- [ ] **Step 6: Manual check in the browser**

Run backend (`./gradlew bootRun --args='--spring.profiles.active=dev'`) and `npm run dev` in `myhive-next`. Open `/destination/prague?tab=trip-builder`, add an activity, click "Let your mates vote" → "Create vote" → verify: heading + one email field with focus, invalid entry keeps value and shows the error, valid entry lands on the waiting page with the link, `window.dataLayer` contains `organizer_voted`, `email_screen_view`, `contact_captured`, `vote_launched`, `link_revealed` in that order. Check the `/de/...` variant shows the German copy.

- [ ] **Step 7: Commit**

```bash
git add myhive-react-app/src/components/vote/StartGroupVoteModal.js myhive-react-app/src/components/vote/StartGroupVoteModal.css myhive-react-app/src/components/vote/StartGroupVoteModal.test.js myhive-react-app/src/i18n/messages/en.json myhive-react-app/src/i18n/messages/de.json
git commit -m "feat(vote): organizer email screen as step 2 of Create vote — no link without an address"
```

---

### Task 10: About page — Company block

**Files:**
- Modify: `myhive-react-app/src/pages/AboutPage.js`
- Modify: `myhive-react-app/src/pages/AboutPage.css`
- Modify: `myhive-react-app/src/i18n/messages/en.json`, `de.json` (`about.company.*`)
- Test: `myhive-react-app/src/pages/AboutPage.test.js` (new)

- [ ] **Step 1: Write the failing test**

```js
import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {HelmetProvider} from 'react-helmet-async';
import AboutPage from './AboutPage';

function renderPage() {
    return render(
        <HelmetProvider>
            <MemoryRouter>
                <AboutPage/>
            </MemoryRouter>
        </HelmetProvider>
    );
}

test('publishes the operating legal entity, address and company id', () => {
    renderPage();

    expect(screen.getByRole('heading', {name: /company/i})).toBeInTheDocument();
    expect(screen.getByText(/PRAGOUT GROUP s\.r\.o\./)).toBeInTheDocument();
    expect(screen.getByText(/Na Folimance 2155\/15/)).toBeInTheDocument();
    expect(screen.getByText('11692111')).toBeInTheDocument();
    expect(screen.getByRole('link', {name: 'info@trivlu.com'})).toHaveAttribute('href', 'mailto:info@trivlu.com');
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd myhive-react-app && npm test -- --watchAll=false src/pages/AboutPage.test.js`
Expected: FAIL — no "Company" heading.

- [ ] **Step 3: Add the keys, the section and the styles**

`en.json`, inside `about`:

```json
"company": {
  "title": "Company",
  "legalName": "Legal entity",
  "address": "Registered address",
  "companyId": "Company ID (IČO)",
  "registration": "Registration",
  "contact": "Contact"
}
```

`de.json`, inside `about`:

```json
"company": {
  "title": "Unternehmen",
  "legalName": "Rechtsträger",
  "address": "Sitz",
  "companyId": "IČO",
  "registration": "Registrierung",
  "contact": "Kontakt"
}
```

`AboutPage.js` — add `import {COMPANY} from '../legal/companyInfo';` and, after the "Our Story" section:

```jsx
            <section className="about-section about-company">
                <h2>{t('company.title')}</h2>
                <dl className="about-company-facts">
                    <div>
                        <dt>{t('company.legalName')}</dt>
                        <dd>{COMPANY.legalName}</dd>
                    </div>
                    <div>
                        <dt>{t('company.address')}</dt>
                        <dd>{COMPANY.address}</dd>
                    </div>
                    <div>
                        <dt>{t('company.companyId')}</dt>
                        <dd>{COMPANY.companyId}</dd>
                    </div>
                    <div>
                        <dt>{t('company.registration')}</dt>
                        <dd>{COMPANY.registration}</dd>
                    </div>
                    <div>
                        <dt>{t('company.contact')}</dt>
                        <dd><a href={`mailto:${COMPANY.contactEmail}`}>{COMPANY.contactEmail}</a></dd>
                    </div>
                </dl>
            </section>
```

`AboutPage.css` — append:

```css
.about-company-facts {
    display: grid;
    grid-template-columns: max-content 1fr;
    gap: 0.5rem 1.5rem;
    margin: 0 auto;
    text-align: left;
    max-width: 36rem;
}

.about-company-facts > div {
    display: contents;
}

.about-company-facts dt {
    font-weight: 600;
}

.about-company-facts dd {
    margin: 0;
    color: var(--text-muted);
}

@media (max-width: 480px) {
    .about-company-facts {
        grid-template-columns: 1fr;
        gap: 0.25rem;
    }

    .about-company-facts dd {
        margin-bottom: 0.75rem;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd myhive-react-app && npm test -- --watchAll=false src/pages/AboutPage.test.js`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/pages/AboutPage.js myhive-react-app/src/pages/AboutPage.css myhive-react-app/src/pages/AboutPage.test.js myhive-react-app/src/i18n/messages/en.json myhive-react-app/src/i18n/messages/de.json
git commit -m "feat(about): publish the legal entity, address and company id"
```

---

### Task 11: Whole-branch verification, review, then docs (docs only after user approval)

**Files:**
- Modify (after approval): `README.md` (emails list + `VOTE_ORGANIZER_EMAILS_ENABLED` env row near `REMINDERS_ENABLED`), `CLAUDE.md` (Key Architectural Patterns: note the Create-vote email step and the two progress emails in the "Trip lead reminders"/vote text), memory files `project_vote_created_email.md` and `project_analytics_tracking.md`.

- [ ] **Step 1: Full verification**

```bash
cd myhive-backend && ./gradlew build
cd ../myhive-react-app && npm test -- --watchAll=false
cd ../myhive-next && npm run build
```
Expected: all three succeed (the Next build syncs `legacy-src` and compiles the changed CRA files; `BACKEND_URL` must be set for it, e.g. `BACKEND_URL=http://localhost:8080`).

- [ ] **Step 2: End-to-end email check against the dev mailer (optional but recommended)**

With `EMAIL_ENABLED=true` and `RESEND_API_KEY` set locally: create a vote via the UI with a real inbox, confirm the "Your group vote is live" email arrives. Then, in the H2 shell, set `expires_at = now + 11h` on that session and cast two votes from incognito windows on a 4-traveler session; within 5 minutes both the halfway and the reminder emails must arrive. End the vote early → "Results are ready" with a "Book it" button.

- [ ] **Step 3: Code review**

Use `superpowers:requesting-code-review` on the whole branch diff (`git diff main...HEAD`). Fix findings, re-run the suites, commit.

- [ ] **Step 4: Ask the user for approval; only then update docs**

Per `CLAUDE.md` rule 3: after the user approves, apply the doc/memory edits listed above and commit them:

```bash
git add README.md CLAUDE.md
git commit -m "docs: organizer email screen, progress emails, VOTE_ORGANIZER_EMAILS_ENABLED"
```

---

## Self-review notes (done while writing)

- Spec §1 (two-step modal, states, events, i18n, no consent note, no prefill) → Task 9. Spec §2 (columns, optional API email, notifier rules, gating, standings) → Tasks 1, 2, 6, 7. Spec §3 (three emails) → Tasks 3, 4, 5. Spec §4 (About) → Task 10. Spec §6 test matrix → covered task by task; Spec §7 docs → Task 11.
- The spec phrases the reminder as "12 h after creation"; the plan implements the equivalent `expiresAt <= now + 12h` because `createdAt` is a `@CreationTimestamp` that tests cannot backdate, while `expiresAt` is a plain column. Same instant for every real session (`expiresAt = createdAt + 24h`).
- Names cross-checked: `sendVoteHalfway(session, voters, standings, frontendUrl)` / `sendVoteReminder(session, missing, frontendUrl)` (Tasks 3, 4, 6); `VoteRanking.byLikes` / `likeCountOf` (Tasks 2, 6); `halfwayCandidateIds` / `reminderCandidateIds` / `sendHalfwayIfDue` / `sendReminderIfDue` (Tasks 6, 7); `emailFormat` (Tasks 8, 9); i18n keys `start.email.*` (Task 9 JSON ↔ JSX ↔ tests); `about.company.*` (Task 10).
