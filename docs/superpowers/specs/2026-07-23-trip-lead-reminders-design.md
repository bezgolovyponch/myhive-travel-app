# Trip Lead Reminders — Design

**Date:** 2026-07-23
**Status:** Approved

## Problem

Users hand us their email in two flows, then leave without booking — and we never follow up:

1. **Quiz hole.** In the QUIZ vote flow the organizer enters their email in `TripSetupModal` *before* the quiz. The email lives only in `sessionStorage` (`myhive-quiz-flow`) and reaches the DB only if the user actually starts a vote afterwards. A user who completes the quiz, builds a cart in Trip Builder, and leaves is lost entirely.
2. **Vote without booking.** A vote session completes (`vote_sessions.initiator_email` is persisted, cart snapshot in `vote_session_activities`), the result email goes out — and nothing happens. No booking, no follow-up.

Additionally, the Trip Builder cart lives **only in browser `localStorage`** (`myhive-trip-items`, `myhive-trip-id`, `myhive-trip-setup` in `TripContext.js`). A reminder link that just points at `/trip-builder` shows an empty cart on any other device or browser.

## Goal

Capture these emails server-side, and send an abandoned-trip reminder series with a link that restores the user's trip on **any device**. Stop the series the moment the user books or starts a vote.

## Research summary (basis for cadence/content decisions)

- Industry consensus: **max 3 emails**, at **~1h → 24h → 72h** after abandonment; a 3-email series converts up to ~30% better than a single email; more than 3 raises spam-complaint risk.
- First email is the most valuable (50–63% open rates) and must be a **plain reminder without discount** (discounts in email 1 train deliberate abandonment).
- Travel-specific: reassurance ("we'll help you plan", flexible changes) and soft urgency outperform discounts; recovery benchmarks 8–12% of abandoned bookings, up to ~20% with personalization.
- **GDPR/ePrivacy:** cart-recovery email is *marketing*, not transactional. We rely on the Czech soft opt-in (Act 480/2004, own similar services) with a visible notice at capture time, an unsubscribe link in every email, and a suppression list.

## Decisions (agreed)

| Question | Decision |
|---|---|
| Audience v1 | (a) quiz-hole leads + (b) completed votes without booking |
| Series | QUIZ leads: 1h / 24h / 72h; VOTE leads: 24h / 72h after vote completion (result email already covers the first touch) |
| Consent UX | Notice line under the email field (no checkbox), unsubscribe in every email, suppression list |
| Architecture | Own `TripLead` entity + own `@Scheduled` job + existing `EmailService`/Resend. No external ESP. |
| Cross-device | Server-side snapshot per lead + `/trip-builder?restore=<token>` link that rehydrates the browser |
| Empty-cart leads | Snapshot is live-synced while the user works; the email content and restore behavior degrade gracefully (cascade below) |
| Incentives | No discounts anywhere in the series; email 2 offers the existing consultation/contact path instead |

## Data model (backend)

### `trip_leads`

| Field | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `email` | varchar, not null | normalized: trimmed, lowercase |
| `source` | enum `QUIZ` / `VOTE` (varchar 20) | |
| `restore_token` | UUID, unique, not null | authenticates PATCH + restore link |
| `unsubscribe_token` | UUID, unique, not null | used only in email links |
| `destination_id` | FK → destinations, nullable | |
| `number_of_travelers` | int, nullable | |
| `start_date` / `end_date` | date, nullable | |
| `budget` | numeric, nullable | |
| `quiz_responses_json` | text, nullable | raw quiz answers, enough to rebuild `myhive-quiz-flow` |
| `vote_session_id` | UUID, nullable | for `VOTE` leads (plain column, like `Booking.voteSessionId`) |
| `status` | enum `ACTIVE` / `CONVERTED` / `COMPLETED` / `UNSUBSCRIBED` (varchar 20) | |
| `reminder_stage` | int, default 0 | number of reminder emails already sent |
| `last_reminder_at` | timestamp, nullable | |
| `last_activity_at` | timestamp, not null | series anchor; refreshed on every capture/sync |
| `created_at` / `updated_at` | timestamps | `@CreationTimestamp` / `@UpdateTimestamp` |

**One ACTIVE lead per email** — an application-level invariant enforced in the service (no DB partial index; consistent with other app-level invariants under `ddl-auto=update`): `POST /leads` with an email that already has an ACTIVE lead **supersedes** it — the existing lead is marked `COMPLETED` and a brand-new lead (new id, new `restore_token`) is minted and returned. The service never hands back an existing lead's tokens from `create()`: a caller who only knows the email must not be able to read (`GET /leads/restore/{token}`) or overwrite (`PATCH /leads/{id}`) someone else's in-progress trip. Any restore link already emailed for the superseded lead keeps working (`restore()` does not filter by status) until the 30-day cleanup removes the row.

### `trip_lead_activities`

Mirror of `vote_session_activities`: `id` UUID PK, `trip_lead_id` FK (cascade delete), `activity_id` FK, `activity_name` (snapshot), `price` (snapshot), `sort_order`. Rewritten wholesale on each cart sync.

