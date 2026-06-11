# Frontend Phase 2 (Review Backlog) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Work through the phase-2 backlog from the 2026-06-11 frontend review: accessibility (shared modal behavior, keyboard cards, aria-labels), TripBuilder/AppContext fixes, admin polish, ChatPanel modernization, vote-page SEO, API error normalization, and test-tooling updates.

**Architecture:** Extract the proven focus-trap/Escape logic from `ActivityPreviewModal` into a `useModalA11y` hook and adopt it in every overlay. Move the global eager `GET /activities` out of `AppContext` into the one consumer that needs it (TripBuilder, per destination). Normalize `api.js` errors the same way `adminApi.js` already does (status + backend message).

**Tech Stack:** React 19, CRA, react-router 7, react-helmet-async, Jest + RTL. `@testing-library/user-event` upgraded to v14 in Task 9 (async API — `await` every interaction).

**Out of scope (phase 3):** CRA→Vite migration; `formatPrice`/`formatAmount` consolidation; admin inline-validation redesign; AppContext context-splitting.

**Conventions:** no wildcard imports; braces always. Run tests from `myhive-react-app/`: `npm test -- --watchAll=false <pattern>`. Commit after each task.

---

### Task 1: `useModalA11y` hook + adopt in all overlays

ActivityPreviewModal has correct dialog behavior (focus trap, Escape, focus restore, `role="dialog"`); TripSetupModal, ContactForm, SuccessModal and Layout's destination modal are plain divs.

**Files:**
- Create: `src/hooks/useModalA11y.js` (logic moved verbatim from `ActivityPreviewModal.js:5-78`)
- Modify: `src/components/ActivityPreviewModal.js` (use the hook)
- Modify: `src/components/TripSetupModal.js`, `src/components/ContactForm.js`, `src/components/SuccessModal.js`, `src/components/Layout.js:47-65`
- Test: `src/hooks/useModalA11y.test.js`

**Hook contract:**

```js
// useModalA11y(isOpen, onClose) -> contentRef
// While isOpen: traps Tab inside contentRef, closes on Escape (via a ref so an
// inline-arrow onClose doesn't retrigger the effect), focuses the first
// focusable on open, restores focus on close/unmount.
```

Implementation is the existing `ActivityPreviewModal` effect with `activity` replaced by the `isOpen` boolean; returns `contentRef`.

- [ ] Write `useModalA11y.js` + a test (renders a button-in-dialog, asserts Escape calls onClose and Tab wraps), run, PASS
- [ ] Refactor `ActivityPreviewModal` to `const contentRef = useModalA11y(!!activity, onClose);` — its own test must stay green
- [ ] Adopt in `TripSetupModal` (add `role="dialog" aria-modal="true" aria-labelledby`, `aria-label="Close"` on ×, hook with `isOpen`/`handleCancel`)
- [ ] Adopt in `ContactForm` (same; onClose = `onClose` prop)
- [ ] Adopt in `SuccessModal` (same; also drop the unused `import React`)
- [ ] Layout's destination modal: convert the `hidden`-class div to conditional render `{state.destinationModalOpen && (...)}` with the hook + dialog roles
- [ ] Full suite + commit

### Task 2: Keyboard access for cards, aria-labels, badge fix

**Files:** `src/components/ActivityCard.js`, `src/components/DestinationCard.js`, `src/components/TripBuilder.js`, `src/components/TripBuilderDropdown.js`, `src/components/Header.js`

- [ ] ActivityCard + DestinationCard: `role="button" tabIndex={0}` and `onKeyDown` (Enter/Space → same handler as click, `e.preventDefault()` on Space) on the card div; `aria-label` describing the card
- [ ] DestinationCard badge: `const badge = destination.rating >= 4.7 ? 'Popular' : null;` — the current else-branch labels every low/unrated destination "Hot Deal" (default decision: no badge below 4.7; flag to user in summary)
- [ ] TripBuilder: `aria-label={`Remove ${item.name}`}` on both × buttons (`:247`, `:284`); `htmlFor="trip-travelers"`/`id` pair on the Travelers input (`:215`)
- [ ] TripBuilderDropdown: `aria-label="Close"` on header ×, `aria-label={`Remove ${...}`}` on item/package ×
- [ ] Header hamburger (`:69`): `aria-label="Menu"`, `aria-expanded={mobileNavOpen}`
- [ ] Full suite + commit

