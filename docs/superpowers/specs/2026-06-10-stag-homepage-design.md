# Stag-Do Homepage Redesign — Design Spec

**Date:** 2026-06-10
**Source:** Google Doc «ТЗ на Homepage» (stag-do landing) + clarifications from the product owner.

## Goal

Replace the current homepage (hero video + destinations grid) with a stag-do-focused
landing of six blocks per the ТЗ. The hero vote-results mock card is **explicitly out of
scope** for now (the product has no per-activity vote-count bars yet).

## Decisions made

| Question | Decision |
|---|---|
| Hero vote-results UI card | Skip for now; add later when vote bars exist in the product |
| Hero background | Keep the existing Cloudinary panorama video; swap to a stag-group photo later |
| Activities grid source | Real activities from the API, admin-configurable via a new `featured` flag (Variant A) |
| Reviews | 4 hardcoded reviews styled after the stagweb.co.uk reference; replace with real ones later |
| WhatsApp / Messenger buttons | Placeholder URLs in `services/config.js`; real links to be provided later |
| «View All Activities» CTA | Links to the main destination page = the **first destination** returned by the API (currently Prague in dev) |
| Trip Builder step visuals | ТЗ wants live product screenshots; no assets exist, so ship with icons now and swap later |

## Backend changes

### `Activity.featured` flag (Variant A)
- New column `featured` (boolean, `NOT NULL DEFAULT false`) on `activities`;
  field on `Activity` entity + `ActivityDTO` (+ admin create/update DTO paths).
- Public `GET /activities` accepts optional `?featured=true` filter, composable with the
  existing `categorySlug` filter.
- Admin activity create/update accepts `featured`; AdminActivities UI gets a
  «Featured on homepage» checkbox in the form and a column indicator in the table.
- **Not** added to CSV import/export mutable fields for now.
- `data.sql`: mark ~12 sample activities as featured for dev.
- Prod schema is `ddl-auto=update`, so the column auto-creates; default false means
  homepage shows an empty grid until admins flag activities — acceptable.

### Tests (required per CLAUDE.md)
- Service test: featured filter returns only featured activities; combined with category filter.
- Controller test: `GET /activities?featured=true` plumbing; admin update toggles the flag.
- DTO mapping test: `featured` survives entity ↔ DTO mapping; defaults to false.

## Frontend changes

New `HomePage.js` with six sections (extracted into components under
`src/components/home/` where non-trivial). Copy (headlines, subcopy, step texts) comes
verbatim from the ТЗ.

1. **Hero** — existing video background; H1 «The Easiest Stag Do Decision. All Sorted
   For You.»; sub «Your mates vote in 10 minutes. We deliver the perfect weekend.»;
   CTA «Start Group Vote» opens `TripSetupModal` in vote mode. The confirm-handler
   logic currently in `TripBuilderDropdown` (navigate to `/vote/new/quiz` with setup
   state) is extracted into a shared `useStartGroupVote` hook used by both call sites (DRY).
2. **Trust bar** — static strip of 4 items (icon + title + one-liner): Stag Do
   Specialists / Group Voted Itinerary / We Handle Everything / Real Human Support.
   Horizontal row on desktop, vertical stack on mobile.
3. **How It Works (Trip Builder)** — heading «The Smartest Way to Plan a Stag Do», 4
   numbered steps with icons and connector arrows (dashed line on desktop), CTA «Start
   Group Vote» (same hook). Reference: thestagcompany.com strip.
4. **Featured Activities** — heading «70+ Activities. Something for Every Group.»;
   fetches `GET /activities?featured=true`, renders up to 12 via the existing
   `ActivityCard` (photo, category, price-from, hover already built in). Grid 4×3
   desktop / 2-col mobile, light background. CTA «View All Activities» → first
   destination's page. Section hidden if the fetch returns zero activities.
5. **How Booking Works** — 3 steps (Vote & Confirm / Tweak the List / Lock It In —
   30% deposit copy per ТЗ), styled after the Airbnb host reference (light background,
   minimal icons). Below: «Got questions? Contact us.» with WhatsApp + Facebook
   Messenger buttons; hrefs from `WHATSAPP_URL` / `MESSENGER_URL` constants in
   `services/config.js` (placeholder values).
6. **Reviews** — dark-green section «What the Lads Say», 4 hardcoded review cards
   (5 stars, quote, name, country, initials avatar), styled after the stagweb
   reference. CTA «Build Your Trip» (same vote hook).

SEO: Helmet title/description updated to stag-do positioning; canonical unchanged.

The old destinations grid is removed from the homepage (destinations remain reachable
via header navigation and destination routes).

### Frontend tests
- HomePage renders all six sections; activities section hidden when no featured
  activities; CTA opens the vote setup modal.
- `useStartGroupVote` hook: confirm navigates to `/vote/new/quiz` with setup state.

## Out of scope
- Hero vote-results card (until vote bars ship).
- Product screenshots in the How It Works steps.
- A global activities catalog page.
- Real review content, real WhatsApp/Messenger links, hero photo asset.
- `homepageOrder` explicit ordering (add later if needed; for now order = API order).