### `email_suppressions`

`id` UUID PK, `email` (unique, normalized), `created_at`. Checked before **every** reminder send. Rows are kept indefinitely (the opt-out record itself must persist). Suppression applies to the reminder series only — transactional email (vote created/result, booking confirmations) is unaffected.

## Capture flows

### QUIZ leads (frontend)

1. `TripSetupModal` (vote mode) submit → `POST /leads` `{email, destinationId?, numberOfTravelers?, startDate?, endDate?, budget?}` → `{id, restoreToken}`. Fire-and-forget from the UX perspective: a lead-capture failure must never block the quiz flow (log and continue).
2. Frontend stores `{id, restoreToken}` in **localStorage key `myhive-trip-lead`** (localStorage, not sessionStorage — the sync continues in Trip Builder tabs later).
3. **Debounced sync** (~2s) while `myhive-trip-lead` exists: `PATCH /leads/{id}` with `restoreToken` in the body, sending any of: `quizResponsesJson`, setup fields, `items: [{activityId, sortOrder}]`. The server snapshots `activity_name`/`price` from the catalog — client-sent prices are never trusted (same principle as `min_price`). Every successful PATCH refreshes `last_activity_at`.
4. On vote-session creation or booking submit success, the frontend removes `myhive-trip-lead` and stops syncing. (Server-side conversion detection below is the authoritative stop; this is hygiene.)
5. Token mismatch on PATCH → 404 (do not reveal lead existence).

### VOTE leads (backend only)

When `VoteSessionService.processSession` completes a session, create a `TripLead`: `source=VOTE`, email = `initiatorEmail`, `vote_session_id`, setup copied from the session, items copied from the session's **result** ranking (falling back to the ballot if no votes). Skip creation if a booking already exists for that `voteSessionId` or the email is suppressed. Lead creation must never fail the vote-completion transaction (catch + log, consistent with best-effort email callers).

No frontend changes for this flow.

## Restore (cross-device)

- Email CTA: `{siteUrl}/trip-builder?restore={restoreToken}`.
- Frontend on that param: `GET /leads/restore/{token}` → `{setup, quizResponsesJson, items[]}` where items carry **current** catalog data (id, slug, name, current price, minPrice, image, destination slug). Activities no longer in the catalog are silently dropped by the server.
- Rehydration cascade:
  1. Items present → write cart + setup into `myhive-trip-items` / `myhive-trip-setup` (fresh `myhive-trip-id`), open Trip Builder.
  2. No items but quiz answers present → rebuild `myhive-quiz-flow` in sessionStorage → Trip Builder shows the same recommendations the user saw.
  3. Neither → open Trip Builder for the lead's destination (or the default destination).
- If the local cart is non-empty, ask via the shared `AppModal` before overwriting ("Replace your current trip?").
- Restore works regardless of lead status (a late click on an old email is still a valid re-entry). Unknown token → 404 → frontend ignores the param and opens Trip Builder normally.

## Scheduler

`TripLeadScheduler` (next to `VoteSessionScheduler`), `@Scheduled(fixedDelay = 600_000)` (10 min):

- Cadence per source, measured from `last_activity_at`:
  - `QUIZ`: stage 1 at +1h, stage 2 at +24h, stage 3 at +72h.
  - `VOTE`: stage 1 at +24h, stage 2 at +72h.
- A lead is due when `status=ACTIVE`, `reminder_stage < maxStages(source)`, and `last_activity_at + threshold[reminder_stage + 1] <= now`.
- If the user comes back mid-series (PATCH refreshes `last_activity_at`), remaining stages re-count from the new anchor. `reminder_stage` never resets — **max 3 (QUIZ) / 2 (VOTE) reminder emails per lead, ever.**
  - **Deliberate exception:** a QUIZ lead that gets repurposed by `createFromVoteSession` (the user later starts and completes a vote from the same email) has its `source` switched to `VOTE` and `reminder_stage` explicitly reset to 0, starting the VOTE cadence fresh. This is intentional — the vote completion is a new, more-committed signal than the original quiz abandonment — but it means the same lead can receive up to 3 QUIZ emails followed by up to 2 more VOTE emails (5 total across the two series) in the worst case.
- **Stop conditions checked immediately before every send** (authoritative, DB-based):
  - a `Booking` exists with the same normalized email created after the lead's `created_at`, or (VOTE leads) any booking with the lead's `vote_session_id` → mark `CONVERTED`, skip;
  - (QUIZ leads) a `VoteSession` exists with the same `initiatorEmail` created after the lead's `created_at` → `CONVERTED`, skip;
  - email in `email_suppressions` → `UNSUBSCRIBED`, skip.
- Send path: existing `EmailService` (async via `AsyncMailSender`, fire-and-forget — same delivery guarantee as vote-result email). Stage increment + `last_reminder_at` update commit in the scheduler transaction.
- After the final stage → `status=COMPLETED`.
- **Kill switch:** `app.leads.reminders-enabled` (env `REMINDERS_ENABLED`, default `true`). Scheduler no-ops when the flag is off **or** `app.email.enabled` is false (so a disabled mailer never silently burns the series). Capture keeps working either way.
- Nightly cleanup (same cron slot pattern as `VoteSessionScheduler`): delete leads with `updated_at` older than 30 days, any status (GDPR retention). Suppression rows are never deleted.

