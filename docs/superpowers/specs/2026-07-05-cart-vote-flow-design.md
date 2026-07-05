# Cart Vote Flow ("Let your mates vote") — Design Spec

**Date:** 2026-07-05
**Status:** Draft — awaiting user review
**Related:** [2026-05-11-quiz-driven-voting-design.md](2026-05-11-quiz-driven-voting-design.md) (the existing QUIZ mode this feature extends)

---

## Overview

Today a group vote starts from a quiz: the organizer answers destination questions, curates a ballot from a category-filtered pool, and participants swipe Like/Skip. This spec adds a **second entry point**: the initiator builds a trip in the **Trip Builder** as usual, and on the itinerary panel (next to **Complete Booking**) clicks **"Let your mates vote"**. That creates a vote session whose ballot is the cart's standalone activities. Participants vote from a **list** (upvote-only, one vote per activity per person). When the vote closes — ended early by the manager or after 24 h — everyone sees a ranked results list styled after the homepage hero "Vote on activities" card (name + vote count + progress bar), and the initiator sees vote badges and vote-descending ordering **inside the Trip Builder itinerary**.

The vote is **advisory**: it never changes the cart. The initiator looks at the ranking and decides what to keep, then completes the booking through the normal flow.

Implementation approach (user-approved): **extend the existing `VoteSession` with a `voteMode` discriminator (`QUIZ` | `CART`)** rather than a parallel entity or masquerading through the quiz endpoint. ~80 % of the machinery (tokens, expiry scheduler, early close, likes table, result freezing, emails) is reused.

## Goals

- One-click path from a built cart to a shareable group vote — no quiz, no curation step.
- Dead-simple voting: a list, upvote any activities you like, max one vote each.
- Live tally (homepage vote-card style) visible to a participant **after** they cast their own vote.
- Vote results visible in the Trip Builder itinerary: per-activity vote badges, standalone items sorted by votes descending.
- Zero behavior change for the existing QUIZ flow.

## Non-goals

- Votes do **not** filter or reorder the persisted cart contents; no score > 0 cutoff, no budget knapsack for CART sessions.
- Packages are **not votable** (user decision): only standalone cart activities enter the ballot. Package groups render as today.
- No downvotes / skips in CART mode.
- No suggestions section, budget block, or PaymentActions on the CART result page — booking and payment stay in the Trip Builder flow.
- No multi-destination sessions: `VoteSession.destination` stays a single FK.
- No captcha on session creation (parity with the existing `POST /vote/sessions`; the global rate limit applies).

---

## Decisions (from brainstorming)

| Question | Decision |
|---|---|
| Voting mechanic | Upvote-only. A participant may vote for any number of ballot activities, max 1 vote per activity. Score = like count. |
| Effect of results | Advisory ranking only; cart unchanged; initiator decides. |
| Packages in cart | Excluded from the ballot. |
| Button placement | Itinerary panel ("Your Itinerary"), under/next to **Complete Booking**. |
| Initiator email | Collected in a one-field mini-modal ("We'll email you a private link to manage the vote"); required, as in QUIZ mode. |
| Interim results | Hidden until the participant votes; after voting, live tally on the waiting screen (poll every 30 s). |
| End of voting | Manager early close (existing `POST /close?managerToken=`) or 24 h expiry (existing 5-min scheduler). |
| Post-vote Trip Builder | Vote badges + descending sort (display-only) on standalone items. |
| Button label | **"Let your mates vote"** (echoes the hero copy "Your mates vote in 10 minutes"; deliberately not "Start Group Vote", which is the homepage quiz-flow CTA). |

---

## User flow

### Initiator

