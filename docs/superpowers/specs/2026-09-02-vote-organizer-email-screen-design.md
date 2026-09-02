# Vote Organizer Email Screen + Progress Emails — Design

**Date:** 2026-09-02
**Source:** ticket "Email Screen (Vote flow)" (colleague) + clarification session
**Apps:** `myhive-react-app` (UI source, SSR'd by `myhive-next`), `myhive-backend`

## Problem

The group-vote funnel currently captures no organizer email. The organizer builds a
shortlist, clicks "Create vote", gets the invite link on the waiting page and is never
contacted again unless they book. The vote closes automatically after 24 h and the
result email exists (`vote-result.html`) but is skipped because `initiator_email` is
always null.

History correction: the email field was removed deliberately in PR #9 (commit
`8068972`, 2026-07-28, "collect organizer email at booking, not at vote creation"),
not by the Next.js cutover (2026-08-19). The backend kept `initiator_email` as a
nullable column (`V1__initiator_email_optional.sql`), and every email path already
branches on its presence — this feature re-enables that plumbing and adds two
progress emails.

## Decisions vs. the ticket

| Ticket says | Codebase reality | Decision |
|---|---|---|
| "after the Organizer's own vote is saved" | The organizer never casts a vote; their "vote" is the shortlist they turn into a session | Email screen = step 2 of the Create-vote modal, before the create request |
| "the Vote never closes and never notifies him" | Sessions expire 24 h after creation; the scheduler closes them and sends `vote-result.html` when an email exists | Email 3 = existing result email (subject + CTA changed) |
| Email 2 "No new vote for 24h" | Impossible inside a 24 h window | Fires once at the 12 h mark if not everyone has voted ("half-time" reminder) |
| "6 of 12 have voted" | No expected-voter count exists; `numberOfTravelers` is the only group size and the waiting page already shows "X voted of N" | Denominator = `numberOfTravelers` |
| "All voted or deadline reached → Results" | No auto-close on voter count (deliberate: organizer doesn't vote, one person on two devices counts twice) | No auto-close; results on deadline or manual close, as today |
| Exactly three emails | The creation-confirmation email (`vote-created.html`, invite + manager dashboard link) and post-close TripLead reminders (24 h / 72 h) already exist and switch on automatically once an email is present | Keep all of them (up to 6 emails per organizer over ~4 days) |
| "Nothing else on screen" | Previous modal carried `EmailConsentNote` (soft opt-in note) | Approved: no consent note on this screen (owner takes the legal side); the helper copy states the purpose |
| "Company legal name and address published on /about" | Data lives in `src/legal/companyInfo.js`, shown on Contact/Terms/Privacy, absent on About | Add a Company section to `AboutPage` |

## Scope

1. Two-step `StartGroupVoteModal` (QUIZ and CART — it is the single session-creation
   call site).
2. Backend: `email_captured_at`, two progress emails driven by the existing 5-minute
   scheduler tick, result email subject/CTA change.
3. Analytics events for the funnel.
4. About page company block.
5. EN copy per ticket; DE copy drafted, flagged for native review.

## 1. Frontend — `StartGroupVoteModal`

### Steps

- **Step 1 `details`** — unchanged: title "Start group vote", 24 h explainer, date
  inputs only when `needsDates`, primary button "Create vote". Clicking validates dates
  (existing `validate`) and, when clean, switches to step 2. **No request is sent.**
- **Step 2 `email`** — the ticket screen:
  - Modal title (rendered by `AppModal` header): **Your vote is saved.**
  - Subheading: **Where should we send the results?**
  - One `<input type="email" autocomplete="email" inputMode="email" autoFocus>`,
    56 px tall, id `start-vote-email`, labelled via `aria-label`.
  - Helper (14 px muted): *We will email you when everyone has voted. If the group
    stops responding, we will send you a reminder message you can paste into the
    chat.*
  - Footer: primary full-width button **Get the link for your group** (`btn--primary
    btn--full-width`, existing classes). While submitting: "Creating…" (existing key).
  - Nothing else: no checkbox, no back button, no skip, no consent note.
  - `AppModal`'s close button stays. Closing = abandoning vote creation (no session, no
    link); `modal_abandoned` fires with `has_email: Boolean(email.trim())` and
    `step: 'details' | 'email'`.
- No prefill: nothing in the browser holds a typed email (`myhive-trip-lead` stores
  only `{id, restoreToken}`).

### Styling (mobile first, single column)

