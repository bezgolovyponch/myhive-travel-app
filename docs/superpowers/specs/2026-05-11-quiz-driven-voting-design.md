# Quiz-Driven Voting — Design Spec

**Date:** 2026-05-11 · **v2:** 2026-05-20 · **v2.1 / v2.2:** 2026-05-21
**Status:** Draft v2.2 — awaiting user review
**Supersedes parts of:** [2026-05-10-vote-together-design.md](2026-05-10-vote-together-design.md) (replaces the category-swipe step)

> **Revision note.** v1 personalized the pool **per participant**. v2 (2026-05-20) switched to **organizer-curation**: the organizer's quiz filters a pool, the organizer hand-picks the voting list, everyone votes the same list. v2.1 folded in a multi-agent review. **v2.2 dropped the `DRAFT` session state**: the organizer's whole setup is client-side and transient — a session is created, already `ACTIVE`, by a single atomic call once the organizer finishes curating. Abandon setup → no session exists. Rationale and rejected alternatives are in [Feedback history](#feedback-history).

---

## Overview

The `Vote Together` flow currently lets the initiator swipe **categories**; participants then swipe every activity in those categories. In Prague a single category (`Food & Drink`, 28 activities) overwhelms voters — "voting paralysis".

This redesign:

1. **Organizer takes a quiz** (3–5 destination questions). Answers carry signed weights toward categories → top-K categories.
2. The system builds a **filtered pool**: activities in those categories, ranked by `featured_weight`, **capped at 20**. Computed statelessly — no session yet.
3. The **organizer curates the voting list** — picks a subset of the pool.
4. The organizer finishes → **one atomic `POST /vote/sessions`** creates the session (`ACTIVE`), persisting the curated list and the organizer's quiz answers. The share link goes live.
5. **Participants** open the link, take the same quiz, then vote `Like / Skip` on the curated list.
6. At close, the result resolver does a **budget-greedy fill**. Activities the group's quiz points to (or, failing that, available activities by margin) feed a **suggestions** list.
7. The **organizer reviews** result + suggestions and manually decides what to add.

Paralysis is solved twice over: the quiz narrows 8 categories → 3, the `featured_weight` cap narrows to 20, and the organizer curates down to the voting list.

---

## Goals

- The voting list a participant sees is **short and curated**, not a raw category dump.
- Category selection becomes a **quiz signal**, not a blind initiator swipe.
- Give the business a single lever (`featured_weight`) to surface high-margin activities.
- Keep voting itself dead simple: `Like / Skip`, no vetoes, no budget input from participants.
- No half-built sessions: a session exists only when the organizer has fully set it up.

## Non-goals

- Per-participant personalized pools. v2 is organizer-curated; everyone votes the same list.
- Algorithmic package generation, supplier availability, schedule/time feasibility. See [Feedback history](#feedback-history).
- A time/duration budget in result resolution. **Time is not a planning constraint** (user decision, 2026-05-20) — the resolver is budget-driven only.
- ML, embeddings, hosted recommendation services. v2 is fully deterministic.
- Checkout / prepayment. Separate monetization workstream.
- A three-state vote (`LOVE/LIKE/SKIP`) or a `DRAFT` session state — both considered and dropped (see [Feedback history](#feedback-history)).

---

## Roles & User Flow

### Organizer — setup is client-side and transient; nothing is persisted until step 6

1. Opens **"Vote together & build a trip"**, fills `TripSetupModal`: destination, dates, travelers, **budget**, email. Held client-side.
2. `/vote/new/quiz` — answers the destination quiz (`GET /vote/destinations/{id}/quiz`). Answers held client-side.
3. Client → `POST /vote/pool` (destinationId + the quiz answers) → receives the ≤ 20 **filtered pool**. **Stateless — no session created.**
4. `/vote/new/curate` — picks the **voting list** from the pool.
5. Clicks **"Create & get link"**.
6. → **`POST /vote/sessions`** carrying everything: setup fields, budget, the organizer's `voterToken`, the organizer's quiz answers, and the curated `activityIds`. **This is the first and only write.** It creates the session (`ACTIVE`), persists the curated list and the organizer's quiz responses, and returns `shareToken` + `managerToken`.
7. Lands on `/vote/:shareToken/waiting` with the live share link.
8. On close → `/vote/:shareToken/result` — reviews result + suggestions, manually adds suggestions.

If the organizer abandons setup at any point before step 6, **nothing was persisted — there is simply no session.** No `DRAFT`, no cleanup.

The organizer's quiz answers, persisted at creation, count in the group aggregate like any participant's. The organizer may also vote the list.

### Participant

1. Opens the share link → `/vote/:shareToken/quiz`.
2. Answers the quiz → `POST /vote/sessions/:shareToken/quiz`.
3. → `/vote/:shareToken/activities` — votes `Like / Skip` on the **curated list** (same list for everyone).
4. → `/vote/:shareToken/waiting`.

### Quiz fallback

If a destination has **no quiz configured**, `GET .../quiz` returns `{ questions: [] }`. The organizer skips the quiz; `POST /vote/pool` with no responses builds the pool from **all votable destination categories**, still capped at 20. Participants skip the quiz too.

---

## Data Model

### New tables

#### `quiz_questions`
| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `destination_id` | UUID FK → destinations | Cascade delete |
| `prompt` | VARCHAR(500) | Displayed text |
| `sort_order` | INT | Order within a destination |
| `created_at` | TIMESTAMP | |

Index: `(destination_id, sort_order)`.

#### `quiz_answers`
| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `question_id` | UUID FK → quiz_questions | Cascade delete |
| `label` | VARCHAR(200) | Displayed choice |
| `sort_order` | INT | Order within a question |

Index: `(question_id, sort_order)`.

#### `quiz_answer_weights`
| Column | Type | Notes |
|--------|------|-------|
| `answer_id` | UUID FK → quiz_answers | Cascade delete |
| `category_id` | UUID FK → categories | Cascade delete |
| `weight` | INT | Signed (`+2`, `-1`); an answer can pull or suppress a category |

PK: `(answer_id, category_id)`.

#### `vote_session_quiz_responses`
| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `session_id` | UUID FK → vote_sessions | Cascade delete |
| `voter_token` | UUID | From `localStorage`, same token used for `vote_activity_likes` |
| `question_id` | UUID FK → quiz_questions | |
| `answer_id` | UUID FK → quiz_answers | |
| `submitted_at` | TIMESTAMP | |

Unique: `(session_id, voter_token, question_id)`. One answer per voter per question; re-submission → 409. The organizer's responses are written here at session creation; participants' are written as they take the quiz. No organizer/participant flag is needed — every response counts equally in the group aggregate, and the pool is computed pre-session without persistence.

#### `vote_session_activities`
The organizer's curated voting list — the activities everyone votes on.

| Column | Type | Notes |
|--------|------|-------|
| `session_id` | UUID FK → vote_sessions | Cascade delete |
| `activity_id` | UUID FK → activities | |
| `activity_name` | VARCHAR(255) | **Snapshot** of the name at curation time |
| `price` | DECIMAL(10,2) | **Snapshot** of the per-person price at curation time |
| `sort_order` | INT | Display order in the swipe list |

PK: `(session_id, activity_id)`. Written once at session creation; frozen for the session. `activity_name` and `price` are **snapshotted** — the resolver and result read these, not live `activities` rows, so an admin re-pricing or renaming an activity mid-session does not change a running vote (mirrors how `BookingItem` snapshots package data).

### Modified tables

#### `activities`
Add column:
| Column | Type | Notes |
|--------|------|-------|
| `featured_weight` | INT NOT NULL DEFAULT 0 | **Triple duty** (see note). Existing rows default to `0`. |

> `featured_weight` deliberately does **triple duty**: (a) it gates the pool — only the top 20 by `featured_weight` survive the cap; (b) it is the margin signal — admins raise it for high-margin activities; (c) it sorts the suggestions list and breaks ties in the result ranking. Because it gates the pool *and* represents margin, the filtered pool skews toward high-margin activities. Accepted product decision (2026-05-20), not an oversight. Splitting "shop-window quality" from "margin" into two fields (`featured_weight` + `margin_score`) is the documented escape hatch — see [Open Questions](#open-questions).

#### `vote_sessions`
Add column:
| Column | Type | Notes |
|--------|------|-------|
| `budget` | DECIMAL(10,2) NULL | Group's willingness-to-spend (a **group total**), set by the organizer. A **soft reference** — not a cap, not a minimum. Nullable: a session without a budget resolves as "all positively-voted activities". When present, must be `> 0` (validated at creation). |

The session `status` enum is **unchanged** — `ACTIVE` then `COMPLETED`. There is no `DRAFT`: a session is created `ACTIVE` and only ever exists fully set up.

`vote_session_liked_categories` (from the old category-swipe flow) is **kept only for historical sessions**; new sessions never write to it. `VoteSession.likedCategories` is no longer a source of truth.

#### `categories`
Add column:
| Column | Type | Notes |
|--------|------|-------|
| `votable` | BOOLEAN NOT NULL DEFAULT true | When `false`, the category is structurally invisible to the voting subsystem — excluded from the quiz weights matrix, the pool, the suggestions query, and the all-categories fallback. For logistics categories like `Transfer` that are not a travel *preference*. Admin-managed; permanent. |

### Dropped from earlier drafts

`vote_session_participant_categories` (v1 per-participant snapshot) — removed; the organizer-curation model has no per-participant pool.

---

## Algorithm

### 1. Quiz → category snapshot

Pure function. Used for the **pool** (pre-session, organizer's answers) and for **suggestions** (group aggregate). It has **no internal fallback** — callers handle an empty result.

```text
snapshot(responses):
  acc: Map<CategoryId, Int> = {}
  for each response.answerId:
    for each (categoryId, weight) in quiz_answer_weights:
      acc[categoryId] += weight
  return acc.entries
            .filter(score > 0 AND category.votable)
            .sortByDescending(score, then category_id)   // deterministic
            .take(TOP_K)                                 // TOP_K = 3
            .categoryIds                                 // may be empty
```

### 2. Filtered pool — `POST /vote/pool` (stateless, pre-session)

```text
organizerCats = snapshot(organizer's quiz responses)
if organizerCats is empty: organizerCats = all votable destination categories   // pool safety net

SELECT DISTINCT a.*
FROM activities a
JOIN activity_categories ac ON ac.activity_id = a.id
WHERE a.destination_id = request.destinationId
  AND ac.category_id IN (organizerCats)        // votable-only by construction
ORDER BY a.featured_weight DESC, a.id
LIMIT POOL_CAP        // POOL_CAP = 20 (provisional)
```

`DISTINCT` because an activity can sit in several categories. The `LIMIT` makes the pool a predictable size — a category filter alone is unbounded: in Prague the three largest categories alone total 57 activities before de-duplication (see [Feedback history](#feedback-history)).

### 3. Session creation — `POST /vote/sessions`

The organizer's curated `activityIds` arrive together with the destination, budget, the organizer's `voterToken`, and the organizer's quiz answers. The backend, in one transaction:

- validates `numberOfTravelers` (1–50), `budget` is null or `> 0`, and `activityIds` is non-empty;
- validates **eligibility, not the capped window**: every `activityId` must still exist, belong to the destination, and belong to a votable category in `organizerCats` (400, naming the offending id, otherwise). `organizerCats = snapshot(quizResponses)`, falling back to **all votable destination categories** when empty (a no-quiz destination — `quizResponses` is empty) — the same safety net the pool builder applies in step 2. `POOL_CAP = 20` is only a display cap for `POST /vote/pool`; validating curated ids against the exact top-20 window would spuriously 400 the organizer if an admin edited `featured_weight` between their `/vote/pool` call and creation. Eligibility (destination + votable category membership) is stable against `featured_weight` edits; an admin *deleting* a curated activity in that window still fails the check, and the organizer re-curates.
- snapshots each curated activity's `name` and `price` into `vote_session_activities` (see [Data Model](#vote_session_activities)) so the resolver does not drift if an admin re-prices an activity mid-session;
- creates the `vote_sessions` row (`status = ACTIVE`), writes the organizer's `vote_session_quiz_responses`;
- returns `shareToken` + `managerToken`.

Re-deriving the pool inside the request removes the *quiz-answer* race; the *catalog* race (admin edits between `/vote/pool` and creation) is handled by validating eligibility rather than the capped window.

### 4. Result resolution — budget-greedy

`VoteSessionService.processSession` is reworked. **The 8h/day minute budget (`ACTIVITY_BUDGET_MINUTES_PER_DAY`) is removed** — time is not a planning constraint.

Votes are **two-state** (`Like` / `Skip`) — stored in the existing `vote_activity_likes.liked` boolean (`true` = Like, `false` = Skip). No new enum, no schema change.

```text
on session resolution:
  for each curated-list activity:
    likeCount = COUNT(vote_activity_likes rows for this activity WHERE liked = true)
    skipCount = COUNT(vote_activity_likes rows for this activity WHERE liked = false)
    score = likeCount - skipCount        // a voter who never voted on it produces NO row
  ranked = activities with score > 0,
           sorted by (score DESC, featured_weight DESC, activity_id)   // fully deterministic

  result = []
  running = 0
  for a in ranked:
    groupCost = a.snapshotPrice * session.numberOfTravelers   // snapshot price per-person, budget per-group
    if budget == null OR running + groupCost <= budget:
      result.add(a); running += groupCost
    else:
      skip a and CONTINUE     // a cheaper lower-ranked activity may still fit
```

- **Skip-and-continue, not stop.** A non-fitting activity is skipped; the scan continues, so a cheaper lower-ranked activity can still take the remaining budget. Better budget use, at the cost of strict vote-rank ordering — accepted trade-off.
- **Tie-break.** Equal `score` → `featured_weight` desc → `activity_id`. With two-state voting ties are common; equal-vote activities are ordered by margin — the accepted consequence of dropping the third vote state.
- **Budget is per-group, price is per-person.** The fill and `totalPrice` use group cost = `snapshotPrice × numberOfTravelers`, where `snapshotPrice` is the price frozen in `vote_session_activities` at curation. `numberOfTravelers` (1–50) is enforced at creation.
- `likeCount` / `skipCount` are counts over **existing `vote_activity_likes` rows only** — "didn't vote" is not "skip". A voter who never reached an activity contributes nothing to either count.
- `budget == null` → every positively-voted activity enters `result`.
- Activities with `score ≤ 0` — more skips than likes, **including zero-vote activities** (`score = 0`) — are dropped from `result`.

### 5. Suggestions

```text
groupCats = snapshot(ALL quiz responses for the session)   // organizer + participants; may be empty

if groupCats is empty:
  quizSuggestions = []          // skip the query entirely — never run `category_id IN ()`
else:
  quizSuggestions =
    SELECT DISTINCT a.* FROM activities a
      JOIN activity_categories ac ON ac.activity_id = a.id
    WHERE a.destination_id = session.destination_id
      AND ac.category_id IN (groupCats)            // votable-only by construction
      AND a.id NOT IN (curated voting list)
    ORDER BY a.featured_weight DESC, a.id
    LIMIT SUGGESTION_CAP                           // SUGGESTION_CAP = 10 (provisional)

if quizSuggestions is empty:        // group gave no usable signal (all-neutral quiz, or no quiz)
  suggestions =
    SELECT DISTINCT a.* FROM activities a
      JOIN activity_categories ac ON ac.activity_id = a.id
      JOIN categories c ON c.id = ac.category_id
    WHERE a.destination_id = session.destination_id
      AND c.votable = true
      AND a.id NOT IN (curated voting list)        // same exclusion as quizSuggestions
    ORDER BY a.featured_weight DESC, a.id
    LIMIT SUGGESTION_CAP
else:
  suggestions = quizSuggestions
```

Notes:
- Suggestions are sorted **by `featured_weight` (margin)** — a deliberate decision (2026-05-20). They carry **no vote counts**.
- **Fallback** (user decision, 2026-05-21): when the group's quiz gives no usable signal, suggestions are *available activities not in the curated list, ranked by margin* — rather than an empty panel. The fallback excludes the **whole curated list** (same as `quizSuggestions`), so activities the group voted *down* are not re-suggested.
- A high-`featured_weight` pool activity the organizer skipped during curation re-surfaces near the top of suggestions. Accepted consequence of margin-sorted suggestions (see [Open Questions](#open-questions)).

### 6. Organizer review

The organizer sees `result` + `suggestions` + `{ totalPrice, budget, remaining }`. Adding a suggestion appends `suggestion.price × numberOfTravelers` to `totalPrice` and re-computes `remaining` (`= budget − totalPrice`); `remaining` may go negative — allowed (budget is soft).

**Price source.** `result` activities carry the **snapshot** price (frozen in `vote_session_activities` at curation). `suggestions` were never curated and have **no snapshot** — their price is the **live catalog price**, and a suggestion added by the organizer enters the trip at that live price. Intentional: the snapshot protects the deal participants *voted on*; a suggestion was never voted on.

---

## API Surface

### New / modified public endpoints

#### `GET /vote/destinations/{destinationId}/quiz`
Returns the destination's questions + answers for the **organizer**, before any session exists. **Weights never exposed.**
```json
{ "questions": [ { "id": "uuid", "prompt": "Daytime hero or 4am legend?",
  "answers": [ { "id": "uuid", "label": "Daytime — in bed by midnight" } ] } ] }
```
Empty `questions: []` → no quiz; client skips the step.

#### `POST /vote/pool`  *(stateless — no session)*
Request: `{ "destinationId": "uuid", "responses": [ { "questionId": "uuid", "answerId": "uuid" } ] }` (`responses` empty if the destination has no quiz).
Response: the filtered pool (≤ 20) to curate from.
```json
{ "pool": [ { "activityId": "uuid", "name": "Tank Driving", "price": 150.00,
  "imageUrl": "...", "categories": ["Extreme"] } ] }
```
- Public and unauthenticated — covered by the global `RateLimitFilter` (100 req/min/IP); `responses` length is capped at the destination's question count (excess → 400).
- Being session-less, this endpoint and `GET /vote/destinations/{destinationId}/quiz` cannot sit under the current `@RequestMapping("/vote/sessions")` controller — they need a new controller or a `/vote`-rebased mapping.

#### `POST /vote/sessions`
Request:
```json
{
  "destinationId": "uuid", "initiatorEmail": "...", "numberOfTravelers": 8,
  "startDate": "2026-06-12", "endDate": "2026-06-14", "budget": 3000.00,
  "voterToken": "uuid",
  "quizResponses": [ { "questionId": "uuid", "answerId": "uuid" } ],
  "activityIds": [ "uuid", "uuid" ]
}
```
Creates the session (`ACTIVE`), persists the curated list (with `name`/`price` snapshots) + the organizer's quiz responses. Returns `shareToken` + `managerToken`.
- `400` — `numberOfTravelers` outside `1–50`; `budget ≤ 0` (if present); `activityIds` empty; an `activityId` not eligible (see [step 3](#3-session-creation--post-votesessions)); a `questionId` not in the destination's quiz, an `answerId` not belonging to its `questionId`, two answers for one question, or quiz responses incomplete when a quiz exists.
- `likedCategoryIds` removed; ignored if sent (tolerant for one release cycle).

#### `GET /vote/sessions/{shareToken}/quiz`
Same payload as the destination quiz, for participants (who hold the `shareToken`).

#### `POST /vote/sessions/{shareToken}/quiz`
Request: `{ "voterToken": "uuid", "responses": [ { "questionId": "uuid", "answerId": "uuid" } ] }`
- `200 OK` · `400` — a `questionId` not in this session's destination's quiz, an `answerId` not belonging to its `questionId`, two answers for one question, or missing answers · `409` already submitted, or session `COMPLETED` · `404` session missing.

#### `GET /vote/sessions/{shareToken}/activities`
Returns the **curated voting list** (same for everyone), ordered by `sort_order`.

#### `POST /vote/sessions/{shareToken}/votes` and `/votes/batch`
Unchanged shape and storage — two-state voting reuses the existing `liked` boolean (`true` = Like, `false` = Skip). Requires the session `ACTIVE`.

#### `GET /vote/sessions/{shareToken}/result`
```json
{
  "result": [
    { "activityId": "uuid", "name": "Tank Driving", "price": 150.00,
      "likeCount": 6, "skipCount": 2 }
  ],
  "suggestions": [
    { "activityId": "uuid", "name": "VIP Club Entry", "price": 150.00, "categories": ["Nightlife"] }
  ],
  "numberOfTravelers": 8,
  "totalPrice": 2400.00,
  "budget": 3000.00,
  "remaining": 600.00
}
```
- In `result`, `price` is the **snapshot** per-person price (frozen at curation); in `suggestions`, `price` is the **live** catalog price (never curated, never snapshotted). `totalPrice` is the group total of `result` (`Σ snapshotPrice × numberOfTravelers`).
- `remaining = budget − totalPrice` **only when a budget exists**; when the session has no budget, both `budget` and `remaining` are `null` (the formula is not applied). `remaining` may be negative.
- `suggestions` ordered by `featured_weight` desc; no vote counts.
- `409` if the session hasn't resolved yet.

> This **near-completely rewrites** the existing `VoteResultResponse` DTO — the old flat `activities` / `startDate` / `endDate` shape is replaced by the two-tier `result` + `suggestions` payload.

#### `POST /vote/sessions/{shareToken}/close`  *(existing endpoint)*
Guarded by `managerToken`. Resolves the session early (`ACTIVE → COMPLETED`, runs `processSession`). Unchanged from today except that `processSession` now uses the budget-greedy resolver.

> **Resolution trigger.** A session resolves either via this endpoint (the organizer, with `managerToken`) or automatically when the existing `VoteSessionScheduler` finds it past its 24h `expiresAt`. `processSession` runs in its own transaction; once `COMPLETED` is committed, `POST /votes` (which requires `ACTIVE`) rejects late votes with 409.

### New admin endpoints (ADMIN role)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/admin/destinations/{id}/quiz` | Full quiz incl. weights |
| `PUT` | `/admin/destinations/{id}/quiz` | Replace quiz transactionally |
| `POST` | `/admin/destinations/{id}/quiz/questions` | Add a question |
| `PUT` | `/admin/quiz/questions/{id}` | Edit prompt / sort_order |
| `DELETE` | `/admin/quiz/questions/{id}` | Cascade-delete answers + weights |

`featured_weight` is added to the activity admin DTO and CSV import as a **mutable, optional** column — absent → defaults to `0`, so previously-exported CSVs still import. The CSV parser must treat it as optional (not add it to `REQUIRED_COLUMNS`); the exporter emits the column going forward.

---

## UI

### New page `/vote/new/quiz` (organizer) and `/vote/:shareToken/quiz` (participant)
One question per screen, progress indicator (`2 / 4`), tap-to-advance. After the last answer: brief "Your picks point to **Nightlife · Extreme**" splash (1.5s), then redirect — organizer → `/vote/new/curate`, participant → `/activities`. No quiz configured → page self-skips.

### New page `/vote/new/curate` *(organizer, pre-session)*
Grid of the ≤ 20 pool activities with `[Add] [Skip]`, a live "Voting list (N)" tray. `[Create & get link]` fires `POST /vote/sessions`, then redirects to `/vote/:shareToken/waiting` with the share link.

### Existing pages — changes
- **`TripSetupModal`** (vote mode): adds a **budget** field. After confirm → `/vote/new/quiz`.
- **`CategoryVotePage`**: deleted; route `/vote/new/categories` removed.
- **`ActivityVotePage`**: votes the curated list with `Like / Skip` (existing `liked` boolean); passes `voterToken`.
- **Result page**: three blocks — voted result (with `6 👍 · 2 👎` like/skip counts), budget (`spent / budget / remaining`, or just `spent` when the session has no budget), suggestions (each with `[Add to trip]`). Replaces the current result page shape.

### Admin
- Activity edit form: `Featured weight` numeric field (default 0).
- Activity CSV: new mutable `featured_weight` column.
- Destinations admin: a "Quiz" tab — questions, answers, and a weights matrix (categories × answers, integer cells).

---

## Edge Cases & Decisions

| Case | Behavior |
|------|----------|
| Organizer abandons setup before `POST /vote/sessions` | Nothing was persisted — no session exists. No cleanup. |
| Participant opens a `COMPLETED` session's link | `GET /activities` still works (read-only); `POST /votes` → 409. |
| Participant retakes quiz | 409 — single-shot per voter. |
| Pool filter yields < 20 activities | Return whatever exists. No padding. |
| Organizer's quiz weights all ≤ 0 | Pool falls back to all **votable** destination categories (still capped at 20). |
| Group voted everything net-negative, or barely voted | Curated activities with `score ≤ 0` all dropped → `result` empty; organizer relies on suggestions. |
| A curated activity gets zero votes | `score = 0`, dropped from `result`. |
| Every positively-voted activity fits the budget | `result` = all of them; `remaining` > 0; suggestions still offered. |
| Group quiz gives no usable signal (all-neutral, or no quiz) | `quizSuggestions` empty → suggestions fall back to available activities not in the curated list, by `featured_weight`. |
| Fallback suggestions also empty (tiny destination, every votable activity already curated) | Suggestions panel is empty — a valid outcome; the result page shows result + budget only. |
| Session has no budget | Result page shows `spent` only — no budget / remaining line. |
| `numberOfTravelers` outside 1–50, `budget ≤ 0`, or empty `activityIds` at creation | `400` from `POST /vote/sessions`. |
| A curated activity becomes ineligible between `/vote/pool` and creation (admin edit/delete) | `POST /vote/sessions` returns 400 naming the offending id; the organizer re-curates. Eligibility is checked by destination + votable-category membership, not the top-20 window — a `featured_weight` edit alone never triggers this. |
| Participant takes the quiz but never votes | Quiz responses still count in `groupCats` (suggestions); absent votes create no `vote_activity_likes` rows — no effect on any `score`. |
| Admin re-prices or renames an activity mid-session | No effect on a running vote — `vote_session_activities` snapshots `name` + `price` at curation; the resolver and result read the snapshot. |
| Admin deletes an activity in a non-`COMPLETED` session's curated list | Blocked — `ActivityService.deleteActivity` raises a 409, same service-level pattern as the "activity used by a `Package`" rule. Needs a new `vote_session_activities` repository dependency in `ActivityService` and its **own** exception (`ActivityInUseException` is package-shaped — do not reuse it verbatim). |
| Admin edits `featured_weight` after a session is created | Curated list + votes frozen; only affects the suggestion ordering computed at close and future sessions. |

---

## Migration

1. **Schema** (`ddl-auto=update`): create 5 new tables; add `activities.featured_weight`, `vote_sessions.budget`, `categories.votable`. **No status-enum change** (no `DRAFT`). **No votes change** — two-state Like/Skip reuses the existing `vote_activity_likes.liked` boolean.
2. **Data**: none. In-flight sessions resolve on the old path or fall back gracefully.
3. **Seed**: ship a Prague quiz in `data.sql` (dev) + a one-off SQL script (prod) — see [Appendix A](#appendix-a--prague-seed-quiz). Mark the `Transfer` category `votable = false` in both dev seeds and a prod migration.
4. **Cleanup**: after one release cycle, drop `likedCategoryIds` from `VoteSessionCreateRequest`. `vote_session_liked_categories` retained for historical sessions only.

---

## Tests

### Backend (`myhive-backend/src/test`)
- **`QuizServiceTest`** (new): weighted aggregation, signed weights, top-K, deterministic tie-break, empty result returned as-is (no internal fallback).
- **`VotePoolTest`** (new): `POST /vote/pool` respects quiz top-K, the `votable` flag (non-votable categories like `Transfer` never appear), `featured_weight DESC, id` order, `LIMIT 20`; empty/no responses → all-votable-categories pool; < 20 returns all.
- **`VoteSessionCreateTest`** (new/extend): `POST /vote/sessions` rejects ineligible `activityIds` (400), empty `activityIds` (400), `numberOfTravelers` outside 1–50 (400), `budget ≤ 0` (400), cross-destination/malformed quiz responses (400); snapshots `name`/`price` into `vote_session_activities`; session created `ACTIVE`.
- **`VoteSessionResultTest`** (new/extend): budget-greedy fill is **skip-and-continue**; group cost uses `snapshotPrice × travelers` (unaffected by a live re-price); null budget → all positives; `score ≤ 0` (incl. zero-vote) excluded; deterministic tie-break by `featured_weight`; `remaining` may be negative.
- **`VoteSuggestionTest`** (new): quiz-based suggestions exclude curated-list ids, sorted by `featured_weight`; empty `groupCats` → no `IN ()` query; empty `quizSuggestions` → fallback to available-not-in-curated-list by margin; fallback may itself be empty.
- **`ActivityDeletionTest`** (extend): deleting an activity in a non-`COMPLETED` session's curated list → 409.
- **`QuizAdminControllerTest`** (new): ADMIN-only, transactional replace, cascade delete.
- **`ActivityImportTest`** (extend): `featured_weight` parsed from CSV, defaults to 0.

### Frontend (`myhive-react-app/src`)
- `QuizPage`: one-question flow, payload shape, 409 handling.
- `CuratePage`: pick/unpick, `POST /vote/sessions` payload, link reveal.
- E2E happy path: setup (with budget) → quiz → pool → curate → create → participant quiz → vote → result.

---

## Telemetry

`log.info` structured events: `quiz_completed` (`top_categories[]`), `session_created` (`pool_size`, `list_size`, `budget`), `vote_submitted` (`like/skip` counts), `result_resolved` (`result_size`, `total_price`, `suggestions_size`, `suggestions_fallback` bool).

Success criteria (4 weeks post-ship): vote completion ≥ 70%; curated list size lands in a sane band (informs the [Open Questions](#open-questions) list-size decision); suggestions-fallback rate low (a high rate means the quiz isn't separating groups).

---

## Open Questions

- **Curated voting list size.** No min/max enforced in v1. Telemetry on `list_size` informs whether to add a hard range (e.g. 6–12) or a soft hint. *Deferred — measure first.*
- **`POOL_CAP = 20` and `SUGGESTION_CAP = 10`** are provisional. Revisit once real pools are observed.
- **`featured_weight` triple duty** (pool gate + margin + suggestion/tie-break sort). Accepted for v1. Escape hatch: split into `featured_weight` + `margin_score`.
- **Budget-skipped liked activities vanish.** An activity the group liked but that didn't fit the budget is absent from both `result` and `quizSuggestions`. It *may* reappear via the suggestions fallback (it is "not in the trip"), but only when `quizSuggestions` is empty. The organizer otherwise never learns the group liked it. Revisit if this proves to matter.
- **Suggestions re-surface curation rejections.** A high-margin activity the organizer skipped during curation reappears near the top of suggestions. Accepted as part of margin-sorted suggestions.
- **Vote score formula** (`likeCount − skipCount`, tie-broken by `featured_weight`) — tune from telemetry.
- **TOP_K = 3** — fixed in v1.

---

## Appendix A — Prague seed quiz

| Q | Prompt | Answers (weights) |
|---|--------|-------------------|
| 1 | Daytime hero or 4am legend? | **Daytime** (+2 Chillout, +1 Food & Drink) · **Mixed** (—) · **4am legend** (+2 Nightlife, +1 Hot babies and pranks) |
| 2 | Adrenaline rush or zero risk? | **Adrenaline** (+2 Extreme, +1 Guns & Bullets) · **Mixed** (—) · **Zero risk** (+2 Chillout) |
| 3 | How central is beer and food? | **All of it** (+2 Food & Drink, +2 Czech beer) · **Some** (+1 Food & Drink) · **Not central** (-1 Food & Drink, -1 Czech beer) |
| 4 | Stag mood: classy or unhinged? | **Classy** (-1 Hot babies and pranks) · **Spicy** (+1 Hot babies and pranks) · **Full send** (+3 Hot babies and pranks, +1 Nightlife) |

`Transfer` is marked `votable = false` (see [Modified tables](#modified-tables)) — logistics, not preference; structurally excluded from quizzes, pools and suggestions. It needs no answer weights.

---

## Feedback history

### Round 1 (2026-05-19) — "Consensus Sprint" proposal
The team's first counter-proposal had the system **algorithmically generate 5 packages**. Rejected and recorded because:

| Proposal | Why rejected |
|----------|--------------|
| Generate 5 packages from categories | No activity-level metadata to compose from (no `hero/recovery` roles, no time-of-day, no co-location). Multi-month project + full catalog re-tagging. |
| Supplier / schedule availability checks | `Activity` has only `price`, `duration`. No schedules, capacity, supplier APIs. |
| Targeted re-vote on "material change" | Heavy state machine for a marginal case. |
| Component substitutions | A third preference channel on top of quiz + votes — unjustified complexity. |
| Per-participant budget ceiling | Not captured; needs a privacy story; not the paralysis lever. |

### Round 2 (2026-05-20) — updated plan, adopted with changes
The team's second plan adopted the quiz and dropped the generator. Resolved with the user:

- **`budget`** = group's willingness-to-spend, a **soft reference** — not a minimum, not a target.
- **Result resolution is budget-greedy, not time-greedy.** The 8h-day budget is removed.
- **Model switched to organizer-curation.** The organizer's quiz filters the pool; the organizer curates the voting list; participants vote the same list. The v1 per-participant snapshot is dropped.
- **The "gap-fill" list = suggestions**, drawn from the group's aggregate quiz, **sorted by `featured_weight` (margin)** — a deliberate, upsell-shaped product decision, recorded as such.
- **`featured_weight` is the single margin field** — also the pool-cap gate. Consequence (pool skews high-margin) accepted.
- **Pool cap = 20.** A category filter alone is unbounded — in Prague the three largest categories alone (`Food & Drink` 28 · `Chillout` 15 · `Hot babies and pranks` 14) total 57 before de-duplication; full counts: `Extreme` 11 · `Nightlife` 9 · `Czech beer` 6 · `Guns & Bullets` 4 · `Transfer` 4.

Still **rejected**: per-activity bundle composition (package generation again); the 50% prepayment / checkout step (separate workstream).

### Round 3 (2026-05-21) — multi-agent review
- **Two-state voting (`Like` / `Skip`)**, not the three-state `LOVE/LIKE/SKIP` enum — reuses the existing `liked` boolean, **no enum migration**. Trip tie-breaks fall to `featured_weight` — accepted.
- **`budget` per-group / `price` per-person reconciled:** the resolver compares `price × travelers`.
- **Resolver confirmed skip-and-continue.** Deterministic tie-breakers added to snapshot top-K and result ranking.
- **Activity deletion blocked** while an activity sits in a non-`COMPLETED` session's curated list — service-level 409, mirroring the `Package` rule.
- Agents also caught: the status enum is `ACTIVE/COMPLETED` (no `CLOSED`); `numberOfTravelers` / `budget` needed validation — both folded into v2.2.

### Round 4 (2026-05-21) — drop `DRAFT`, suggestions fallback
- **No `DRAFT` session state.** The organizer's setup (quiz, pool, curation) is entirely client-side and transient; a session is created — already `ACTIVE` — by a single atomic `POST /vote/sessions` when the organizer finishes. Abandon setup → no session exists. This removes the `DRAFT` status, a cleanup job, the "organizer never curates" edge case, the leaked-link-during-`DRAFT` problem, and the need to identify the organizer's quiz responses. The pool is computed statelessly via `POST /vote/pool` before any session exists.
- **Suggestions fallback** (user decision): when the group's quiz yields no usable signal, suggestions are *available activities not in the trip, sorted by margin* — rather than an empty panel.

### Round 5 (2026-05-21) — second agent review
- **Curated-id validation by eligibility, not the top-20 window** — an admin editing `featured_weight` between `/vote/pool` and creation no longer spuriously 400s the organizer.
- **`vote_session_activities` snapshots `name` + `price`** — the resolver no longer drifts if an admin re-prices an activity mid-session (mirrors `BookingItem`).
- **Empty-`groupCats` guard** — the suggestions query is skipped (never `category_id IN ()`); the fallback excludes the whole curated list, so vote-rejected activities are not re-suggested.
- **Cross-destination quiz-response validation** added; `numberOfTravelers` bounded `1–50`; empty `activityIds` rejected; `remaining` formula null-guarded.
- **Resolution trigger documented** — `POST .../close` (managerToken) or the existing 24h scheduler; late votes rejected once `COMPLETED`.

### Round 6 (2026-05-21) — third agent review
- **No-quiz path fixed** — curated-id eligibility validation applies the same empty-`organizerCats` → all-votable-categories fallback as the pool builder.
- **Suggestion price is the live catalog price** (suggestions are never curated/snapshotted) — made explicit; `result` prices remain snapshots.
- CSV `featured_weight` documented as an **optional** column (default 0) so existing exports still import; the activity-deletion guard needs its own exception, not the package-shaped `ActivityInUseException`.

### Borrowed, scheduled for v1.5
Veto signal · natural-language "why this won" reason strings · taxonomy refinement (`Extreme`+`Guns & Bullets` → "Daytime action"; `Czech beer` → `Food & Drink`) · per-session **excluded categories** (`vote_session_excluded_categories` — an organizer opts one group out of e.g. adult-content categories; distinct from the global `Category.votable` flag). Vote counts on the *result* tier are already in v1.
