# Vote-created confirmation email — Design Spec

**Date:** 2026-06-02
**Status:** Approved

---

## Overview

When an organizer creates a group vote session (`POST /vote/sessions`), the backend
saves the session and returns the `shareToken` + `managerToken`, but **sends no email**.
The only email in the vote flow today is `sendVoteResult`, fired when the session closes
(24h timer or manual "End voting early").

The gap: if the organizer loses the link — closes the tab, clears `localStorage`, or
switches device — they have no way back to their session and no record of what happens
next, until the result email arrives up to 24 hours later. They are "in a vacuum".

This change sends a **confirmation email immediately on session creation** to the
organizer's `initiatorEmail`. The email:

- confirms the session details (destination, dates, traveler count, 24h deadline),
- explains the whole process and the next steps (3 steps),
- gives the **invite link** to share with friends (shown as copyable text),
- gives a **dashboard link that carries the `managerToken`** so the organizer can
  return — and end voting early — from **any device**,
- links to support via `mailto:support@trivlu.com`.

Touches the backend (new `EmailService` method + new Thymeleaf template, wired into
`VoteSessionService.createSession`) and one small frontend change so the dashboard link's
embedded `managerToken` actually grants management on a fresh device.

---

## User Flow

1. Organizer finishes curating and creates the session (`CuratePage` → `POST /vote/sessions`).
2. Backend saves the session, then (gated by `app.email.enabled`) sends the confirmation
   email to `initiatorEmail`. **Email failure never fails session creation.**
3. Organizer receives the email. From it they can, at any time / on any device:
   - **Copy the invite link** and send it to friends, and
   - **Open their vote dashboard** (`/vote/{shareToken}/waiting?manager={managerToken}`),
     where the timer, live vote count, share box, and the **"End voting early"** button are
     all available — even on a device that never had the session in `localStorage`.
4. When the timer ends or they end early, the existing `sendVoteResult` email delivers the
   final itinerary (unchanged).

---

## Approach

**Send synchronously inside `createSession`, gated by `emailEnabled`, wrapped in
`try/catch` (Approach A — approved).** This mirrors the existing `sendVoteResult` call
inside `processSession`; no new event/listener machinery. The send happens after the
session and its child rows are saved, so all data the email needs (tokens, destination,
dates) is populated. Because the call is wrapped in `try/catch` and the exception is
swallowed (logged, not rethrown), the creation transaction still commits and the API
always returns the new session even if SMTP is down.

Trade-off accepted: the email is sent within the creation transaction (before commit), so
it is sent slightly before the row is durably committed. The session object is fully valid
at that point, and a post-send commit failure is vanishingly unlikely for a simple insert;
the simplicity and consistency with `sendVoteResult` outweigh it. (Rejected alternative B:
`@TransactionalEventListener(AFTER_COMMIT)` — cleaner separation but extra machinery for a
single email; YAGNI.)

---

## Components & Changes

### 1. Backend — `EmailService.sendVoteCreatedConfirmation(VoteSession session, String siteUrl)`

New public method, modeled on `sendVoteResult` (`EmailService.java:121-156`):

- `helper.setFrom(fromEmail)`, `helper.setTo(session.getInitiatorEmail())`.
- Subject: `"Your group vote for " + session.getDestination().getName() + " is live"`.
- Builds two URLs (same `siteUrl` base as `sendVoteResult`):
  - **invite link** — `siteUrl + "/vote/" + shareToken + "/activities"` (what friends use).
  - **dashboard link** — `siteUrl + "/vote/" + shareToken + "/waiting?manager=" + managerToken`.
- Thymeleaf context variables: `session`, `inviteUrl`, `dashboardUrl`, `supportEmail`
  (= `"support@trivlu.com"`), plus pre-formatted `startDate`/`endDate`/`expiresAt` strings
  (formatted in Java with `DateTimeFormatter`, matching how `sendItineraryConfirmation`
  formats `bookingDate`, so the template stays logic-free).
- Processes template `"vote-created"`.
- Logging uses the existing `maskEmail(session.getInitiatorEmail())` helper.
- On failure: `log.error(...)` and throw `EmailSendException` (consistent with the other
  methods). The caller decides whether that is fatal.

### 2. Backend — new template `templates/email/vote-created.html`

Visually identical to the two **customer-facing** templates (`vote-result.html` and
`itinerary-confirmation.html`), which share one house style. Reuse their CSS **verbatim**:
`body` (Arial, `#f0f0f0` page bg, `line-height: 1.6`), `.container` (600px white, rounded),
`.header` (`#6A1B9A`, white logo 56px, `h1` 22px/700 + muted sub-line), `.content` (30px),
`.footer` (`#4A148C`, white logo 36px, muted 12px lines incl. the existing
"For support, contact us at support@trivlu.com"), plus `.cta-button` (from `vote-result`)
for the dashboard button and `.section` (from `itinerary-confirmation`: left border
`#6A1B9A`, `#f8f9fa` bg) for the "How it works" block. The internal
`contact-notification.html` is **not** a style reference — it is an admin notification with
a lighter, off-palette variant (stray `#667eea`). Sections:

- **Header:** logo, `"Your group vote is live!"`, sub-line `"Trip to {destination}"`.
- **Confirmation line:** destination, `{startDate} – {endDate}`, `{numberOfTravelers}`
  travelers, and that voting closes automatically in 24 hours (`{expiresAt}`).
