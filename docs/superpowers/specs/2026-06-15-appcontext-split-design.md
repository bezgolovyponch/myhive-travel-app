# AppContext Split — Design

**Date:** 2026-06-15
**Phase:** 4 (sub-project 3 of 4)
**Status:** Approved, ready for implementation plan

## Context

Phase 4 of the frontend cleanup is four independent sub-projects, done one at a
time (separate spec + plan each), in this order:

1. Price formatter consolidation — shipped (`f32aebf`).
2. Admin inline validation — shipped (`d8ea965`).
3. **AppContext split** ← *this spec*
4. CRA → Vite migration (last, as a clean infra swap)

This spec covers only sub-project 3. It is a **pure refactor** — application
behavior must be identical before and after.

## Problem

`src/context/AppContext.js` is one `useReducer` holding four unrelated clusters
of state behind a single `{state, dispatch}` context:

- **Catalog** — `destinations`, `loading`, `error` (read-only after the on-mount
  `api.getDestinations()` fetch in the provider).
- **Trip** — `tripItems`, `tripTravelers`, `tripStartDate`, `tripEndDate`,
  `tripBudget`, plus the trip UI flags `tripSetupModalOpen` /
  `tripBuilderModalOpen`; persisted to `localStorage` (`myhive-trip-items`,
  `myhive-trip-setup`) via two effects, and seeded from `localStorage` in the
  reducer initializer.
- **Destination "Coming Soon" modal** — `destinationModalOpen`,
  `selectedDestination` (opened by `DestinationCard`, rendered by `Layout`).
- **Chat** — `chatOpen`, `chatMessages`, `autoEngaged` (consumed only by
  `ChatPanel`).

Every consumer subscribes to the whole context, so unrelated state changes (e.g.
a chat toggle) re-render every consumer. The four clusters have disjoint
consumers and no shared actions, so they are cleanly separable.

**Notable finding:** `ChatPanel` is the only consumer of the chat cluster, and
`ChatPanel` itself is not imported or rendered anywhere in the app (grep
confirms its only references are within `ChatPanel.js`). The chat cluster is
therefore dead in the live app today — localizing it carries zero behavioral
risk.

## Goal

Replace the single `AppContext` with three focused contexts plus a localized
chat, so each unit has one responsibility and consumers subscribe only to what
they use. No user-visible behavior changes.

## Decisions (from brainstorming)

- **Granularity:** three contexts — `CatalogContext`, `TripContext`,
  `DestinationModalContext` — plus **Chat localized into `ChatPanel`** (its own
  `useReducer`, no global state).
- **API shape:** each context keeps the existing `{state, dispatch}` shape and
  the **same action-type strings**. Consumers change only *which hook supplies
  `dispatch`/`state`*; their `dispatch({type: '...'})` calls are unchanged. This
  is the lowest-churn, lowest-risk path and matches the codebase's existing
  reducer style.
- **Composition:** a single `AppProviders` component nests the three providers,
  used by `App.js`.

## Design

### Architecture

```
<CatalogProvider>          destinations / loading / error  (+ on-mount fetch)
  <TripProvider>           trip data + trip modal flags     (+ localStorage)
    <DestinationModalProvider>  coming-soon modal
      <App/>
```

(The three providers are independent; nesting order is arbitrary. `ChatPanel`
holds its own state and needs no provider.)

### New files (`src/context/`)

Each context file exports: the raw `Context` (so tests can seed it via
`<XContext.Provider value={{state, dispatch}}>`, as today), its `initialState`
and `reducer` (so the reducer can be unit-tested), the `XProvider` component,
and a `useX()` hook that returns `{state, dispatch}` and throws a clear error
when used outside its provider (mirroring the existing `useAuth` pattern).

1. **`CatalogContext.js`**
   - State: `{destinations: [], loading: true, error: null}`.
   - Reducer actions: `SET_DESTINATIONS` (sets destinations, `loading: false`),
     `SET_ERROR` (sets error, `loading: false`), `SET_LOADING`.
   - `CatalogProvider`: `useReducer` + the on-mount `api.getDestinations()`
     effect (dispatches `SET_LOADING`/`SET_DESTINATIONS`/`SET_ERROR`), unchanged
     from today.
   - `useCatalog()`.

2. **`TripContext.js`**
   - State: `{tripItems, tripTravelers, tripStartDate, tripEndDate, tripBudget,
     tripSetupModalOpen, tripBuilderModalOpen}` (the trip slice of today's
     `initialState`).
   - Reducer actions (verbatim from today): `ADD_TO_TRIP`, `REMOVE_FROM_TRIP`,
     `SET_TRIP_ITEMS`, `ADD_PACKAGE_TO_TRIP`, `REMOVE_PACKAGE_FROM_TRIP`,
     `UPDATE_TRIP_TRAVELERS`, `UPDATE_TRIP_DATES`, `UPDATE_TRIP_BUDGET`,
     `SET_TRIP_SETUP`, `OPEN_TRIP_BUILDER_MODAL`, `CLOSE_TRIP_BUILDER_MODAL`,
     `CLOSE_TRIP_SETUP_MODAL`, `CANCEL_TRIP_SETUP`.
   - `TripProvider`: `useReducer` with the `localStorage` initializer (reads
     `myhive-trip-items` + `myhive-trip-setup`) and the two persistence effects,
     unchanged from today.
   - `useTrip()`.

