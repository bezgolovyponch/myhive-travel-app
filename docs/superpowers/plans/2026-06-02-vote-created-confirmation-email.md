# Vote-created confirmation email — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Send the organizer a confirmation email the moment a vote session is created, explaining the process and giving a shareable invite link plus a dashboard link that grants management from any device.

**Architecture:** A new `EmailService.sendVoteCreatedConfirmation` renders a new Thymeleaf template (`vote-created.html`) and is invoked from `VoteSessionService.createSession`, gated by `app.email.enabled` and wrapped in `try/catch` so a mail failure never fails session creation. The email's dashboard link carries the `managerToken` as a query param; the frontend `VoteWaitingPage` adopts that param into `localStorage` (then strips it from the URL) so management works on any device.

**Tech Stack:** Spring Boot 4 / Java 25, Thymeleaf email templates, JUnit 5 + Mockito + AssertJ; React 19, React Router, Jest + React Testing Library.

**Spec:** `docs/superpowers/specs/2026-06-02-vote-created-confirmation-email-design.md`

**Branch:** `feat/vote-created-email` (already created; spec + prior UI commits are on it).

---

## File Structure

| File | Responsibility | Action |
|------|----------------|--------|
| `myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java` | Add `sendVoteCreatedConfirmation(VoteSession, String)` | Modify |
| `myhive-backend/src/main/resources/templates/email/vote-created.html` | New email body, same house style as `vote-result.html` | Create |
| `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java` | Call the new method in `createSession` (gated, try/catch) | Modify (~line 144) |
| `myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java` | Unit test the new method (mocked engine, capture Context) | Modify |
| `myhive-backend/src/test/java/com/myhive/backend/service/VoteCreatedTemplateRenderTest.java` | Render the real template, assert links present | Create |
| `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionServiceTest.java` | Service tests: sent-when-enabled, not-when-disabled, survives-failure | Modify |
| `myhive-react-app/src/pages/vote/VoteWaitingPage.js` | Adopt `?manager=` into localStorage, strip from URL | Modify |
| `myhive-react-app/src/pages/vote/VoteWaitingPage.test.js` | Test the param adoption + absence | Create |

---

## Task 1: Backend — `EmailService.sendVoteCreatedConfirmation`

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java`

- [ ] **Step 1: Write the failing test**

In `EmailServiceTest.java`, add these imports to the existing import block:

```java
import com.myhive.backend.entity.Destination;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.context.Context;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
```

(`Destination` and several of these may already be imported — do not duplicate.)

Add this test method to the class:

```java
    @Test
    void sendVoteCreatedConfirmation_buildsLinksWithManagerTokenAndSends() throws Exception {
        UUID shareToken = UUID.randomUUID();
        UUID managerToken = UUID.randomUUID();

        Destination destination = new Destination();
        destination.setName("Bali");
        destination.setSlug("bali");

        VoteSession session = new VoteSession();
        session.setShareToken(shareToken);
        session.setManagerToken(managerToken);
        session.setInitiatorEmail("alice@example.com");
        session.setNumberOfTravelers(2);
        session.setStartDate(LocalDate.of(2026, 8, 1));
        session.setEndDate(LocalDate.of(2026, 8, 10));
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(24));
        session.setDestination(destination);

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("vote-created"), contextCaptor.capture()))
                .thenReturn("<html>ok</html>");
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailService.sendVoteCreatedConfirmation(session, "https://trivlu.com");

        Context context = contextCaptor.getValue();
        String dashboardUrl = (String) context.getVariable("dashboardUrl");
        String inviteUrl = (String) context.getVariable("inviteUrl");
        assertThat(dashboardUrl)
                .contains("/vote/" + shareToken + "/waiting")
                .contains("manager=" + managerToken);
        assertThat(inviteUrl)
                .isEqualTo("https://trivlu.com/vote/" + shareToken + "/activities");
        assertThat(context.getVariable("supportEmail")).isEqualTo("support@trivlu.com");
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `myhive-backend/`): `./gradlew test --tests '*EmailServiceTest'`
Expected: compile error / FAIL — `sendVoteCreatedConfirmation` does not exist.