- **"How it works" — 3 numbered steps:**
  1. Share the invite link with your group — they swipe to vote on the activities.
  2. Track progress any time on your vote dashboard (live count + countdown).
  3. When the timer ends — or you end it early — we tally the votes and email you the
     final itinerary to open in Trip Builder.
- **Invite link block:** the `inviteUrl` rendered as selectable/copyable text in a bordered
  box, labelled "Your invite link — send it to your group".
- **Primary CTA button:** `"Open your vote dashboard"` → `dashboardUrl`.
- **Reassurance line:** the final results will be emailed to this address automatically.
- **Support line:** `"Questions? Email us at support@trivlu.com"` as a `mailto:` link.
- **Footer:** same markup as `vote-result.html`.

### 3. Backend — wire into `VoteSessionService.createSession`

After the curated-activity and quiz-response rows are saved and before computing
`participantCount` / building the response (`VoteSessionService.java:144`), add:

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
```

`emailEnabled` and `siteUrl` fields already exist on the service
(`VoteSessionService.java:84-88`). `EmailService` is already injected.

### 4. Frontend — `VoteWaitingPage` reads `?manager=` from the URL

Today `isInitiator` and `managerToken` are read once from `localStorage`
(`VoteWaitingPage.js:8-9`). The dashboard link in the email carries the token in the query
string, so the page must adopt it:

- Import `useSearchParams` from `react-router-dom`.
- Convert `isInitiator` and `managerToken` from `const` to `useState` initialized from
  `localStorage` (lazy initializer).
- In a `useEffect` keyed on the search params: if a `manager` param is present,
  - write `localStorage['myhive-manager-{shareToken}'] = managerParam` and
    `localStorage['myhive-initiator-{shareToken}'] = 'true'`,
  - set the `managerToken` / `isInitiator` state,
  - then **strip the token from the URL** via `navigate('/vote/{shareToken}/waiting',
    { replace: true })` so the secret is not left in the address bar, history, or
    screenshots.
- Everything else (the "End voting early" button gated on `isInitiator` + `managerToken`,
  the close call) keeps working unchanged.

This makes the email's dashboard link grant full management on any device. On the device
that created the session, behavior is unchanged (the param is simply absent).

---

## Data / URL Reference

| Link | URL | In email as | Purpose |
|------|-----|-------------|---------|
| Invite | `{siteUrl}/vote/{shareToken}/activities` | copyable text | friends vote |
| Dashboard | `{siteUrl}/vote/{shareToken}/waiting?manager={managerToken}` | primary button | organizer monitors + ends early, any device |
| Support | `mailto:support@trivlu.com` | text link | support |

`managerToken` is a server-generated secret returned only to the organizer
(`VoteSessionResponse.managerToken`) and emailed only to the organizer's own
`initiatorEmail`; embedding it in the organizer's email link is acceptable, and the
frontend strips it from the URL on arrival.

---

## Edge Cases

- **`emailEnabled = false` (dev/test default):** no email sent; creation unaffected.
- **SMTP / template failure:** logged via masked email, swallowed in `createSession`; the
  API still returns the created session.
- **Email link opened on a new device:** `?manager=` populates `localStorage` and unlocks
  the "End voting early" button; token then removed from the visible URL.
- **Email link opened on the creating device:** no `manager` param (the in-app navigation
  doesn't add it), so nothing changes; existing `localStorage` tokens already apply.
- **`destinationSlug` present:** dashboard link always uses the `/vote/{token}/waiting`
  route (unlike `sendVoteResult`, which deep-links to Trip Builder — that only makes sense
  once results exist).
- **Missing optional data (`budget`):** not shown in this email, so no impact.

---

## Testing

### Backend — `EmailServiceTest` (extend existing)
- `sendVoteCreatedConfirmation` sets `to` = `initiatorEmail`, a non-empty subject naming the
  destination, and sends exactly one message (assert via mocked `JavaMailSender`
  `createMimeMessage` / `send`, matching the existing email-test style).
- The processed template/model carries the dashboard URL **including the `managerToken`**
  and the invite URL (assert the `Context` variables passed to a mocked `TemplateEngine`,
  or assert on the rendered HTML if the existing tests render real templates — follow
  whichever pattern `EmailServiceTest` already uses).

### Backend — `VoteSessionServiceTest` (extend existing)
- With `emailEnabled = true`, `createSession` invokes
  `emailService.sendVoteCreatedConfirmation` once with the saved session.
- With `emailEnabled = false`, it is **not** invoked.
- When `sendVoteCreatedConfirmation` throws `EmailSendException`, `createSession` still
  returns a valid `VoteSessionResponse` (creation is not rolled back / does not propagate
  the exception). Use `expected`-prefixed values where setup values are re-asserted.

### Frontend — `VoteWaitingPage` (new or extended test)
- When rendered at `/vote/{token}/waiting?manager={mgr}`, the component writes the token to
  `localStorage`, shows the "End voting early" button, and removes `manager` from the URL.
- When rendered without the param and without `localStorage`, the "End voting early" button
  is absent (non-initiator view).

---

## Out of Scope

- No change to `sendVoteResult`, the 24h scheduler, or session-close logic.
- No new endpoint; creation stays `POST /vote/sessions`.
- No live-chat widget — support is `mailto:support@trivlu.com` only (decided 2026-06-02).
- No resend/"lost my email" endpoint; the email is sent once at creation.
- No localization; email is English, matching existing templates.
