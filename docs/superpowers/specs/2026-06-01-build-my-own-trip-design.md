# "Build my own trip" from the curate finalize step — Design Spec

**Date:** 2026-06-01
**Status:** Approved

---

## Overview

After the organizer finishes swiping activities (`CuratePage`, finalize step), today the only forward action is **"Create & get link"**, which opens a vote session and asks the group to vote. But sometimes the organizer just curated a set for themselves and does not want to share or collect votes.

This feature adds a second forward action, **"Build my own trip"**, that drops the picked activities straight into the Trip Builder for the destination — no vote session is created, nothing is shared. The organizer's setup context (travelers, dates, budget) carries over so the Trip Builder shows the right per-person totals and budget remaining.

Frontend-only change. No backend changes, no `vote_session` row.

---

## User Flow

1. Organizer goes through setup → quiz → swipes activities as today.
2. On the finalize screen ("Your voting list (N)") they now see three actions:
   - **Start over** (unchanged)
   - **Build my own trip** (new) — solo path, no session
   - **Create & get link** (unchanged) — group voting path
3. Clicking **Build my own trip**:
   - Seeds the Trip Builder with `travelers`, `dates`, and `budget` from setup.
   - Adds every picked activity to the trip (deduped by id, appended to any existing trip).
   - Navigates to `/destination/:slug?tab=trip-builder`, landing directly on the Trip Builder tab with the picks already in the itinerary and the budget bar populated.

The button is disabled when nothing was picked (same guard as "Create & get link").

---

## Components & Changes

### 1. `AppContext` — add budget to trip-state

The Trip Builder budget panel is currently driven by a loaded vote-session result (`voteResult.budget`). To carry budget on the solo path (no session), budget becomes part of the persisted trip-state, alongside travelers and dates.

- `initialState`: add `tripBudget: null`.
- New reducer action `UPDATE_TRIP_BUDGET`:
  ```js
  case 'UPDATE_TRIP_BUDGET':
      return { ...state, tripBudget: action.budget };
  ```
- Persistence: include `budget` in the existing `myhive-trip-setup` localStorage blob (next to `travelers` / `startDate` / `endDate`), and read it back in the `useReducer` initializer. Budget then behaves exactly like travelers/dates — it survives a refresh, and is overwritten the next time a setup runs.

### 2. `TripBuilder.js` — single source of truth for budget

- The budget panel (currently gated on `voteResult && voteResult.budget != null`, using `voteResult.budget`) is changed to render on `state.tripBudget != null` and use `state.tripBudget`.
- In the vote-session loader effect (`?voteSession=...`), add `dispatch({ type: 'UPDATE_TRIP_BUDGET', budget: result.budget })` so the group path feeds the same source. The group path's visible behavior is unchanged.

### 3. `CuratePage.js` — the new button and handler

- Import and use `useContext(AppContext)` to obtain `dispatch` (not currently used in this file).
- Add **"Build my own trip"** to the `curate-finalize-actions` button row, disabled when `pickedActivities.length === 0`.
- New handler `handleBuildMyTrip`:
  1. `dispatch({ type: 'UPDATE_TRIP_TRAVELERS', travelers: setup.travelers })` (when present/positive, mirroring `VoteResultPage.handleOpenTripBuilder`).
  2. `dispatch({ type: 'UPDATE_TRIP_DATES', startDate: setup.startDate ?? '', endDate: setup.endDate ?? '' })`.
  3. `dispatch({ type: 'UPDATE_TRIP_BUDGET', budget: setup.budget ?? null })` — `null` budget simply means no budget bar.
  4. For each picked activity, `dispatch({ type: 'ADD_TO_TRIP', silent: true, activity: { id, name, price, slug, destinationSlug, imageUrl, categories: (a.categories || []).map(name => ({ name })) } })`. The `silent` flag + id-dedup keep the setup modal from popping and append to any existing trip.
  5. `navigate(\`/destination/${setup.destination.slug}?tab=trip-builder\`)`. `setup.destination` is the full destination object (from `state.destinations`), so it has `.slug`.

`setup.destination` shape is confirmed via `TripSetupModal` / `TripBuilderDropdown`: the destination passed into setup is a full destination object with `id`, `name`, `slug`.

---

## Data Mapping

Pool DTO (`VotePoolActivityDTO`) ships: `activityId`, `name`, `price`, `imageUrl`, `slug`, `destinationSlug`, `categories` (`List<String>`). On `CuratePage` these are already remapped so `card.id === activityId`. The trip item built from a pick uses `id`/`name`/`price`/`slug`/`destinationSlug`/`imageUrl` directly and converts `categories` (string array) to `[{ name }]`, matching how `TripBuilder` and `VoteResultPage.suggestionToActivity` expect categories. `description`, `includes`, `duration` are absent from the pool DTO and default downstream (e.g. booking submission defaults them to `''` / `0`).

---

## Edge Cases

- **Nothing picked:** button disabled, same as "Create & get link".
- **Budget not set in setup:** `setup.budget` is `null`; `tripBudget` is `null`; no budget bar — graceful.
- **Existing trip in localStorage:** picks are appended (id-dedup), not replaced. Consistent with how the app adds activities elsewhere.
- **Budget persistence side effect:** because budget persists in localStorage like travelers/dates, the bar remains until the next setup overwrites it. Accepted as consistent with existing travelers/dates behavior (decided during brainstorming).

---

## Testing

Frontend only (no backend code changes, so no new backend tests per the workflow rule).

- `AppContext.test.js`:
  - `UPDATE_TRIP_BUDGET` sets `tripBudget` without touching other trip fields.
  - `initialState.tripBudget` defaults to `null`.
- `CuratePage.test.js`:
  - After swiping right on activities and reaching finalize, clicking **"Build my own trip"** dispatches travelers/dates/budget updates and one `ADD_TO_TRIP` per pick, then navigates to `/destination/:slug?tab=trip-builder`.
  - Button is disabled when no activities were picked.

---

## Out of Scope

- No backend, no vote session, no sharing for this path.
- No change to the existing "Create & get link" group-voting flow beyond budget now flowing through `state.tripBudget` (behavior preserved).
