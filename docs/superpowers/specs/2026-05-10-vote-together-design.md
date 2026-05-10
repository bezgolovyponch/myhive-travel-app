# Vote Together & Build a Trip — Design Spec

**Date:** 2026-05-10  
**Status:** Approved  

---

## Overview

A group voting feature for the trip builder. The initiator swipes destination categories to filter activities, then swipes activities. A share link is generated after category swiping is complete. Friends open the link and swipe the same activities. After 24 hours the backend runs a greedy-fill algorithm, selects activities that fit the trip duration, and emails the result to the initiator. The initiator opens the result, edits if needed, and completes the booking normally.

---

## User Flow

### Initiator

1. Clicks **"Vote together & build a trip"** in `TripBuilderDropdown`
2. `TripSetupModal` opens in vote mode — enters dates, travelers, **email** (required only in vote mode)
3. Navigates to `/vote/new/categories` — swipes destination categories (stored in browser memory only, no DB yet)
4. After last category swiped → `POST /vote/sessions` with all data including `likedCategoryIds` → session created in DB with status `ACTIVE`, receives `shareToken`
5. Navigates to `/vote/:shareToken/activities` — swipes filtered activities; each swipe sends `POST /vote/:shareToken/votes`
6. After last activity → `/vote/:shareToken/waiting` — sees countdown timer, copy-able share link, participant count (polled every 30s)
7. After 24h — receives email with result link

### Friends

1. Receive share link (only exists after initiator completes category swiping)
2. Open `/vote/:shareToken` → redirect to `/vote/:shareToken/activities`
3. Swipe activities — same set as initiator
4. After last activity → `/vote/:shareToken/waiting` — sees countdown timer

### After 24 Hours (Backend)

`@Scheduled` job runs every 5 minutes:
1. Finds `ACTIVE` sessions where `expires_at < NOW()`
2. Runs greedy-fill algorithm
3. Saves result to `vote_session_result_activities`, sets status to `COMPLETED`
4. Sends email to `initiator_email`

Same job also cleans up: deletes `COMPLETED` sessions where `expires_at < NOW() - 7 days` (cascade deletes all related rows).

---

## Data Model

### New tables

#### `vote_sessions`

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | Internal ID |
| `share_token` | UUID UNIQUE | Used in all public URLs — never exposes internal `id` |
| `destination_id` | UUID FK → destinations | |
| `initiator_email` | VARCHAR | Receives result email |
| `number_of_travelers` | INT | For price calculation in email |
| `start_date` | DATE | |
| `end_date` | DATE | |
| `status` | VARCHAR | `ACTIVE` \| `COMPLETED` |
| `max_participants` | INT DEFAULT 50 | Guards against viral link abuse |
| `expires_at` | TIMESTAMP | `created_at + 24h` |
| `created_at` | TIMESTAMP | |

#### `vote_session_liked_categories`

| Column | Type | Notes |
|--------|------|-------|
| `session_id` | UUID FK → vote_sessions | Cascade delete |
| `category_id` | UUID FK → categories | |

PK: `(session_id, category_id)`. Determines which activities are shown to all participants.

#### `vote_activity_likes`

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `session_id` | UUID FK → vote_sessions | Cascade delete |
| `voter_token` | UUID | Anonymous participant ID from `localStorage` |
| `activity_id` | UUID FK → activities | |
| `liked` | BOOLEAN | `false` = dislike, stored for participation tracking |

Unique constraint: `(session_id, voter_token, activity_id)` — one vote per participant per activity. `INSERT ... ON CONFLICT DO UPDATE SET liked = excluded.liked` for idempotency.

#### `vote_session_result_activities`

| Column | Type | Notes |
|--------|------|-------|
| `session_id` | UUID FK → vote_sessions | Cascade delete |
| `activity_id` | UUID FK → activities | |
| `sort_order` | INT | Order in email and TripBuilder |

Populated by `@Scheduled` after expiry. PK: `(session_id, activity_id)`.

### `voter_token` lifecycle

Generated client-side with `crypto.randomUUID()` on first page load. Stored in `localStorage` under key `myhive-voter-{shareToken}`. Scoped per session — different sessions get different tokens.

---

## API Endpoints

All public, no authentication required. Protected by existing `RateLimitFilter` (100 req/min/IP).