### Task 3: TripBuilder vote-session effect + error surfacing

**Files:** `src/components/TripBuilder.js:55-98`

- [ ] Extract `const voteSession = searchParams.get('voteSession');` and depend on that string (currently the `searchParams` object identity re-runs the effect on every `?tab=` change and re-dispatches `UPDATE_TRIP_TRAVELERS`/`DATES`/`BUDGET`, clobbering user edits)
- [ ] Add `const [voteError, setVoteError] = useState(false);` — replace `.catch(() => {})` with `.catch(() => setVoteError(true))`; render in the right column:

```jsx
{voteError && (
    <p className="text-error">Couldn't load your group's vote results. Refresh the page to try again.</p>
)}
```

- [ ] Add cancelled-flag cleanup to the effect (dispatches after unmount)
- [ ] Full suite + commit

### Task 4: Drop the eager global `GET /activities` from AppContext

Every visitor on any page currently downloads the entire activity catalog. Only TripBuilder's browse panel reads `state.activities`.

**Files:** `src/context/AppContext.js:177-196`, `src/components/TripBuilder.js`, `src/context/AppContext.test.js` (check), grep for other `state.activities` / `SET_ACTIVITIES` consumers first

- [ ] Grep `state.activities` and `SET_ACTIVITIES` across src/ — confirm TripBuilder is the only consumer (if others exist, stop and reassess)
- [ ] AppContext: fetch only destinations in the mount effect; delete `SET_ACTIVITIES` case + `activities: []` from initialState
- [ ] TripBuilder: local `browseActivities` state fetched per destination alongside categories:

```js
useEffect(() => {
    if (!destinationId) return;
    let cancelled = false;
    api.getCategoriesForDestination(destinationId).then(c => { if (!cancelled) setCategories(c); }).catch(() => {});
    api.getActivities(destinationId).then(a => { if (!cancelled) setBrowseActivities(a); }).catch(() => {});
    return () => { cancelled = true; };
}, [destinationId]);
```

