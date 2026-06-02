# Activity preview modal in the vote flow — Design Spec

**Date:** 2026-06-01
**Status:** Approved

---

## Overview

In the Tinder-style voting flow (`SwipeCard`) and in the organizer's finalize list
(`CuratePage`), the activity **name is a link** that opens the full activity page in a
new tab (`<a target="_blank">`). Clicking it pulls the user out of the swipe / trip-builder
flow.

This change replaces that navigation with an **informational popup** (`ActivityPreviewModal`)
that shows the activity's image, name, meta (price / duration / categories) and full
description, plus an unobtrusive **"View full page ↗"** link at the bottom (new tab) as an
escape hatch. The user stays in the flow.

The modal is **purely presentational**: no like/dislike inside it. Liking still happens only
on the swipe card itself and its buttons. The only thing the modal does is close (×, backdrop,
Esc) or follow the external link.

Touches frontend (new modal + two call sites) and a small backend DTO enrichment so the
organizer flow has a description and duration to show.

---

## User Flow

1. User is swiping activities (`SwipeCard`) — organizer curating (`CuratePage`) or participant
   voting (`ActivityVotePage`) — or looking at the finalize list on `CuratePage`.
2. They click the **activity name** (instead of being thrown to the activity page in a new tab):
   - A modal opens showing photo, name, meta line, and the full description.
   - Like/dislike controls are **not** in the modal — only on the card behind it.
3. They either:
   - **Close** the modal (× button, click on the backdrop, or `Esc`) and continue swiping / stay
     on the finalize list, or
   - Click **"View full page ↗"** to open the full activity page in a **new tab** (their choice
     to leave; the flow tab is untouched).

---

## Components & Changes

### 1. New component `ActivityPreviewModal`

`src/components/ActivityPreviewModal.js` + `src/components/ActivityPreviewModal.css`.

Reuses the existing modal pattern (`.app-modal`, `.app-modal-content`, `.app-modal-header`,
`.app-modal-body`, `.app-modal-close-btn`) so it matches `TripSetupModal` et al.

- **Props:**
  - `activity` — the activity object to preview, or `null`. When `null`, renders nothing
    (`if (!activity) return null;`), same convention as `TripSetupModal`.
  - `link` — href of the full activity page, or `null`/absent. When present, the footer shows
    the "View full page ↗" link.
  - `onClose` — called on ×, backdrop click, and `Esc`.
- **Layout (body):**
  - Image (`activity.imageUrl`) if present — omitted gracefully when absent.
  - Name (`activity.name`).
  - Meta line: `€{price}/person`, `{Math.round(duration / 60)}h`, and categories joined with
    ` · ` — each part rendered only when its value is present (mirrors `SwipeCard`'s meta line
    at `SwipeCard.js:121-125`). `categories` is a string array.
  - Divider, then the full `description` text. When description is empty/absent, show a muted
    placeholder ("No description yet.").
- **Footer:** `View full page ↗` anchor with `target="_blank" rel="noopener noreferrer"`,
  rendered only when `link` is truthy.
- **Close behavior:**
  - × button → `onClose`.
  - Backdrop (`.app-modal` outer) click → `onClose`; clicks inside `.app-modal-content` do not
    bubble to close (`stopPropagation`).
  - `Esc` key → `onClose` (keydown listener mounted while open, cleaned up on unmount).
- **Accessibility:** `role="dialog"`, `aria-modal="true"`, labelled by the name. The
  name-trigger at the call sites is a real `<button>` styled as a link (keyboard-focusable),
  with `aria-haspopup="dialog"`.

### 2. `SwipeCard.js`

- Today the name is rendered by `renderName` (`SwipeCard.js:64-74`) as an `<a target="_blank">`.
  Replace that anchor with a `<button>` styled as a link that opens the modal for the **current
  card**.
- `SwipeCard` owns a single local state variable holding which card to preview (`infoCard`,
  default `null`). This is just the open/closed flag for the modal — no added interactivity.
- Render `<ActivityPreviewModal activity={infoCard} link={infoCard ? getCardLink(infoCard) : null}
  onClose={() => setInfoCard(null)} />` once, at the page level inside `SwipeCard`.
- **Keep `stopPropagation`** on the name-trigger's pointer handlers (already present on the old
  anchor at lines 70-72) so opening the modal is not interpreted as the start of a swipe.
- Both consumers (`CuratePage` swipe deck, `ActivityVotePage`) get the popup automatically with
  no parent changes, since they already pass `getCardLink`.