New classes in `StartGroupVoteModal.css`, scoped under `.start-vote-modal--email`:
heading 28 px / 700 (override the modal title size via `contentClassName`), subheading
17 px muted, input 56 px with 16 px font (prevents iOS zoom), helper 14 px muted,
error 14 px red below the field. Reuse existing `.error-message`.

### Validation and states

| State | Behaviour |
|---|---|
| Empty / invalid format | Error under the field: "Please check the email address." Value kept, focus stays in the field (`inputRef.current.focus()`), `email_invalid_attempt` fires. No request. |
| Valid | `initiatorEmail: email.trim()` is added to the existing `createSession` / `createCartSession` payload. Rest of the success path unchanged (localStorage markers, `clearTripLead`, `vote_launched`, `onLaunched`, navigate to `/vote/{token}/waiting`). |
| Create fails | Existing `apiError` under the footer button ("Failed to create the vote. Please try again." or server message). Value kept, `submitting` reset, retry on tap. |

Email format check: move `EMAIL_RE = /\S+@\S+\.\S+/` from
`hooks/useEmailLeadCapture.js` into `utils/validators.js` as
`emailFormat(value, message)` (same shape as `required`/`slugFormat`) and make the
hook import it, so checkout and the vote screen share one rule. Server-side `@Email`
remains the authority; a server 400 surfaces as the create-failure state.

### Analytics (`pushEvent`, no PII)

| Event | When | Params |
|---|---|---|
| `organizer_voted` | step 1 → step 2 transition | `vote_mode`, `selected_count` |
| `email_screen_view` | step 2 first render (once per modal open) | `vote_mode` |
| `email_invalid_attempt` | client-side validation failure | `vote_mode`, `reason: 'empty' \| 'format'` |
| `contact_captured` | create request succeeded | `trip_id` (shareToken), `vote_mode`, `source: 'vote_email_screen'` |
| `link_revealed` | immediately after `contact_captured`, before navigation | `trip_id`, `vote_mode` |
| `vote_launched` | unchanged (GTM trigger) | unchanged |
| `modal_abandoned` | unchanged timing | `has_email` becomes real again; add `step` |

`link_revealed` fires from the modal because the waiting page is the only next
screen and the address is stored server-side at that point; firing it on
`VoteWaitingPage` mount would also count participants and return visits.

### i18n

New keys under `voteComponents.start.email.*` in `en.json` and `de.json`:
`title`, `sub`, `placeholder` ("you@email.com"), `helper`, `submit`, `errors.invalid`.
DE draft (flag for native review, as in PR #22):

- title: "Deine Stimme ist gespeichert."
- sub: "Wohin sollen wir die Ergebnisse schicken?"
- helper: "Wir mailen dir, sobald alle abgestimmt haben. Wenn die Gruppe nicht mehr reagiert, schicken wir dir eine Erinnerung, die du direkt in den Chat kopieren kannst."
- submit: "Link für deine Gruppe holen"
- errors.invalid: "Bitte prüf die E-Mail-Adresse."

### Rollback hook

If `link_revealed / email_screen_view < 0.69` after a month: add a ghost "Skip" link
on step 2 that calls the same create path with `initiatorEmail: null`. The backend
already accepts that (see §2), so the rollback is frontend-only.

## 2. Backend

### Contract

`initiatorEmail` stays **optional** on `VoteSessionCreateRequest` and
`VoteSessionCartCreateRequest` (`@Email`, nullable). The UI is the only gate.
Rationale: no deploy-order coupling (an old frontend bundle must not get 400s while
the Next build is still rolling), and the rollback above stays one-sided. Service
trims the value; blank → null.

### Schema (`vote_sessions`, all nullable, added by Hibernate `ddl-auto=update`; no Flyway)

| Column | Set when |
|---|---|
| `email_captured_at TIMESTAMP` | `newSession` when `initiatorEmail` is present (now = capture time) |
| `halfway_email_sent_at TIMESTAMP` | notifier sends email 1 |
| `reminder_email_sent_at TIMESTAMP` | notifier sends email 2 |

### `VoteProgressNotifier` (new `@Service`)

Invoked from `VoteSessionScheduler` in the existing `fixedDelay = 300_000` tick as a
separate `@Scheduled` method `sendOrganizerProgressEmails()` (own transaction per
session via `@Transactional` on the notifier's per-session method, mirroring
`TripLeadReminderScheduler` → `TripLeadReminderService`).

Gating (checked once per tick, before any query):
- `app.email.enabled=false` → return (a disabled mailer must not burn the one-shot
  `*_sent_at` markers).
- `app.vote.organizer-emails-enabled` (`VOTE_ORGANIZER_EMAILS_ENABLED`, default
  `true`) → kill switch for both progress emails only. The result email and the
  creation confirmation are unaffected.

Per-session rules (`N = numberOfTravelers`, `voters =
countDistinctVoterTokensBySessionId`):

| Email | Candidates (repository query) | Send when | Marker |
|---|---|---|---|
| 1 "halfway" | `status = ACTIVE and initiatorEmail is not null and halfwayEmailSentAt is null` | `voters >= ceil(N / 2) and voters < N` | `halfwayEmailSentAt = now` |
| 2 "reminder" | `status = ACTIVE and initiatorEmail is not null and reminderEmailSentAt is null and createdAt <= now - 12h` | `voters < N` | `reminderEmailSentAt = now` |

Edge cases: `N = 1` → threshold 1, `voters < 1` fails once anyone voted, so email 1
never fires; email 2 fires only if nobody voted by 12 h. `N = 2` → "1 of 2 have
voted". Both emails are independent; worst case both go out in the same tick (3 of
12 at 11:59, 6 of 12 at 12:01) — accepted. Once `voters >= N`, neither fires.

