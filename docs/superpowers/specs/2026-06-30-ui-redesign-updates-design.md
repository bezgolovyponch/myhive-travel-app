# UI Redesign Updates — Design

**Date:** 2026-06-30
**Branch:** feat-ui-redesign

## Context

A batch of UI tweaks from a redesign review. The source list mixed code tasks with
design/content tasks assigned to other people (@Stanislav images, @Max content/flows,
@Samir voting-at-checkout). This spec covers **only the front-end code changes**; the
content/strategy items are out of scope.

All changes are in `myhive-react-app/src`. No backend changes.

## Scope (code items)

1. **Transparent header everywhere** — header overlays content on all pages, not just home.
2. **Nav links go cross-domain** — "Destinations" → `https://trivlu.com`,
   "Activities" → `https://<destination>.trivlu.com` (new nav item).
3. **Secondary hero CTA** — add an "Explore activities" secondary button next to the
   existing "Start Group Vote" primary on the homepage hero.
4. **Blog page cleanup** — remove the page-hero sub-header, remove the "Blog" `<h1>`,
   left-align content.
5. **Activity-page breadcrumbs** — left-align and visually emphasize (already in Header.js).
6. **Booking Complete popup** — add a WhatsApp icon/link with heading
   "Contact us to get details about your trip"; uses existing `WHATSAPP_URL` placeholder.
7. **Activity card** — remove inline description; Primary button "Add to trip",
   Secondary button "More info" that opens the existing `ActivityPreviewModal`.

## Decisions (from clarification)

- Header: transparent on **all** pages.
- Nav links: **external absolute URLs** (real cross-domain navigation).
- "More info": opens **ActivityPreviewModal** (no navigation); card no longer shows
  description inline.
- WhatsApp: wire up using the existing `WHATSAPP_URL` placeholder in `config.js`.

## Design

### 1. Transparent header (Header.js / Header.css)

`Header.js` currently sets `header--transparent` only when `isHome`. Change the header
to always be transparent. The simplest path: always apply `header--transparent`, and make
the breadcrumb bar / mobile nav still legible against arbitrary backgrounds.

Risk: non-home pages don't all have a dark hero behind the header, so white nav text +
text-shadow may sit on a light background. To stay safe, the transparent header keeps the
existing dark text-shadow on links (already present) and we leave page top-padding intact.
We will visually confirm on blog/about/contact (light pages) and accept the redesign's
intent that the header floats.

`isHome` is still needed for the nav layout (`.header--transparent .nav-links` right-aligns).
Keep that layout for all pages now that all are transparent.

### 2 + 3. Nav links + secondary CTA

**Nav (Header.js):**
- "Destinations" `<a>` → `https://trivlu.com` (apex). Replace the `scrollToHomeSection`
  handler with a plain external link.
- Add new "Activities" `<a>` → `https://${DEFAULT_DESTINATION_SLUG}.trivlu.com`, built from
  `resolveDestinationSlugFromHost` already exported in `config.js` (via `DEFAULT_DESTINATION_SLUG`).
- These are real navigations (no `react-router` `Link`), so the mobile-close handler still
  fires before navigation.

Helper: add `DESTINATIONS_URL` (= `https://trivlu.com`) and an `activitiesUrl(slug)` to
`config.js` so the host math lives in one place and is testable.

**Secondary CTA (HomePage.js / HomePage.css):**
Add a second button after the primary hero button:
```
<a className="hp-btn-secondary" href={activitiesUrl(DEFAULT_DESTINATION_SLUG)}>
  Explore activities
</a>
```
Fires a `cta_click` analytics event (`cta_label: 'Explore activities', block: 'hero'`) to
match the existing primary. Styled as an outline/ghost button via new `.hp-btn-secondary`.

### 4. Blog page (BlogPage.js / BlogPage.css)

- Delete the `<section className="page-hero">` block (the `<h1>Blog</h1>` + subtitle).
- Keep the `<Helmet>` title for SEO (`Blog — Trivlu`) — that's the document title, not the
  on-page header, so removing the visible `<h1>` is fine.
- Left-align: ensure `.blog-section` / `.blog-grid` align left (remove any centering that the
  hero provided). Adjust `BlogPage.css` so the grid starts at the left page padding.

### 5. Breadcrumbs (Header.css)

Breadcrumbs are already a left-aligned flex row. "Emphasize" = make them stand out:
- Increase font-size slightly (0.75rem → 0.8125rem) and weight of the current item.
- Confirm left alignment (already `display:flex` with no centering). No JS change.

### 6. Booking Complete popup (SuccessModal.js)

Add a WhatsApp call-to-action block inside the modal:
- Heading: "Contact us to get details about your trip".
- A WhatsApp link (`href={WHATSAPP_URL}`, `target=_blank`, `rel=noopener noreferrer`) with a
  WhatsApp icon. The project uses Phosphor icons (`ph ph-*`); use `ph ph-whatsapp-logo`.
- Place it below the existing "next steps", styled minimally (reuse existing classes where
  possible; add a small `.success-whatsapp` style if needed).

### 7. Activity card (ActivityCard.js / ActivityCard.css)

- Remove `<p className="activity-description">` (and its CSS if now unused).
- Footer buttons become two:
  - Primary: "Add to trip" (existing handler, existing `.add-to-trip-btn`, relabeled).
  - Secondary: "More info" → opens `ActivityPreviewModal` for this activity.
- ActivityCard owns local modal state (`const [previewOpen, setPreviewOpen] = useState(false)`),
  builds the activity link the same way `handleCardClick` does, and renders
  `<ActivityPreviewModal activity={activity} link={link} onClose={...} />` when open.
- The "More info" click must `stopPropagation` so it doesn't trigger the card's navigate.
- No changes needed in the three callers (FeaturedActivitiesSection, DestinationPage,
  VoteResultPage) — the card is self-contained.

## Testing

- `config.test.js`: add cases for `DESTINATIONS_URL` and `activitiesUrl(slug)`.
- `ActivityCard`: add/extend a test — description no longer rendered; "Add to trip" and
  "More info" present; clicking "More info" opens the preview modal.
- `SuccessModal`: assert the WhatsApp heading + link (href = WHATSAPP_URL) render.
- `BlogPage`: assert no `<h1>` / page-hero, posts still render.
- Run the existing suite to catch regressions (Header, breadcrumb tests if any).

## Out of scope (assigned to others)

Quality hero image (@Stanislav), About page strategy/content (@Stanislav/@Max), user-flow
write-ups (@Max), voting option at checkout (@Samir), Activity-details template/content
(@Stanislav).