3. **`DestinationModalContext.js`**
   - State: `{destinationModalOpen: false, selectedDestination: null}`.
   - Reducer actions: `OPEN_DESTINATION_MODAL`, `CLOSE_DESTINATION_MODAL`.
   - `DestinationModalProvider`: `useReducer`.
   - `useDestinationModal()`.

4. **`AppProviders.js`** — composes the three providers (above order); exported
   for `App.js`.

`src/context/AppContext.js` is deleted.

### Chat localization (`ChatPanel.js`)

Move the chat slice into `ChatPanel` as a local `useReducer`:
- Local initial state: `{chatOpen: false, chatMessages: [<the existing greeting
  message>], autoEngaged: false}`.
- Local reducer: `TOGGLE_CHAT`, `ADD_CHAT_MESSAGE`, `SET_AUTO_ENGAGED` (verbatim
  logic).
- The component's existing `state`/`dispatch` usage stays the same shape, now
  backed by the local reducer.
- `ChatPanel` reads `tripItems` from `useTrip()` (it currently reads
  `state.tripItems` for contextual responses).

### Consumer migration (16 source files)

Action-type strings are unchanged, so each consumer only swaps
`useContext(AppContext)` for the appropriate hook(s). Consumers reading two
clusters take two hooks and alias state, e.g.
`const {state: catalog} = useCatalog(); const {state, dispatch} = useTrip();`.

| File | Reads | Dispatches | Hook(s) |
|------|-------|-----------|---------|
| `App.js` | — | — | wraps with `AppProviders` |
| `Header.js` | destinations; tripItems, tripBuilderModalOpen | OPEN/CLOSE_TRIP_BUILDER_MODAL | `useCatalog` + `useTrip` |
| `Layout.js` | destinationModalOpen, selectedDestination | CLOSE_DESTINATION_MODAL | `useDestinationModal` |
| `DestinationCard.js` | — | OPEN_DESTINATION_MODAL | `useDestinationModal` |
| `ActivityCard.js` | — | ADD_TO_TRIP | `useTrip` |
| `ActivityDetailPage.js` | tripItems | ADD_TO_TRIP | `useTrip` |
| `PackageDetailPage.js` | — | ADD_PACKAGE_TO_TRIP | `useTrip` |
| `DestinationPage.js` | destinations | — | `useCatalog` |
| `TripBuilder.js` | trip slice | many trip actions | `useTrip` |
| `TripBuilderDropdown.js` | trip slice | trip actions | `useTrip` |
| `TripSetupModal.js` | tripSetupModalOpen; destinations, loading, error | SET_TRIP_SETUP, CANCEL_TRIP_SETUP | `useCatalog` + `useTrip` |
| `home/FeaturedActivitiesSection.js` | destinations; tripItems | — | `useCatalog` + `useTrip` |
| `useStartGroupVote.js` | tripItems; destinations | CLOSE_TRIP_BUILDER_MODAL | `useCatalog` + `useTrip` |
| `vote/CuratePage.js` | — | trip setup + ADD_TO_TRIP | `useTrip` |
| `vote/VoteResultPage.js` | (via children) trip actions | trip actions | `useTrip` |
| `ChatPanel.js` | tripItems | (chat → local) | `useTrip` + local reducer |

### Testing

- **Reducer unit tests:** split `AppContext.test.js` into `CatalogContext.test.js`,
  `TripContext.test.js`, `DestinationModalContext.test.js`, preserving the
  existing reducer-coverage (each test imports that context's `reducer` /
  `initialState`). Chat reducer logic, if currently covered, moves to a
  `ChatPanel` test or is covered by a ChatPanel render test.
- **Integration tests (5):** `TripSetupModal.test.js`, `DestinationPage.test.js`,
  `HomePage.test.js`, `vote/CuratePage.test.js`, `useStartGroupVote.test.js`
  currently wrap with `<AppContext.Provider value={{state, dispatch}}>`. Re-wrap
  each with the specific split provider(s) it needs, seeding only that cluster's
  state (e.g. a Catalog-only test wraps `<CatalogContext.Provider>`; a test
  needing both wraps both). Use the exported raw contexts.
- Full suite green; `npm run build` clean (no eslint warnings).

## Out of scope

- No API redesign (no semantic action methods) — `{state, dispatch}` + existing
  action strings are kept deliberately.
- `ChatPanel` is left in place (not deleted) despite being unmounted today;
  localizing keeps it self-contained for any future use. Deleting dead
  components is a separate decision.
- The final Phase-4 sub-project (CRA → Vite) gets its own spec and plan.

## Risks

- **Medium.** Large surface (16 source files + ~6 test files + provider wiring),
  but each change is mechanical and the action strings are unchanged. The main
  risks are (a) a consumer that reads two clusters wiring only one hook, and
  (b) a test seeding state into the wrong provider. Both surface immediately as
  failing tests. Because no action crosses cluster boundaries and behavior is
  unchanged, the existing test suite (after re-wiring its providers) is the
  safety net; the refactor is "green before, green after".