`filteredBrowseActivities` switches from `state.activities` to `browseActivities`. (Bonus: the browse panel now shows only the current destination's activities instead of the global list.)
- [ ] Update AppContext.test.js / DestinationPage.test.js state fixtures if they reference `activities`
- [ ] Full suite + commit

### Task 5: ChatPanel modernization

**Files:** `src/components/ChatPanel.js`, `src/components/ChatPanel.css` (send button style if needed)

- [ ] `onKeyPress` → `onKeyDown`
- [ ] Add a visible Send button (`type="button"`, calls `handleSendMessage`) next to the input
- [ ] `aria-label="Close chat"` on ×, `aria-label="Open chat assistant"` on the 🤖 trigger, `aria-label="Chat message"` on the input
- [ ] Clean up the reply `setTimeout` on unmount (store id in a ref, clear in an unmount effect)
- [ ] Un-hardcode Tenerife: auto-engage message → "Planning a stag do? Tell me what your group is into and I'll point you to the right activities." All canned responses rewritten destination-agnostic (no Tenerife/Teide/specific activity names with prices); keep the keyword-routing structure
- [ ] Full suite + commit

### Task 6: Vote pages — Helmet titles + noindex; inline styles → CSS

Share-token pages get pasted into group chats; they currently inherit whatever title Helmet last set, and the tokens shouldn't be indexed.

**Files:** all 5 files in `src/pages/vote/`, create `src/pages/vote/VoteWaitingPage.css` and `src/pages/vote/ActivityVotePage.css`

- [ ] Add to each page (titles: QuizPage "Group quiz", CuratePage "Pick activities", ActivityVotePage "Vote on activities", VoteWaitingPage "Voting open", VoteResultPage "Vote results" — all "… — Trivlu"):

```jsx
<Helmet>
    <title>Voting open — Trivlu</title>
    <meta name="robots" content="noindex"/>
</Helmet>
```

(Wrap page JSX in a fragment where needed; import from `react-helmet-async`.)
- [ ] VoteWaitingPage: move the inline style objects (`pageStyle` and the per-element blobs at lines 116-205) into `VoteWaitingPage.css` classes (`.vote-waiting-page`, `.vote-waiting-card`, `.vote-waiting-share-input`, `.vote-waiting-btn`, `.vote-waiting-close-btn`, etc.); keep the conditional `copied` background via a `--copied` modifier class
- [ ] ActivityVotePage: move `stateStyle` blobs (lines 75-97) into `ActivityVotePage.css` (`.vote-state`, `.vote-state--error`, `.vote-state-muted`)
- [ ] Full suite + commit

### Task 7: Admin polish

**Files:** `src/pages/AdminActivities.js`, `src/pages/AdminCategories.js`, `src/pages/AdminDestinations.js`, `src/pages/AdminPackages.js`, `src/pages/AdminBlog.js`, `src/pages/AdminDashboard.js` (check spinner pattern)

- [ ] **price=0 edit bug** (`AdminActivities.js:360`): Save is disabled via `!form.price`, but `mapItemToForm` puts the raw number in, so editing a €0 activity permanently disables Save. Change the disabled check to `form.price === ''` (same review for `!form.name`-style checks on numeric fields in other pages — `AdminPackages` already uses `form.discountPct === ''`, correct)
- [ ] **Per-row delete spinner** (`AdminCategories.js`): replace boolean `loadingUsage` with `loadingUsageId` (category id); each row's Delete shows spinner/disabled only for its own id (`disabled={loadingUsageId === category.id}`, others stay enabled)
- [ ] **openEdit race** (`AdminDestinations.js:88-97`): an admin opening Edit on A then quickly on B can get A's slow `api.getDestination` response overwriting B's category selection. Add a request-id ref: `const editSeqRef = useRef(0);` bump on each `openEdit`, apply `updateCategorySelection` only if still current
- [ ] **AdminPackages activity picker** (`:82-90`): replace the unpaginated `adminApi.getActivities()` + client filter with `adminApi.getActivitiesPaged(0, 200, form.destinationId)` (server-side filter; 200 covers any realistic destination) and add a cancelled flag so a stale destination's response can't populate the picker
- [ ] **Keep table during pagination**: in all 5 list pages change the early-return to `if (loading && items.length === 0)` so page changes don't unmount the whole table+filters (the rows update in place when the fetch lands)
- [ ] Full suite + commit

### Task 8: Normalize `api.js` errors (status + backend message)

`api.js` throws fixed strings, so users never see backend validation messages (booking, contact form) and callers can't branch on status. Mirror `adminApi.handleError`.

**Files:** `src/services/api.js`

- [ ] Add at the top:

```js
async function parseError(response, fallbackMessage) {
    let body = null;
    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
        try {
            body = await response.json();
        } catch (e) {
            body = null; // non-JSON error page — keep the fallback message
        }
    }
    const err = new Error((body && body.message) || fallbackMessage);
    err.status = response.status;
    err.body = body;
    return err;
}
```

- [ ] Replace every `if (!response.ok) throw new Error('<msg>')` with `if (!response.ok) throw await parseError(response, '<msg>');` (keep each existing message as the fallback)
- [ ] Full suite + commit (TripBuilder/ContactForm already render `error.message`, so backend messages now surface with zero UI changes)

### Task 9: Test tooling — devDependencies + user-event v14

**Files:** `package.json`, test files using `userEvent`

- [ ] Move `@testing-library/dom`, `@testing-library/jest-dom`, `@testing-library/react`, `@testing-library/user-event` from dependencies to devDependencies; upgrade user-event: `npm install -D @testing-library/user-event@^14`
- [ ] v14 makes all interactions async: grep tests for `userEvent.` and ensure every call is awaited (known non-awaited spot: `DestinationPage.test.js` click); run the full suite, fix any act/async failures
- [ ] `npm run build` still clean + commit

---

## Final verification
- [ ] `npm test -- --watchAll=false` all green
- [ ] `npm run build` succeeds, no new eslint warnings
- [ ] Code review of the full diff (multi-angle, per CLAUDE.md rule 1)

## Remaining for phase 3
CRA→Vite; `formatPrice`/`formatAmount` consolidation; AppContext split (dispatch/state contexts); admin inline validation messages + shared delete-handler (`mapDeleteError` option on useAdminCrud); SwipeCard clipboard fix shared helper; ContactForm footer submit via form attribute; admin/auth test coverage; `useAdminCrud` pageSize dep warning; CuratePage `responses` useMemo; ActivityVotePage `navigate` dep.
