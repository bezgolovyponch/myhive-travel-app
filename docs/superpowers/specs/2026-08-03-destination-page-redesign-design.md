# Destination page redesign — Split Rail trip view

**Date:** 2026-08-03
**Branch:** ui-fixes
**Status:** Design approved, pending spec review

## Goal

Recraft the destination page (`/destination/:slug`) to the existing Trivlu dark
design system, removing the fixed chrome and the purple hero subheader. The
Activities / Packages / Trip tab strip is **kept** (it's the only switcher
between the three views) but is no longer sticky — it scrolls with the page.
The trip view
adopts a **Split Rail** layout: itinerary + browse in the main column, a
**sticky summary rail** (travelers, dates, total, and the vote/book CTAs) on the
right at desktop widths and pinned to the bottom on mobile.

Chosen from three mockups (variant 2 of the `trip-redesigns.html` artifact).

## Scope

Whole destination page — Activities, Packages, and Trip views all lose the
sticky header, the purple hero subheader, and the tab bar.

Out of scope: homepage, activity/package detail pages, vote flow pages. The
global `Header` component keeps its current fixed/transparent behavior
**everywhere except the destination page** (see "Header" below).

## What changes

### 1. Header (global component, conditional behavior)

The `Header` is rendered once in `Layout.js` for every route, and is
`position: fixed` site-wide. The homepage and detail pages depend on the fixed
transparent header floating over hero photos (a locked design decision) — so we
do **not** delete fixed positioning; we make it conditional.

- On `/destination/:slug` (list/trip view, **not** activity/package detail
  pages), the header renders **non-sticky**: `position: static`, so logo +
  breadcrumbs scroll away with the page.
- On every other route, the header is unchanged (fixed + transparent).
- Detection: `Header` already computes `destinationSlug` and `isDetailPage`.
  Add a `showBreadcrumbs`-adjacent flag `isDestinationListPage =
  Boolean(destinationSlug) && !isDetailPage`, and apply a
  `header--static` modifier class when true. The modifier overrides
  `position`/`background`/`box-shadow`.

### 2. Cart icon replaces "TRIP BUILDER" button

- The orange `.trip-builder-btn` text pill becomes a **cart icon** with the item
  count as a badge (reuse `.trip-builder-count` styling; reposition as an
  overlay badge on the icon).
- Behavior is unchanged: clicking toggles the existing trip-builder dropdown
  (`OPEN_TRIP_BUILDER_MODAL` / `CLOSE_TRIP_BUILDER_MODAL`). No routing change —
  the trip view is still reached via the existing "Continue" flows (calendar/quiz
  handoff and the dropdown's Complete button) and the `?tab=trip` URL.
- Icon: inline SVG cart (stroke, `currentColor`), 24px. Badge only shows when
  `tripItems.length > 0`, matching current logic.
- Accessibility: `aria-label` reflects the count (e.g. "Cart, 4 items").

### 3. Destination page (`DestinationPage.js`)

- **Remove** the `.page-hero.destination-header` block (the purple
  "Prague / City of a Hundred Spires" banner).
- **Keep** the `.tab-nav` block (Activities / Packages / Trip Builder tabs) with
  its current underline-active styling — it is the switcher between the three
  views. It is no longer sticky: with the header now static, the tabs already
  scroll with the page. The only CSS change is dropping the top-padding reserve
  that existed to clear the fixed header/breadcrumbs.
- Keep the three content panels and the `currentTab` URL-driven switching — the
  `?tab=` param selects which panel shows. Default remains `activities`.
- Because the destination name/description no longer appear in the hero, surface
  the destination **name** as a plain page heading above the tab strip so the
  page isn't nameless. Description text is dropped from this page (it survives in
  `<meta>` for SEO — the Helmet block is unchanged).
- Top padding: `.destination-header` currently reserves `8.375rem`
  (`7.625rem` mobile) to clear the fixed header. With a static header this
  reserve is removed; content flows normally under the now-in-flow header.

### 4. Trip view — Split Rail layout (`TripBuilder.js` + `TripBuilder.css`)

