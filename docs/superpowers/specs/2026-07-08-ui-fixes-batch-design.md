# UI Fixes Batch — Design Spec (2026-07-08)

Branch: `layout-fixes`. App: `myhive-react-app`. Approved by user 2026-07-08.

## 1. Hero background image

- Source: `myhive-react-app/hero_stag_do_prague.png` (4.1MB, 1934×1348).
- Compress to web-sized JPEG (quality ~85, target ≤300KB), save as `public/hero-stag-do-prague.jpg`.
- Replace the CDN hero URL in `src/pages/HomePage.css` (desktop, ~line 44) with the local asset.
- Mobile (max-width 768px): use the same image, replace `hero_mobile2.png`; tune `background-position` so the crowd-surfer stays centered.

## 2. Purple → neutral white (hero + activity section)

Replace purple accents with neutral white. Homepage `--purple*` tokens remain defined; only these usages change:

- Hero primary button "Start Group Vote" (`.hp-btn-primary`, HomePage.css): white fill, dark text (`#1f1d27`-ish), hover slightly dimmed white (`rgba(255,255,255,0.85)`); drop the purple shadow.
- Hero purple accents: vote card badge, progress bars/fills, icon tints → white / neutral grays.
- `ActivityCard.css`: `.add-to-trip-btn` → white fill, dark text (hover dimmed white); `.more-info-btn` border+text → white.
- `ActivityDetailPage.css`: chip icons, section-title icons, included-list checkmarks, `.activity-detail-add-btn` (+hover), panel meta icons → white/neutral.
- `ActivityGallery.css`: lightbox active thumbnail outline → white.

## 3. Trip builder vote button → orange outline

- "Let your mates vote" (`TripBuilder.js:595` / TripBuilder.css): transparent background, 1.5px solid `#E24A33` border, `#E24A33` text; hover: `rgba(226,74,51,0.12)` background.

## 4. Remove budget from setup modal

- Delete the "Group budget (€, optional)" field, its state and validation from `src/components/TripSetupModal.js` (vote mode section, ~lines 220–246).
- Remove now-dead budget seeding from the modal's confirm payload. TripBuilder's budget summary block only renders when a budget exists; it stays but any paths made unreachable by this removal are cleaned up.

## 5. Traveler count → mobile-friendly slider

- In `TripSetupModal.js`, replace the native `<input type="number">` (1–20) with a range slider:
  - Gray track, large white circular thumb (~28px) — comfortable for touch.
  - Editable numeric value bubble next to the slider (typing syncs the slider; slider syncs the number).
  - Same min/max/default semantics as today (1–20, seeded from trip state).

## 6. Global scrollbar: white thumb, gray track

- In `src/styles/global.css`: `::-webkit-scrollbar` (width ~10px), track gray (`#3a3a3a`-ish to fit dark theme), thumb white with rounded corners; Firefox `scrollbar-color: #fff <gray>`.
- Applies site-wide including mobile where the platform allows. Known limitation: iOS Safari uses overlay scrollbars and ignores custom styling.

## 7. Clickable hero headline

- New headline copy: **"The smartest way to plan a stag do"** (`HomePage.js:33`).
- The phrase (or its key part) is a link-styled inline element (underline/hover affordance) that opens the Start Group Vote modal — same handler as the "Start Group Vote" button.
- Subtitle "Your mates vote in 10 minutes. We deliver the perfect weekend." stays.
- Must remain an accessible interactive element (button semantics, keyboard focusable).

## 8. Swipe undo (vote deck)

- `src/pages/vote/ActivityVotePage.js` swipe deck: add a ↩ Undo control (Tinder-style).
- Single-step history: restores the last swiped card to the top of the deck and reverts that vote on the server (delete/overwrite the recorded vote for that activity+voter).
- Undo disabled when there is nothing to undo. If the backend has no revert endpoint, add one (or overwrite semantics) as part of this work.

## 9. Activity detail page → final spec `Activity_Trivlu_0photo (2).html`

Deltas to implement:

- Zero-photo case: `ActivityGallery` currently returns `null`; instead render a full-width single image area with the default/fallback image (mockup: `data-count="0"` → single column, thumbnail grid hidden).
- "Added to trip" button state: orange (`#E8852B`, hover `#cf7320`) instead of gray disabled.
- Description sub-headings: "What to expect on the day" and "Good to know" semantic sub-headers within the About section.
- Explicitly out of scope (user decision): the mockup's "Create your trip first" / "Added to trip" overlay popups — current inline flow stays.

## 10. Logo → tri▼lu SVG

- Recreate the logo as an SVG: bold lowercase "tri" + orange (`#E24A33`) downward-pointing triangle in place of the "v" + "lu". Text white (dark-header variant).
- Place in `public/` (e.g. `logo-trivlu.svg`) and use it in `src/components/Header.js` instead of `logo-white.png`.
- Favicon/app icons unchanged (out of scope).

## Colors

- Logo/button orange: `#E24A33` (from logo mockup).
- "Added to trip" state orange: `#E8852B` / hover `#cf7320` (from activity mockup).
- Neutral white replacements: `#fff` fills with dark text `#1f1d27`, dimmed-white hovers.

## Testing

- Follow existing test patterns (React Testing Library; see TripBuilder.test.js, ReviewsSection.test.js).
- Key behaviors to cover: budget field absent from vote-mode modal; slider/number sync; hero headline click opens vote modal; undo restores card + reverts vote; zero-photo gallery renders fallback (not null); "Added to trip" orange state.
- Visual/CSS changes verified manually (desktop + mobile widths).