- [ ] **Step 3: Implement the method**

In `EmailService.java`, add the new method after `sendVoteResult` (after line 156, before `maskEmail`). It mirrors `sendVoteResult`:

```java
    public void sendVoteCreatedConfirmation(VoteSession session, String siteUrl) {
        log.info("Preparing vote-created confirmation email: from={}, to={}, destination={}",
                fromEmail, maskEmail(session.getInitiatorEmail()), session.getDestination().getName());
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(session.getInitiatorEmail());
            helper.setSubject("Your group vote for " + session.getDestination().getName() + " is live");

            String shareToken = session.getShareToken().toString();
            String inviteUrl = siteUrl + "/vote/" + shareToken + "/activities";
            String dashboardUrl = siteUrl + "/vote/" + shareToken + "/waiting?manager=" + session.getManagerToken();

            DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy");
            DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' HH:mm 'UTC'");

            Context context = new Context();
            context.setVariable("session", session);
            context.setVariable("inviteUrl", inviteUrl);
            context.setVariable("dashboardUrl", dashboardUrl);
            context.setVariable("supportEmail", "support@trivlu.com");
            context.setVariable("startDate", session.getStartDate().format(dateFormat));
            context.setVariable("endDate", session.getEndDate().format(dateFormat));
            context.setVariable("expiresAt", session.getExpiresAt().format(dateTimeFormat));

            log.debug("Processing email template: vote-created");
            String htmlContent = templateEngine.process("vote-created", context);
            helper.setText(htmlContent, true);

            log.info("Sending vote-created confirmation email via SMTP to: {}", maskEmail(session.getInitiatorEmail()));
            mailSender.send(message);
            log.info("Vote-created confirmation email sent successfully to: {}", maskEmail(session.getInitiatorEmail()));

        } catch (Exception e) {
            log.error("Failed to send vote-created confirmation email to: {}. Cause: {}",
                    maskEmail(session.getInitiatorEmail()), e.getMessage(), e);
            throw new EmailSendException("Failed to send vote-created confirmation email", e);
        }
    }
```

`DateTimeFormatter` is already imported (`EmailService.java:21`). `Context`, `MimeMessage`, `MimeMessageHelper`, `EmailSendException` are already imported. No new imports needed.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests '*EmailServiceTest'`
Expected: PASS (both the existing tests and the new one).

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java
git commit -m "feat: add sendVoteCreatedConfirmation to EmailService

Builds the invite link and a dashboard link carrying the managerToken,
renders the vote-created template, and sends to the organizer.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Backend — `vote-created.html` template + render test

**Files:**
- Create: `myhive-backend/src/main/resources/templates/email/vote-created.html`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteCreatedTemplateRenderTest.java`

- [ ] **Step 1: Write the failing render test**

Create `myhive-backend/src/test/java/com/myhive/backend/service/VoteCreatedTemplateRenderTest.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.entity.Destination;
import com.myhive.backend.entity.VoteSession;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class VoteCreatedTemplateRenderTest {

    private TemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/templates/email/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }

    @Test
    void voteCreatedTemplateRendersLinksAndDestination() {
        Destination destination = new Destination();
        destination.setName("Bali");

        VoteSession session = new VoteSession();
        session.setDestination(destination);
        session.setNumberOfTravelers(2);
        session.setStartDate(LocalDate.of(2026, 8, 1));
        session.setEndDate(LocalDate.of(2026, 8, 10));
        session.setExpiresAt(LocalDateTime.of(2026, 8, 2, 12, 0));

        Context context = new Context();
        context.setVariable("session", session);
        context.setVariable("inviteUrl", "https://trivlu.com/vote/tok/activities");
        context.setVariable("dashboardUrl", "https://trivlu.com/vote/tok/waiting?manager=mgr-9");
        context.setVariable("supportEmail", "support@trivlu.com");
        context.setVariable("startDate", "August 1, 2026");
        context.setVariable("endDate", "August 10, 2026");
        context.setVariable("expiresAt", "August 2, 2026 at 12:00 UTC");

        String html = engine().process("vote-created", context);

        assertThat(html)
                .contains("Bali")
                .contains("https://trivlu.com/vote/tok/waiting?manager=mgr-9")
                .contains("https://trivlu.com/vote/tok/activities")
                .contains("mailto:support@trivlu.com")
                .contains("How it works");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*VoteCreatedTemplateRenderTest'`