Restructure `.trip-builder-layout` from the current 1fr/1fr (itinerary | browse)
into:

- **Desktop (≥769px):** two columns — `main` (itinerary, then browse/suggestions
  stacked below) and a **sticky rail** (`position: sticky; top: <gap>`) holding
  the trip summary card (destination, travelers input, dates), the running total,
  and the two CTAs — matching mockup variant 2. Grid:
  `grid-template-columns: 1fr 20rem` (rail fixed width).
- **Mobile (≤768px):** single column — itinerary, then browse/suggestions. The
  summary + CTAs become a **sticky bottom bar** (`position: sticky; bottom: 0`)
  with a gradient fade, containing the total and the two CTAs. Travelers/dates
  move into a compact summary card above the bottom bar (or into the bottom bar
  header) rather than the sticky bar itself, to keep the bar thumb-sized.
- The **"Let your mates vote"** and **"Complete Booking"** buttons live in this
  sticky zone (rail on desktop, bottom bar on mobile) — this is the "make sticky
  also let your mates vote" requirement.
- Browse-More-Activities and (quiz/vote-mode) Recommended/Group-suggestions move
  **below the itinerary in the main column**, no longer a separate right column.
- The booking `ContactForm` (which currently replaces the right column inline)
  now replaces the **main column** content when `showContactForm` is true; the
  sticky rail/bar can hide or show a "back to trip" affordance while the form is
  open. `scrollBookingFormIntoView` retargets to the main column.
- All existing state, effects, analytics events, vote annotation, budget panel,
  and package grouping are preserved — this is a layout re-flow, not a logic
  change. The `ResizeObserver` height-sync between the two columns
  (`leftRef`/`rightRef`) is removed (no longer two equal-height columns);
  the rail sizes to its own content and sticks.

### 5. Styling

Everything uses existing tokens. Cart icon and vote CTA use `--purple` /
`--purple-l` per the locked color direction (no white/orange CTAs). Sticky bottom
bar respects `env(safe-area-inset-bottom)`. Sticky rail offset accounts for the
now-static header (top gutter = `--gap-lg`, since nothing is fixed above it).

## Components / files touched

| File | Change |
|---|---|
| `components/Header.js` | Add `isDestinationListPage` flag → `header--static` class; cart icon SVG + badge replacing text button |
| `components/Header.css` | `.header--static` modifier; `.cart-btn` / badge-as-overlay styles |
| `pages/DestinationPage.js` | Remove hero; keep tab-nav; add destination heading above tabs |
| `pages/DestinationPage.css` | Keep `.tab-nav` styling; drop `.destination-header` top-padding reserve; heading styles |
| `components/TripBuilder.js` | Re-flow to main column + sticky rail; move browse below itinerary; retarget booking form + scroll |
| `components/TripBuilder.css` | Split Rail grid; sticky rail (desktop) + sticky bottom bar (mobile); drop height-sync CSS |

## Testing

- Existing tests: `TripBuilder.test.js`, `TripBuilderDropdown.test.js`,
  `DestinationModalContext.test.js` must still pass. Update any that assert on
  the "TRIP BUILDER" button text. Tab buttons remain, so tab assertions hold.
- Manual (per project rule, **on production/WebKit for mobile** — local desktop
  checks don't reproduce the CookieYes/GTM overlays):
  - Header scrolls away on `/destination/prague`; still fixed on homepage.
  - Cart icon shows badge count, opens dropdown.
  - Trip view: rail sticks on desktop; bottom bar sticks on mobile; vote + book
    both reachable; booking form takes over main column and scrolls into view.
  - Tabs still switch Activities / Packages / Trip and scroll with the page.
  - iPhone: bottom bar clears the home indicator and sits above the WhatsApp FAB.

## Risks / notes

- Header is global — the conditional must be verified on homepage, activity
  detail, and vote pages to confirm nothing regressed to non-sticky.
- Tabs are retained, so the switcher between the three views is unchanged; only
  their sticky positioning goes away.
- The purple hero also carried the destination **name**; it now renders as a
  heading above the tabs so the page isn't nameless.
