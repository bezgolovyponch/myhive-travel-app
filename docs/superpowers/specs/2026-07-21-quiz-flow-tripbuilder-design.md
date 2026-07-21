# Quiz Flow: Remove Finalize Screen, Land in Trip Builder

**Date:** 2026-07-21
**Status:** Approved

## Problem

The organizer quiz flow currently has a redundant screen. After the quiz
(`/vote/new/quiz`) and the swipe deck (`/vote/new/curate`), the organizer sees a
"Your voting list" finalize screen with three buttons (Start over / Build my own
trip / Create & get link). This screen duplicates what the Trip Builder already
does (show the selected list, let the user proceed to booking or voting).

## Solution

Remove the finalize screen. After the last swipe the organizer lands directly in
the Trip Builder (`/destination/:slug?tab=trip-builder`) with the picked
activities in the cart, quiz-based recommendations, and three actions:

- **Start Over** — restarts the quiz (same setup, question 1)
- **Complete Booking** — existing Trip Builder button
- **Let your mates vote** — one-click QUIZ vote session creation (no modal),
  same behavior as the removed "Create & get link" button

## Design

### 1. CuratePage (`src/pages/vote/CuratePage.js`)

After the last swipe, when ≥ 1 activity is picked:

1. Fire `shortlist_completed` (`selected_count`), guarded to fire once.
2. Seed `TripContext` exactly as the current `handleBuildMyTrip` does:
   travelers/dates/budget from `setup`, each picked activity via
   `ADD_TO_TRIP` (silent).
3. Persist quiz-flow context `{setup, responses}` to sessionStorage under
   `myhive-quiz-flow` (per-tab, survives refresh).
4. Navigate with `replace: true` to `/destination/:slug?tab=trip-builder` —
   the browser Back button from the Trip Builder returns to the quiz page
   (equivalent to Start Over), not to a half-finished deck.

When 0 activities are picked: stay on CuratePage with the existing
"You didn't pick anything" message + Start over button (deck reset).

Removed: finalize-screen JSX (grid, three buttons), history snapshot/restore
logic (`handleBuildMyTrip` stash, `restoreSnapshot`), `handleCreate`,
`ActivityPreviewModal` usage, and now-unused imports/state.

### 2. TripBuilder (`src/components/TripBuilder.js`)

A new util `src/utils/quizFlow.js` owns the sessionStorage key
(`readQuizFlow` / `writeQuizFlow` / `clearQuizFlow`).

Quiz mode is active only when a stored context exists **and**
`setup.destination.id === destinationId` (prop). In quiz mode:

- **Start Over** button (secondary, in the trip-actions block; also rendered in
  the empty-cart state while quiz mode is active): clears the cart (same reset
  dispatches as post-booking), clears the quiz-flow context, navigates to
  `/vote/new/quiz` with `{state: {setup}}`. Fires
  `cta_click {cta_label: 'Start Over', block: 'trip_builder'}`.
- **Vote button** keeps the label "Let your mates vote" but becomes one-click:
  instead of opening `StartGroupVoteModal`, call `voteApi.createSession` (QUIZ)
  with `initiatorEmail` from `setup.email`, `quizResponses` from stored
  `responses`, current cart values (`tripTravelers`, `tripStartDate`,
  `tripEndDate`, `tripBudget`), and the **current standalone cart item ids**
  (Trip Builder edits are reflected). On success: set
  `myhive-initiator-*` / `myhive-manager-*` localStorage markers, fire
  `vote_launched` (same payload as today: `trip_id` = shareToken,
  `user_role: 'organizer'`, `selected_count`), clear the quiz-flow context,
  navigate to `/vote/:token/waiting` with `managerToken` state. The existing
  `cta_click` on the button click and the existing guards (`canStartVote`:
  standalone items only, no foreign-destination items, no ended vote) are kept.
  A submitting state disables the button ("Creating…").
- **Recommended for you** section above "Browse More Activities", modeled on
  "Group suggestions": fetched via `voteApi.buildPool({destinationId,
  responses})`, includes left-swiped items; in-cart items show a disabled
  "Added" button; clicking an item name opens `ActivityPreviewModal` (keep the
  user in the flow).
- **`vote_skipped`** fires on the Complete Booking click (`handleConfirmTrip`)
  while quiz mode is active — deduped once per `trip_id` via sessionStorage
  (`myhive-vote-skipped-<tripId>`), payload unchanged
  (`trip_id`, `selected_count`).

Outside quiz mode the Trip Builder is unchanged (CART vote button + modal).
QUIZ sessions do not set `myhive-trip-vote-session` (parity with the current
`handleCreate`).

Quiz-flow context is cleared on: vote launch, booking submit
(`handleContactSubmit`), Start Over. Visiting another destination's Trip
Builder leaves the context stored but inactive (id mismatch).

### 3. Analytics mapping

| Event | Before | After |
|---|---|---|
| `quiz_completed` | quiz end | unchanged |
| `shortlist_completed` | finalize screen shown | last swipe, before navigating |
| `vote_skipped` | "Build my own trip" click | Complete Booking click in quiz mode (dedup per trip_id) |
| `vote_launched` | "Create & get link" | one-click vote button in Trip Builder |
| `trip_builder_viewed` / `booking_form_viewed` / `booking_submitted` / `cta_click` | Trip Builder | unchanged, fire automatically |

### 4. Testing

Frontend (Jest + RTL):
- `quizFlow` util: read/write/clear round-trip, malformed JSON tolerance.
- `CuratePage.test.js`: after last swipe → context seeded, sessionStorage
  written, `shortlist_completed` fired, replace-navigation to the Trip Builder;
  zero-picks keeps the empty state; removed-screen assertions deleted.
- Trip Builder quiz mode: one-click session creation (payload includes quiz
  responses + current cart ids), Start Over resets and navigates,
  `vote_skipped` fires once per trip on Complete Booking, Recommended-for-you
  renders pool items, inactive on destination mismatch.

Backend: no changes (`createSession` already exists), so no backend tests.