Expected: FAIL — Thymeleaf cannot resolve template `vote-created` (template file does not exist yet).

- [ ] **Step 3: Create the template**

Create `myhive-backend/src/main/resources/templates/email/vote-created.html`:

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Your Group Vote is Live</title>
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; background: #f0f0f0; margin: 0; padding: 20px 0; }
        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 8px; overflow: hidden; }
        .header { background: #6A1B9A; color: white; padding: 32px 30px; text-align: center; border-bottom: 3px solid #4A148C; }
        .header h1 { margin: 12px 0 8px; font-size: 22px; font-weight: 700; color: #f5f5f5; }
        .header p { margin: 0; color: rgba(245,245,245,0.75); font-size: 14px; }
        .content { padding: 30px; }
        .section { margin: 20px 0; padding: 18px 20px; border-left: 4px solid #6A1B9A; background: #f8f9fa; border-radius: 0 6px 6px 0; }
        .section h2 { margin: 0 0 12px; font-size: 16px; color: #1f2121; }
        .steps { margin: 0; padding-left: 20px; }
        .steps li { margin: 8px 0; font-size: 14px; }
        .invite-label { margin-bottom: 4px; font-weight: 600; }
        .invite-box { word-break: break-all; font-size: 13px; color: #6A1B9A; background: #f8f9fa; border: 1px solid #e0e0e0; border-radius: 6px; padding: 12px 14px; margin: 8px 0 0; }
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
        <h1>Your group vote is live!</h1>
        <p th:text="'Trip to ' + ${session.destination.name}">Trip to Bali</p>
    </div>
    <div class="content">
        <p>
            Your group vote for <strong th:text="${session.destination.name}">Bali</strong>
            (<span th:text="${startDate}">August 1, 2026</span> &ndash; <span th:text="${endDate}">August 10, 2026</span>,
            <span th:text="${session.numberOfTravelers}">2</span> travelers) is now open.
            Voting closes automatically in 24 hours, on <span th:text="${expiresAt}">August 2, 2026 at 12:00 UTC</span>.
        </p>

        <div class="section">
            <h2>How it works</h2>
            <ol class="steps">
                <li>Share the invite link below with your group &mdash; they swipe to vote on the activities.</li>
                <li>Track progress any time on your vote dashboard (live count + countdown).</li>
                <li>When the timer ends &mdash; or you end it early &mdash; we tally the votes and email you the final itinerary to open in Trip Builder.</li>
            </ol>
        </div>

        <p class="invite-label">Your invite link &mdash; send it to your group:</p>
        <p class="invite-box" th:text="${inviteUrl}">https://trivlu.com/vote/abc/activities</p>

        <a th:href="${dashboardUrl}" class="cta-button" style="color: #ffffff !important;">Open your vote dashboard</a>

        <p class="muted">
            The final results will be emailed to this address automatically &mdash; you don't need to do anything else.
        </p>
        <p class="muted">
            Questions? Email us at
            <a th:href="'mailto:' + ${supportEmail}" th:text="${supportEmail}">support@trivlu.com</a>.
        </p>
    </div>
    <div class="footer">
        <img src="https://trivlu.com/logo-white.png" alt="Trivlu Travel" style="max-height: 36px; margin-bottom: 10px;">
        <p>Creating unforgettable travel experiences</p>
        <p>This is an automated message. Please do not reply to this email.</p>
        <p>For support, contact us at support@trivlu.com</p>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests '*VoteCreatedTemplateRenderTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/resources/templates/email/vote-created.html myhive-backend/src/test/java/com/myhive/backend/service/VoteCreatedTemplateRenderTest.java
git commit -m "feat: add vote-created email template

Customer-facing house style (matches vote-result/itinerary-confirmation):
purple header, 3-step how-it-works, copyable invite link, dashboard CTA,
support mailto. Covered by a Thymeleaf render test.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Backend — wire into `createSession`

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java` (~line 144)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionServiceTest.java`

- [ ] **Step 1: Write the failing tests**

In `VoteSessionServiceTest.java`, add imports:

```java
import com.myhive.backend.exception.EmailSendException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
```

(`verify`, `eq`, `any`, `when`, `BigDecimal`, `LocalDate`, `Optional`, `Set`, `UUID`, `Activity`, `Category`, `Destination`, `VoteSession`, `VoteSessionResponse`, `VoteSessionCreateRequest` are already imported.)

Add a private helper and three tests to the class:

```java
    private VoteSessionCreateRequest happyPathCreateSetup() {
        UUID destId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        Category category = new Category();
        category.setId(catId);
        category.setVotable(true);

        Destination destination = new Destination();
        destination.setId(destId);
        destination.setName("Bali");
        destination.setSlug("bali");
        destination.setCategories(Set.of(category));

        Activity activity = new Activity();
        activity.setId(activityId);
        activity.setDestination(destination);
        activity.setName("Surfing");
        activity.setPrice(new BigDecimal("65"));
        activity.setCategories(Set.of(category));

        when(destinationRepository.findById(destId)).thenReturn(Optional.of(destination));
        when(activityRepository.findAllById(any())).thenReturn(List.of(activity));
        when(voteSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(voteActivityLikeRepository.countDistinctVoterTokensBySessionId(any())).thenReturn(0L);

        VoteSessionCreateRequest request = new VoteSessionCreateRequest();
        request.setDestinationId(destId);
        request.setInitiatorEmail("alice@example.com");
        request.setNumberOfTravelers(2);
        request.setStartDate(LocalDate.of(2026, 8, 1));
        request.setEndDate(LocalDate.of(2026, 8, 10));
        request.setVoterToken(UUID.randomUUID());
        request.setQuizResponses(List.of());
        request.setActivityIds(List.of(activityId));
        return request;
    }

    @Test
    void createSession_sendsConfirmationEmailWhenEnabled() {
        ReflectionTestUtils.setField(voteSessionService, "emailEnabled", true);
        ReflectionTestUtils.setField(voteSessionService, "siteUrl", "https://trivlu.com");
        VoteSessionCreateRequest request = happyPathCreateSetup();

        VoteSessionResponse response = voteSessionService.createSession(request);

        assertThat(response.getShareToken()).isNotNull();
        verify(emailService).sendVoteCreatedConfirmation(any(VoteSession.class), eq("https://trivlu.com"));
    }

    @Test
    void createSession_doesNotSendEmailWhenDisabled() {
        ReflectionTestUtils.setField(voteSessionService, "emailEnabled", false);
        VoteSessionCreateRequest request = happyPathCreateSetup();

        voteSessionService.createSession(request);

        verify(emailService, never()).sendVoteCreatedConfirmation(any(), any());
    }

    @Test
    void createSession_succeedsEvenWhenConfirmationEmailFails() {
        ReflectionTestUtils.setField(voteSessionService, "emailEnabled", true);
        ReflectionTestUtils.setField(voteSessionService, "siteUrl", "https://trivlu.com");
        VoteSessionCreateRequest request = happyPathCreateSetup();
        doThrow(new EmailSendException("smtp down", null))
                .when(emailService).sendVoteCreatedConfirmation(any(), any());

        VoteSessionResponse response = voteSessionService.createSession(request);

        assertThat(response.getShareToken()).isNotNull();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests '*VoteSessionServiceTest'`
Expected: `createSession_sendsConfirmationEmailWhenEnabled` and `createSession_succeedsEvenWhenConfirmationEmailFails` FAIL — `sendVoteCreatedConfirmation` is never invoked (the wiring does not exist yet). `createSession_doesNotSendEmailWhenDisabled` passes incidentally.

- [ ] **Step 3: Wire the call into `createSession`**

In `VoteSessionService.java`, in `createSession`, replace the final two lines (currently `VoteSessionService.java:144-146`):

```java
        long participantCount = voteActivityLikeRepository
                .countDistinctVoterTokensBySessionId(session.getId());
        return toResponse(session, participantCount, session.getManagerToken());
```

with:

```java
        if (emailEnabled) {
            try {
                emailService.sendVoteCreatedConfirmation(session, siteUrl);
            } catch (EmailSendException e) {
                // A failed confirmation email must never fail session creation — log and move on.
                log.error("Failed to send vote-created confirmation for session {}: {}",
                        session.getId(), e.getMessage(), e);
            }
        }

        long participantCount = voteActivityLikeRepository
                .countDistinctVoterTokensBySessionId(session.getId());
        return toResponse(session, participantCount, session.getManagerToken());
```

Add the import near the other exception imports (`VoteSessionService.java:24-27`):

```java
import com.myhive.backend.exception.EmailSendException;
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests '*VoteSessionServiceTest'`
Expected: PASS (all tests, including the three new ones).

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionServiceTest.java
git commit -m "feat: send confirmation email on vote-session creation

Gated by app.email.enabled and wrapped in try/catch so a mail failure
never rolls back or fails session creation.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Frontend — `VoteWaitingPage` adopts `?manager=` from the email link

**Files:**
- Modify: `myhive-react-app/src/pages/vote/VoteWaitingPage.js`
- Test: `myhive-react-app/src/pages/vote/VoteWaitingPage.test.js`

- [ ] **Step 1: Write the failing test**

Create `myhive-react-app/src/pages/vote/VoteWaitingPage.test.js`:

```javascript
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import VoteWaitingPage from './VoteWaitingPage';
import voteApi from '../../services/voteApi';

jest.mock('../../services/voteApi');

function LocationSearch() {
  const location = useLocation();
  return <div data-testid="search">{location.search}</div>;
}

function renderAt(entry) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route
          path="/vote/:shareToken/waiting"
          element={<><VoteWaitingPage /><LocationSearch /></>}
        />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => {
  localStorage.clear();
  voteApi.getSession.mockResolvedValue({
    destinationName: 'Bali',
    destinationSlug: 'bali',
    status: 'ACTIVE',
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    participantCount: 0,
    numberOfTravelers: 2,
  });
  voteApi.getParticipantCount.mockResolvedValue({ count: 0 });
});

test('adopts managerToken from ?manager=, shows End voting early, strips token from URL', async () => {
  renderAt('/vote/tok-1/waiting?manager=mgr-9');

  expect(await screen.findByText(/End voting early/i)).toBeInTheDocument();
  expect(localStorage.getItem('myhive-manager-tok-1')).toBe('mgr-9');
  expect(localStorage.getItem('myhive-initiator-tok-1')).toBe('true');
  await waitFor(() => expect(screen.getByTestId('search').textContent).toBe(''));
});

test('without manager param or localStorage, End voting early is absent', async () => {
  renderAt('/vote/tok-2/waiting');

  expect(await screen.findByText(/Share with friends/i)).toBeInTheDocument();
  expect(screen.queryByText(/End voting early/i)).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `myhive-react-app/`): `npm test -- --watchAll=false VoteWaitingPage`
Expected: FAIL — the first test cannot find "End voting early" (the page doesn't read `?manager=` yet, so `isInitiator` stays false).

- [ ] **Step 3: Implement the param adoption**

In `VoteWaitingPage.js`:

(a) Update the router import (line 2) to add `useSearchParams`:

```javascript
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
```

(b) Replace the two `const` derivations of `isInitiator` / `managerToken` (lines 8-9):

```javascript
    const isInitiator = !!localStorage.getItem(`myhive-initiator-${shareToken}`);
    const managerToken = localStorage.getItem(`myhive-manager-${shareToken}`);
```

with state initialized lazily from localStorage, plus the search-params hook:

```javascript
    const [searchParams] = useSearchParams();
    const [isInitiator, setIsInitiator] = useState(
        () => !!localStorage.getItem(`myhive-initiator-${shareToken}`));
    const [managerToken, setManagerToken] = useState(
        () => localStorage.getItem(`myhive-manager-${shareToken}`));
```

(c) Add this effect immediately after the state declarations (before the existing `useEffect` that calls `voteApi.getSession`):

```javascript
    // Adopt a managerToken arriving via the email dashboard link (?manager=...),
    // persist it, then strip it from the URL so the secret isn't left in history.
    useEffect(() => {
        const urlManager = searchParams.get('manager');
        if (!urlManager) {
            return;
        }
        localStorage.setItem(`myhive-manager-${shareToken}`, urlManager);
        localStorage.setItem(`myhive-initiator-${shareToken}`, 'true');
        setManagerToken(urlManager);
        setIsInitiator(true);
        navigate(`/vote/${shareToken}/waiting`, { replace: true });
    }, [searchParams, shareToken, navigate]);
```

Everything else (the `isInitiator &&` guard on the "End voting early" button, `handleClose` using `managerToken`) is unchanged — those names now resolve to state.

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watchAll=false VoteWaitingPage`
Expected: PASS (both tests).

- [ ] **Step 5: Lint the changed file**

Run: `npx eslint src/pages/vote/VoteWaitingPage.js`
Expected: no new errors (a pre-existing `react-hooks/exhaustive-deps` warning on the `getSession` effect at ~line 29 is acceptable and unrelated).

- [ ] **Step 6: Commit**

```bash
git add myhive-react-app/src/pages/vote/VoteWaitingPage.js myhive-react-app/src/pages/vote/VoteWaitingPage.test.js
git commit -m "feat: adopt managerToken from email dashboard link on VoteWaitingPage

Reads ?manager= from the URL, persists it to localStorage so the organizer
can end voting from any device, then strips the token from the URL.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full backend test suite**

Run (from `myhive-backend/`): `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Run the frontend test suite**

Run (from `myhive-react-app/`): `npm test -- --watchAll=false`
Expected: all suites pass.

- [ ] **Step 3: Report results**

Summarize pass/fail counts. Do not claim success unless both suites are green. If anything fails, stop and use superpowers:systematic-debugging.

---

## Post-Approval (not part of TDD loop)

After the user approves the implementation (per CLAUDE.md workflow rule 3), update memory:
- `memory/project_recent_changes.md` (or a new memory file) noting the vote-created confirmation email feature shipped on `feat/vote-created-email` (2026-06-02): new `EmailService.sendVoteCreatedConfirmation`, `vote-created.html`, gated wire-in to `createSession`, and `VoteWaitingPage` `?manager=` adoption.
- No `README.md` change required (no new endpoint, env var, or service).

---

## Self-Review Notes

- **Spec coverage:** new EmailService method (Task 1) ✓; template matching customer-facing style (Task 2) ✓; gated try/catch wire-in (Task 3) ✓; invite + dashboard(+managerToken) + support links (Tasks 1-2) ✓; frontend `?manager=` adoption + URL strip (Task 4) ✓; all three test groups from the spec ✓.
- **Type/name consistency:** method `sendVoteCreatedConfirmation(VoteSession, String)` and context vars `inviteUrl` / `dashboardUrl` / `supportEmail` / `startDate` / `endDate` / `expiresAt` are used identically in EmailService, the render test, and the template. Query param is `manager` in both backend link construction and frontend reader.
- **No placeholders:** every code/test/command step is concrete.