| Method | URL | Description |
|--------|-----|-------------|
| `POST` | `/vote/sessions` | Create session. Body: `{destinationId, initiatorEmail, numberOfTravelers, startDate, endDate, likedCategoryIds[]}`. Returns `{shareToken, expiresAt}`. Validates that all `likedCategoryIds` belong to the destination. |
| `GET` | `/vote/sessions/{shareToken}` | Session info: `{status, destinationName, destinationSlug, expiresAt, participantCount}` |
| `GET` | `/vote/sessions/{shareToken}/activities` | Activities to swipe — filtered to those belonging to liked categories |
| `POST` | `/vote/sessions/{shareToken}/votes` | Cast vote. Body: `{voterToken, activityId, liked}`. On new `voterToken`, checks `DISTINCT voter_token COUNT < max_participants` — returns `403` if full. |
| `GET` | `/vote/sessions/{shareToken}/participant-count` | `{count}` — distinct voter tokens. Polled every 30s on waiting screen. |
| `GET` | `/vote/sessions/{shareToken}/result` | Returns ordered activity list after `COMPLETED`. Returns `404` if still `ACTIVE`. |

---

## Greedy-Fill Algorithm

Runs in `VoteSessionScheduler` (Spring `@Scheduled`, every 5 minutes):

```
tripDays       = DAYS.between(startDate, endDate) + 1
budgetMinutes  = tripDays × 480   // 8 hours of activities per day

likedActivities = SELECT l.activity_id, a.duration, COUNT(*) AS likes
                  FROM vote_activity_likes l
                  JOIN activities a ON a.id = l.activity_id
                  WHERE l.session_id = :id AND l.liked = true
                  GROUP BY l.activity_id, a.duration
                  ORDER BY likes DESC

selected = []
remaining = budgetMinutes
for each activity (by likes DESC):
    if activity.duration <= remaining:
        selected.add(activity)
        remaining -= activity.duration

persist to vote_session_result_activities (with sort_order)
set session status = COMPLETED
send result email to initiator_email
```

**Edge cases:**
- Only initiator voted — algorithm uses only their likes, works normally
- Nobody liked any activity — sends email with empty result and "No activities matched" message
- Session expires before initiator finishes activity swiping — scheduler processes whatever votes exist

---

## Email

Sent via existing `EmailService` (Resend). HTML template consistent with booking confirmation style.

- **Subject:** `Your group trip to {destinationName} is ready!`
- **Body:** list of selected activities (image, name, duration, price per person), total price × travelers, countdown notice
- **CTA button:** "Open in Trip Builder" → `https://trivlu.com/vote/{shareToken}/result`

---

## Frontend

### New routes

| Route | Component | Who |
|-------|-----------|-----|
| `/vote/new/categories` | `CategoryVotePage` | Initiator only |
| `/vote/:shareToken/activities` | `ActivityVotePage` | Initiator + friends |
| `/vote/:shareToken/waiting` | `VoteWaitingPage` | Initiator + friends |
| `/vote/:shareToken/result` | `VoteResultPage` | Initiator (from email link) |

### New components

- **`SwipeCard.js`** — Tinder-style card using `react-tinder-card`. Drag left = dislike (red ✕ overlay), drag right = like (green ♥ overlay). Button fallback for desktop. Progress indicator: "3 / 12".
- **`CategoryVotePage.js`** — Stack of category cards. Receives `destinationId` and vote setup data (dates, travelers, email) via React Router `location.state`. After last swipe → `POST /vote/sessions` → redirect to `/vote/:shareToken/activities`.
- **`ActivityVotePage.js`** — Stack of activity cards (image, name, price, duration). Each swipe → `POST /votes`. After last → redirect to `/waiting`.
- **`VoteWaitingPage.js`** — Countdown to `expiresAt`, copy share link button, participant count (polls `/participant-count` every 30s).
- **`VoteResultPage.js`** — Ordered activity list with total price. Button "Open in Trip Builder" → `/destination/:destSlug?tab=trip-builder&voteSession=:shareToken`.

### Changes to existing code

- **`TripBuilderDropdown.js`** — Add "Vote together & build a trip" button. On click: if trip dates not set, open `TripSetupModal` in vote mode; otherwise navigate directly to `/vote/new/categories` with setup data in state.
- **`TripSetupModal`** — Add `isVoteMode` prop. When true, show required email field. Normal flow unchanged.
- **`TripBuilder.js`** — Read `?voteSession=:shareToken` URL param. If present and session is `COMPLETED`, fetch result and auto-populate trip items.
- **`App.js`** — Register 4 new `/vote/*` routes.

---

## Security

- `share_token` (UUID v4) in all public URLs — internal `id` never exposed
- `max_participants = 50` default — prevents viral link abuse; enforced on `POST /votes` for new voter tokens
- `RateLimitFilter` (existing, 100 req/min/IP) — protects all endpoints
- Category IDs validated against destination on session creation — prevents cross-destination injection

---

## Out of Scope

- Real-time vote count updates (WebSocket) — 24h window makes polling sufficient
- Friend notifications when results are ready — only initiator gets email
- Multiple destinations per session
- Re-voting / changing a swipe decision