## Emails

One adaptive Thymeleaf template `trip-reminder.html` (English, matching existing templates), from `noreply@trivlu.com`. Subject/content varies by stage and lead contents:

| Stage | Tone | Content |
|---|---|---|
| 1 (QUIZ +1h) | "Your {destination} trip is waiting" | Cart items with prices + total (group-minimum note where it binds, reuse existing pricing rules), or quiz-based recommendations (cascade), big restore CTA. **No discount.** |
| 2 (+24h) | Reassurance | "Need a hand planning?" — restore CTA + secondary CTA to the existing consultation/contact path. |
| 3 (+72h, QUIZ only) | Soft urgency | "Best dates and slots fill up early" — honest scarcity only, restore CTA, last email of the series. |
| VOTE 1 (+24h) / VOTE 2 (+72h) | "Your group voted — book the winners" | Ranked result items, restore CTA builds the winning cart. Same reassurance/urgency split as stages 2/3. |

Every reminder email includes:
- Unsubscribe link `{siteUrl}/unsubscribe?token={unsubscribeToken}` in the footer + company identity block.
- `List-Unsubscribe` + `List-Unsubscribe-Post: List-Unsubscribe=One-Click` headers (RFC 8058) pointing at the POST endpoint, so mail clients render their native unsubscribe control.

## Unsubscribe

- Frontend route `/unsubscribe?token=...`: page with a confirm button (GET must not change state — mail scanners prefetch links) → `POST /leads/unsubscribe` `{token}`.
- The RFC 8058 one-click header POSTs to `POST /leads/unsubscribe/one-click?token=...` (no body requirements from mail clients).
- Effect: add the lead's email to `email_suppressions`, mark **all** ACTIVE leads for that email `UNSUBSCRIBED`. Idempotent; unknown token → generic success page (don't leak validity).

## Consent notice (frontend)

Small muted line under the email input in **both** `TripSetupModal` (vote mode) and `StartGroupVoteModal`:

> We'll email you a link to your trip and a couple of reminders. Unsubscribe anytime.

(`StartGroupVoteModal` creates the vote session immediately, but its email later feeds VOTE-lead reminders, so the notice is required there too.)

## API surface (all public, behind `RateLimitFilter`)

| Endpoint | Purpose |
|---|---|
| `POST /leads` | create-or-refresh lead (dedup by email) |
| `PATCH /leads/{id}` | sync setup/quiz/cart; auth via `restoreToken` in body; 404 on mismatch |
| `GET /leads/restore/{token}` | snapshot with current catalog data |
| `POST /leads/unsubscribe` | body `{token}` — suppress + confirm |
| `POST /leads/unsubscribe/one-click` | RFC 8058 target, `token` as query param |

Abuse note: `POST /leads` lets anyone enter a third-party email — the exposure is equivalent to today's vote-session flow (which emails immediately); mitigations: rate limit, email dedup (one ACTIVE lead per address, enforced by **superseding** — see above), max 3 sends ever, suppression honored across leads. Superseding also closes a token-leak path: without it, a second `POST /leads` for an email that already has an ACTIVE lead would hand that lead's live `restore_token` (PII read + overwrite) to whoever merely typed the email.

## Testing

- **Service:** stage progression per source (1h/24h/72h vs 24h/72h), anchor refresh pushes remaining stages, stage cap, all three stop conditions (booking by email, booking by voteSessionId, vote by email, suppression), VOTE-lead creation on session completion (incl. skip-if-booked, never-fails-completion), email dedup on POST, catalog snapshot on PATCH (client price ignored), 30-day cleanup.
- **Controller:** create/patch/restore/unsubscribe — validation (`@Email`), token auth (404 on wrong token), restore returns current catalog data and drops deleted activities, unsubscribe idempotency.
- **Frontend (Jest):** restore hook cascade (items / quiz / bare), non-empty-cart confirm, debounced sync (fires, stops after `myhive-trip-lead` removal), lead capture failure doesn't block quiz.

## Rollout

1. Backend + frontend deploy in any order (feature is additive; scheduler is a no-op until leads exist).
2. Prod env: set `REMINDERS_ENABLED=true` (or leave default), verify `app.email.enabled` path.
3. `ddl-auto=update` creates the new tables in prod (same as previous features).
4. Post-launch: watch Resend deliverability + unsubscribe rate; revisit cadence if spam complaints appear.

## Out of scope (v1)

- On-blur email capture before submit (checkout `ContactForm`, modal typing) — legally grey, revisit later.
- Discount/promo-code incentives — no promo infrastructure; consultation CTA instead.
- SMS/WhatsApp touches, quiet-hours windows, per-locale email copy.
- Admin UI over leads (DB access is enough for v1).