Marker is written and flushed **before** the send (same "stage advance must commit
even if the hand-off fails" rule as `TripLeadReminderService`); send is wrapped in
try/catch with `log.error`. `EmailService.send` is already async via
`AsyncMailSender`.

Repository additions on `VoteSessionRepository`:
`findByStatusAndInitiatorEmailIsNotNullAndHalfwayEmailSentAtIsNull(status)` and
`findByStatusAndInitiatorEmailIsNotNullAndReminderEmailSentAtIsNullAndCreatedAtBefore(status, cutoff)`.
Volume is tiny (sessions live 24 h, purged after 7 days).

### Standings for email 1

`findVoteCountsBySessionId` + `findBySessionIdOrderBySortOrder` (curated ballot),
ordered by likes desc then ballot order (extract the existing `cartRankingOrder`
comparator from `VoteSessionService` into a small package-private helper so the
notifier reuses it instead of duplicating). Rows: activity name (localized via the
session locale), like count. Shown for **both** vote modes — the organizer authored
the ballot in both since the 2026-07-21 handoff.

### `EmailService` additions

- `sendVoteHalfway(session, standings, frontendUrl)` → template `vote-halfway`,
  subject `email.voteHalfway.subject` = "{0} of {1} have voted".
- `sendVoteReminder(session, missingCount, frontendUrl)` → template `vote-reminder`,
  subject `email.voteReminder.subject` = "{0} people have not voted yet" (singular
  variant `email.voteReminder.subject.one` = "1 person has not voted yet").
- Both reuse `localeOf`, `localePrefix`, the dashboard URL builder from
  `sendVoteCreatedConfirmation` (extract `dashboardUrlFor(session, base)` and
  `inviteUrlFor(session, base)` as private helpers — three call sites now).
- Result email: `email.voteResult.subject` → "Results are ready" (DE: "Die Ergebnisse
  sind da"), `email.voteResult.cta.open` → "Book it" (DE: "Jetzt buchen"). Link
  target unchanged (`resultUrlFor`: CART → result page, QUIZ → Trip Builder
  hydration URL). Header/intro text unchanged.

## 3. Email content

House style = `vote-created.html` (purple header, `.section`, `.cta-button`,
footer). Every string via `#{...}` in `messages.properties` + `messages_de.properties`.

### `vote-halfway.html`

- Header: "{voters} of {N} have voted" · "Trip to {destination}".
- Intro: "Half your group has voted for {destination}. Here is where things stand
  right now — the final results come when the vote closes."
- Standings block (`.section`): table of activity name → "{likes} ♥" (likes only, no
  skips), ranked.
- CTA: "See live results" → dashboard URL (`/vote/{token}/waiting?manager={managerToken}`).
- Muted line: "Voting closes automatically on {expiresAt}."

### `vote-reminder.html`

- Header: "{missing} people have not voted yet" · "Trip to {destination}".
- Intro: "Voting for {destination} closes in about 12 hours and {missing} of {N}
  people have not voted yet. Paste this into your group chat:"
- Quote box (`.invite-box`, selectable): the paste text —
  EN: "Hey, {missing} of you still haven't voted for our {destination} trip. It takes
  a minute, voting closes tonight: {inviteUrl}"
  DE: "Hey, {missing} von euch haben noch nicht für unseren {destination}-Trip
  abgestimmt. Dauert eine Minute, das Voting endet bald: {inviteUrl}"
  (The "tonight"/"bald" wording is deliberately vague — the email may arrive at any
  hour.)
- CTA: "Open your vote dashboard" (reuse `email.voteCreated.cta.dashboard`).

### `vote-result.html`

Subject and CTA only (see §2). No marketing-consent line (ticket marks marketing
permission out of scope; revisit separately).

## 4. About page — Company block

`AboutPage.js` gets a final `<section className="about-section about-company">` with
heading `about.company.title` ("Company") and a definition list from `COMPANY`
(`legal/companyInfo.js`): legal name, address, company ID (IČO), registration,
contact email (mailto). Labels are i18n keys `about.company.*` (EN + DE:
"Unternehmen", "Firmenname", "Adresse", "IČO", "Registrierung", "Kontakt"). Values
come from the shared constant, never from the dictionaries. A render test pins the
legal name, as `TermsPage.test.js` does.

## 5. Error handling

- Client-side invalid email never reaches the API and never clears the field.
- Create-request failure keeps the modal on step 2 with the typed value.
- Notifier: per-session try/catch in the scheduler loop (log, continue) — one broken
  session must not starve the rest; marker commit precedes send.
- Email disabled (dev/test) → notifier no-ops and markers stay null. Re-enabling the
  mailer therefore sends email 2 to every still-ACTIVE session older than 12 h in one
  tick. Sessions live 24 h, so that burst is bounded to the last 12 h of sessions —
  accepted, same trade-off as the TripLead reminder kill switch.

## 6. Testing

Backend (JUnit 5, existing patterns — Mockito unit tests + `@SpringBootTest` render
tests):

- `VoteSessionCreateSessionTest` / `VoteSessionCartCreateTest`: `emailCapturedAt` set
  when email present, null otherwise; blank email normalised to null.
- `VoteProgressNotifierTest`: threshold math (N=12: 5 → no, 6 → yes, 12 → no; N=1
  never; N=2 at 1), marker written before send, send failure keeps marker, second
  tick does not resend, no-email/COMPLETED sessions excluded (query contract),
  reminder due at 12 h only, reminder skipped when `voters >= N`, kill switch and
  email-disabled short-circuit before any repository call.
- `VoteSessionSchedulerTest`: new scheduled method delegates per session and swallows
  per-session exceptions.
- `VoteHalfwayTemplateRenderTest` / `VoteReminderTemplateRenderTest` (mirror
  `VoteCreatedTemplateRenderTest`): EN + DE render, contains invite/dashboard URLs
  with locale prefix, standings rows, paste text, no unresolved `#{}` keys.
- `EmailServiceTest`: result subject "Results are ready" and CTA "Book it".

Frontend (`myhive-react-app`, Jest + RTL — `StartGroupVoteModal.test.js`):

- Step 1 with dates → step 2 renders heading, one email input, no other inputs, no
  consent note; `organizer_voted` then `email_screen_view` pushed.
- Empty / malformed email → error text, input keeps value and focus,
  `email_invalid_attempt` pushed, `createCartSession` not called.
- Valid email → `createCartSession` (and `createSession` for QUIZ) called with
  `initiatorEmail`; events in order `contact_captured`, `vote_launched`,
  `link_revealed`; navigation to waiting.
- API rejection → error shown, value kept, button re-enabled, retry calls API again.
- `modal_abandoned` carries `has_email: true` after typing, `step: 'email'`.
- `AboutPage.test.js`: company legal name and address rendered.

Manual: full flow in dev with `EMAIL_ENABLED=true` against Resend once (creation
email arrives, then force the notifier by lowering the 12 h constant locally or
inserting a backdated row via H2 shell), verify all three progress emails on a test
vote with two participants.

## 7. Rollout

- Single merge to `main`; both Render services deploy. No env change required
  (`VOTE_ORGANIZER_EMAILS_ENABLED` optional). No Flyway migration (nullable columns).
- Order-insensitive: old frontend + new backend keeps working because the email stays
  optional server-side; new frontend + old backend keeps working because the old
  backend already accepts and stores `initiatorEmail` (only the progress emails are
  missing until it catches up).
- After deploy: watch `email_screen_view`, `contact_captured`, `link_revealed` in GA4;
  review the 0.69 threshold after one month.
- Docs to update after approval: README (emails table), memory
  `project_vote_created_email` (email is captured again, where), `project_analytics_tracking`
  (5 new events), CLAUDE.md "Trip lead reminders"/vote paragraph.

## Out of scope

Participant email capture, marketing consent / newsletter, extending the 24 h vote
window, auto-close on voter count, removal of the unused single `castVote` endpoint,
per-organizer email frequency cap across vote + TripLead series (documented ceiling:
up to 6 emails over ~4 days).