### 3. `CuratePage.js` — finalize list

- The finalize-grid name (`CuratePage.js:190-201`) is also an `<a target="_blank">`. Replace it
  with the same `<button>`-as-link trigger that opens the modal.
- `CuratePage` owns a local `selected` state (default `null`) for the finalize-list modal, and
  renders one `<ActivityPreviewModal activity={selected} link={selected ? getCardLink(selected) :
  null} onClose={() => setSelected(null)} />` below the grid.
- `getCardLink` already exists on `CuratePage` (`CuratePage.js:64-69`) and is reused for the link.

### 4. Backend — enrich `VotePoolActivityDTO`

The organizer pool (`/vote/pool` → `VotePoolActivityDTO`) currently has **no `description` and
no `duration`** (`VotePoolActivityDTO.java`). Without them the curate-flow popup would have
nothing to show and the swipe meta line shows no duration. The participant flow
(`VoteActivityResponse`) already ships both.

- Add `description` (`String`) and `duration` (`Integer`) fields to `VotePoolActivityDTO`, placed
  to keep the constructor readable.
- Populate them in `VotePoolService.toDTO` (`VotePoolService.java:61-71`) from
  `activity.getDescription()` / `activity.getDuration()`.
- On the frontend these flow through automatically: `CuratePage` already spreads pool items with
  `{ ...a, id: a.activityId }` (`CuratePage.js:50`), so `description` and `duration` reach the
  card and the modal with no further mapping.

---

## Data Mapping

| Flow | Source DTO | description | duration | categories | Notes |
|------|------------|-------------|----------|------------|-------|
| Participant voting (`ActivityVotePage`) | `VoteActivityResponse` | already present | already present | **absent** | modal omits the category-chips line for participants (graceful) |
| Organizer curate (`CuratePage` swipe + finalize) | `VotePoolActivityDTO` | **added** | **added** | present | spread to `card` via existing `{...a}` map |

The modal reads `imageUrl`, `name`, `price`, `duration`, `categories` (string array), and
`description`. All are present on `VotePoolActivityDTO` (curate flow). On `VoteActivityResponse`
(participant flow), `categories` is **not** present, so the participant-flow modal renders
without the category-chips line — the modal guards this
(`activity.categories && activity.categories.length > 0`) and degrades gracefully. Adding
`categories` to `VoteActivityResponse` is left as a future enhancement (decided out of scope on
2026-06-02).

---

## Edge Cases

- **No description:** muted placeholder ("No description yet."); modal still shows image + meta.
- **No image:** image element omitted; modal renders name + meta + description.
- **No link** (missing `slug`/`destinationSlug`, `getCardLink` returns `null`): "View full page"
  link is hidden; modal still opens with the info.
- **Swipe vs. open:** name-trigger stops pointer-event propagation so tapping the name never
  starts/commits a swipe.
- **Close paths:** × button, backdrop click, and `Esc` all close; clicks inside the content do
  not close.
- **Modal open across card change:** the modal is bound to the explicitly selected card
  (`infoCard` / `selected`), so it shows a stable snapshot until closed.

---

## Testing

### Backend (`VotePoolServiceTest`)
- `buildPool` maps `description` and `duration` from the activity into `VotePoolActivityDTO`
  (assert both new fields, using `expected`-prefixed values per the test-style rule).

### Frontend
- **`ActivityPreviewModal` (new test):**
  - Renders nothing when `activity` is `null`.
  - Shows name, meta (price/duration/categories), and description when given an activity.
  - Shows muted placeholder when description is empty.
  - Shows "View full page ↗" with `target="_blank"` when `link` is provided; hides it when `link`
    is `null`.
  - Calls `onClose` on × click, backdrop click, and `Esc`.
- **`CuratePage.test.js` (update existing — currently asserts the name link):**
  - Clicking an activity name on the finalize list opens the modal (shows description) instead of
    being a navigating `<a>`; the "View full page" link inside the modal points at
    `/destination/:destSlug/activity/:slug`.
  - Adjust/replace the existing assertion that the finalize name is an external link.
- **`SwipeCard` (test the name-click):**
  - Clicking the card name opens the modal and does not trigger `onSwipe`.

---

## Out of Scope

- No like/dislike or any mutating action inside the modal — display only.
- No change to swipe mechanics, vote submission, pool building logic, or the finalize actions
  (Start over / Build my own trip / Create & get link).
- No new public endpoint; only two fields added to an existing DTO.