1. Builds a cart in Trip Builder. When the cart holds ≥ 1 standalone activity (packages don't count) and all standalone activities share one destination, the **"Let your mates vote"** button renders under **Complete Booking** (`TripBuilder.js`, `.trip-actions`). Mixed-destination standalone items → button disabled with an explanatory tooltip. Empty cart or packages-only cart → button hidden.
2. Click → mini-modal (built on `AppModal`): single email field, helper text, **Create vote** button. When the trip setup never captured dates (silent adds skip `TripSetupModal`), the modal additionally shows two required date inputs — the session's date columns are NOT NULL. Travelers come from the itinerary panel's existing input.
3. Submit → `POST /vote/sessions/cart`. On success: store `myhive-manager-{shareToken}` and `myhive-initiator-{shareToken}` in localStorage (existing conventions), plus a cart↔session link for later annotation (see § Trip Builder annotation), then navigate to `/vote/{shareToken}/waiting` — the existing lobby with share link, countdown to `expiresAt`, participant count, and the manager-only **End voting early** button.
4. A confirmation email goes to the initiator: invite link + `?manager=` dashboard link (existing adoption mechanism). For CART sessions the invite link targets the list page (`/vote/{shareToken}/activities`), not the quiz.
5. After close, the result page's initiator CTA **Back to Trip Builder** returns to `/destination/{slug}?tab=trip-builder&voteSession={shareToken}`; the itinerary shows badges and vote-sorted standalone items. The initiator completes the booking as usual (call-back or 30 % deposit).

### Participant

1. Opens the invite link → `/vote/{shareToken}/activities`. The page reads `voteMode` from `GET /vote/sessions/{shareToken}` and renders the **CartVoteList** (QUIZ sessions keep the swipe deck on the same route).
2. Each row: image, name, per-person price, an info button opening **`ActivityPreviewModal`** (stay-in-flow rule — never navigate away), and a ♥ toggle. Any number of rows may be toggled; each counts once.
3. **Submit vote** → `POST /vote/sessions/{shareToken}/votes/batch` with `liked: true` entries only → set `myhive-voted-{shareToken}` → redirect to the waiting page.
4. Waiting page (CART + has voted): renders **VoteTallyCard** — "{n} mates have voted", rows of name + like count + progress bar (fill = likeCount / participantCount) — refreshed with the existing 30 s poll, alongside the countdown and share button.
5. On `COMPLETED` (early close or expiry) the poll redirects to `/vote/{shareToken}/result`: the same tally card with prices, full ranking including zero-vote items.

### Trip Builder annotation (post-vote)

- Trigger: `?voteSession={shareToken}` in the URL **or** the stored cart↔session link (survives reloads and direct visits).
- TripBuilder fetches `GET /vote/sessions/{shareToken}/result`; if the session is CART and COMPLETED, it builds an `activityId → likeCount` map.
- Standalone items get a badge (♥ n + mini progress bar) and are **sorted by likeCount descending** (tie: original cart order). Display-only — `tripItems` in state/localStorage are not reordered or removed. Package groups render first, as today. Items missing from the ballot (added after the vote started) render last, unbadged.
- Hard guard: the existing `?voteSession=` hydration (which **replaces** cart items with QUIZ winners) must never run for CART sessions; branch on `voteMode` before hydrating.

---

## Backend design

### Schema

- `vote_sessions` gains `vote_mode VARCHAR NOT NULL DEFAULT 'QUIZ'` → enum `VoteMode { QUIZ, CART }`. Default follows the `consultation_requested` pattern (commit `466652c`) so `ddl-auto: update` adds the column in prod; existing rows become `QUIZ`.
- No new tables. Ballot → `vote_session_activities` (`sortOrder` = cart order; name + price snapshots as today). Votes → `vote_activity_likes` (CART writes `liked = true` only). Frozen results → `vote_session_result_activities`.

### Endpoints (all under `/vote/**`, permitAll; token-based access as today)

| Method | Path | Behavior |
|---|---|---|
| POST | `/vote/sessions/cart` | **New.** Body `{destinationId, initiatorEmail, numberOfTravelers, startDate, endDate, activityIds[]}` — travelers and dates are **required** (`vote_sessions` columns are NOT NULL and feed the confirmation email). Validates: destination exists; `activityIds` non-empty, deduped, every activity exists and belongs to the destination (no quiz-category check); email present/valid; endDate ≥ startDate. Creates an `ACTIVE` session with `voteMode = CART`, `expiresAt = now(UTC) + 24h`, snapshots the ballot, sends the confirmation email. Returns the standard `VoteSessionResponse` incl. `managerToken` (creation only). 201. |
| GET | `/vote/sessions/{shareToken}` | Response gains `voteMode`. |
| GET | `/vote/sessions/{shareToken}/activities` | Reused unchanged — `VoteActivityResponse` already exposes `imageUrl`, which the list UI needs. |
| POST | `/vote/sessions/{shareToken}/votes` and `/votes/batch` | Reused (upsert per voter+activity, `maxParticipants` guard). CART sessions reject any `liked = false` entry → 400 `BadRequestException`. |
| GET | `/vote/sessions/{shareToken}/tally?voterToken=` | **New.** CART sessions only (QUIZ → 409). Caller must have voted in this session (`existsBySessionIdAndVoterToken`) **or** present a valid `managerToken` (query param, like `/close`) → otherwise 403. Returns `{participantCount, status, expiresAt, rows: [{activityId, name, price, likeCount}]}` via the existing `findVoteCountsBySessionId` aggregate. Works for ACTIVE and COMPLETED sessions. |
| POST | `/vote/sessions/{shareToken}/close?managerToken=` | Unchanged. |
| GET | `/vote/sessions/{shareToken}/result` | Reused; CART branch below. |

### Service changes (`VoteSessionService`)

- `createCartSession(request)` — mints `shareToken`/`managerToken`, no quiz responses, no category validation, persists ballot rows in cart order, sends the email.
- `processSession` — CART branch: rank **all** ballot activities by `likeCount` desc (tie-break: `sortOrder` asc), freeze the full ranking into `vote_session_result_activities`. No score > 0 filter, no budget knapsack. QUIZ path untouched.
- `castVote` / `castVotes` — reject `liked = false` when the session is CART.
- `getTally(shareToken, voterToken, managerToken)` — access rules above; aggregates like counts.
- `getResult` — CART: rows expose `likeCount` (skipCount is always 0), skip the suggestions computation (empty list) and the budget fields (null).
- `VoteSessionScheduler` — unchanged; both modes expire and clean up identically.
- `EmailService` — invite URL branches on `voteMode` (list page vs quiz page); templates otherwise reused.

## Frontend design

| File | Change |
|---|---|
| `services/voteApi.js` | + `createCartSession(payload)`, + `getTally(shareToken, voterToken)` (manager variant accepts managerToken). |
| `components/TripBuilder.js` | Vote button (visibility/disable rules above); email mini-modal (`AppModal` + shared `validators`); on success store tokens + cart↔session link, navigate to waiting. Post-vote annotation mode: badge + display-sort of standalone items; never hydrate cart from CART sessions. |
| `pages/vote/ActivityVotePage.js` | Branch on `session.voteMode`: CART → `CartVoteList`, QUIZ → existing swipe deck. Route unchanged. |
| **New** `components/vote/CartVoteList.js` | Voting list: image, name, price, info → `ActivityPreviewModal`, ♥ toggle, single **Submit vote** batch call. Guards: already-voted → redirect to waiting; session full → existing UX. |
| **New** `components/vote/VoteTallyCard.js` | Shared ranking card (header, "{n} mates have voted", rows: name + count + progress bar; optional price column for the result page). Own CSS, visually derived from the homepage `.vote-card` (those styles are scoped under `.homepage` and are not directly reusable). |
| `pages/vote/VoteWaitingPage.js` | CART: render `VoteTallyCard` when the visitor has voted (voterToken) **or** is the initiator (managerToken — the manager sees the live tally without voting; they authored the list, so bias isn't a concern). Poll `getTally` with the session poll (30 s). Existing countdown/share/early-close untouched. |
| `pages/vote/VoteResultPage.js` | CART branch: `VoteTallyCard` with prices; hide budget block, suggestions grid, and `PaymentActions`; initiator CTA **Back to Trip Builder** (with `?voteSession=` for annotation). |

localStorage: reuse `myhive-manager-{shareToken}`, `myhive-initiator-{shareToken}`, `myhive-voted-{shareToken}`, shared `voterToken` util; add `myhive-trip-vote-session` (the shareToken of the CART session created from the current cart) for post-reload annotation. Cleared when the cart is cleared after a completed booking.

## Edge cases

- Cart empty / packages-only → button hidden. Standalone activities across > 1 destination → button disabled + tooltip.
- Activity deleted from the catalog between carting and creating → `POST /cart` returns 400; message shown in the mini-modal.
- `maxParticipants` (default 50) → existing `SessionFullException` UX.
- Double vote: client blocks via `myhive-voted-*`; server upsert is idempotent per (voter, activity).
- Tally requested by a non-voter without manager token → 403; the UI never requests it pre-vote.
- The initiator may also vote through the share link — allowed, nothing special.
- Zero-vote activities stay in the ranking (bottom) and in the cart; nothing is auto-removed.
- Result before completion → existing 409 `ResultNotReadyException` handling.
- CART sessions with quiz endpoints: `GET /{shareToken}/quiz` and `POST /{shareToken}/quiz` are not part of the CART flow; participants are never routed there. (No server-side block needed — submitting quiz answers to a CART session is harmless and unreachable through the UI.)

## Testing

**Backend** (JUnit 5 + H2; project style: `expected`-prefixed variables, inline DTOs):
- `VoteSessionServiceTest`: `createCartSession` happy path + validations (foreign-destination activity, empty/duplicate `activityIds`, missing email); CART `processSession` full ranking incl. zero-vote rows and `sortOrder` tie-break; `liked = false` rejection in CART; `getTally` access (non-voter 403, voter OK, manager OK, QUIZ session 409) and count aggregation; CART `getResult` has no suggestions and null budget.
- Controller integration tests: `POST /vote/sessions/cart` (201 + managerToken; 400s), `GET /tally`, `voteMode` in session/activities responses.
- Regression: the entire existing QUIZ suite passes unmodified (`voteMode` defaults to QUIZ).

**Frontend** (Jest + RTL): button visibility/disable matrix; mini-modal validation, API call, 400 handling; `CartVoteList` select/submit/already-voted; `VoteTallyCard` counts and bar widths; waiting/result CART branches; Trip Builder badge + sort annotation and the no-hydration guard for CART sessions.

## Out of scope / future

- Votes influencing the booking automatically (top-N, filtering) — revisit after real usage.
- Voting on packages (would need ballot rows referencing `Package`).
- Live tally push (WebSocket/SSE) — polling is fine at this scale.
- Analytics events for the new flow (belongs to the GTM/dataLayer workstream).
