# SSR ↔ CRA parity: one canonical UI for the Next.js migration

**Date:** 2026-08-06
**Branch:** `feat/nextjs-foundation`
**Status:** implemented 2026-08-07 (`2760c45`, `b75897b`, `984919b`)

## Outcome

All twelve SSR public routes now render the canonical CRA components. The
duplicate layer is gone: `Header.tsx`, `Footer.tsx`, `ActivityCardStatic.tsx` and
`ContactFormIsland.tsx` are deleted, and no page under `app/` hand-copies CRA
markup any more.

Verified on the production build in WebKit at iPhone 13: `smoke.mjs` PASS with
zero server errors, 547 CRA tests passing, every page one `<h1>` and one
`<title>` with no console or hydration errors, cart present and incrementing,
`.vc-fill` back to 7px, 12 crawlable activity links, and all five homepage
`cta_click` events firing with their `cta_label`/`block` params where previously
none fired at all.

Three SEO improvements the hand-written duplicates carried and CRA lacked had to
be ported *into* CRA so switching to it was not a regression — a crawlable anchor
on `ActivityCard`'s title (CRA rendered `role="button"` with no `href`), an `h1`
on the blog listing, and an `h1` on the destination catalog. This was the main
surprise of the work: the duplicates were not uniformly worse.

Also fixed, found only by running it: `react-helmet-async@3`'s
`React19Dispatcher` renders real `title`/`meta`/`link` elements for React 19 to
hoist, so every page emitted two titles, two descriptions and two canonicals —
and on streamed dynamic routes the `<head>` title came out **empty** while the
real one landed in the body. CRA's `Helmet` is now wrapped in `PageHead`, which
the SSR chrome switches off by context; the SPA keeps the default.

**Still open:** the `page_view` question below. It needs the GTM console, not the
repo.

## Problem

UI fixes landed in the legacy CRA app after the Next.js migration PR was opened do not
appear in the Next.js app. Reported symptoms: no cart in the top-right, a different
landing page, unverified analytics parity, and unverified mobile + itinerary behaviour.

The cause is not git divergence. `main` is already merged into this branch
(`8a2d433`), and `scripts/sync-legacy.mjs` re-copies `myhive-react-app/src` into
`myhive-next/legacy-src` on every `predev`/`prebuild`. The SPA therefore has every fix.

The cause is **duplication**. `myhive-next/app/(public)/` and
`myhive-next/components/site/` are hand-written re-implementations of CRA UI, with
content hardcoded in TSX (`STEPS`, `REVIEWS`, `TRUST_ITEMS`, an inline vote-card).
They do not track `legacy-src`, so they froze at the moment they were written and have
drifted ever since. Patching them would restore parity for a week and then drift again.

A second-order consequence: because SSR pages win route resolution on fresh loads but
react-router owns history once the SPA mounts, **the app currently serves two different
homepages** depending on how the user arrived.

## Evidence gathered

Verified by execution against `localhost:3001` (Next dev) and `https://www.trivlu.com`
(production, which runs CRA):

| Claim | Status |
|---|---|
| Cart absent from SSR HTML | Confirmed — `cart-btn`: 0, `header-actions`: 0 occurrences |
| SSR still renders old text `TRIP BUILDER` button | Confirmed (3 matches) |
| 12 activity cards server-rendered on `/` | Confirmed — this is the SEO floor to preserve |
| Landing section order inverted | Confirmed via `<h2>` order: Trust → HowItWorks → Activities → Reviews vs CRA Hero → Activities → HowItWorks → Trust → Reviews |
| H1 drift | SSR "The Easiest Stag Do Decision. All Sorted For You." vs production "Prague Stag Do. Planned in 10 minutes." |
| Hero vote card renders unstyled | **Live bug.** `page.tsx` copies `.vote-card`/`.vc-*` markup but never imports `VoteDemoCard.css`, where those styles exclusively live. Production renders the card correctly. |
| `.hero-trust-line` unstyled | **Live bug.** Class exists only in `page.tsx`; no CSS defined anywhere in the repo. |
| 8 hotlinks to `cdn.jsdelivr.net/gh/cyrudi/sandbox` | Confirmed — production homepage imagery depends on a personal GitHub sandbox repo |
| Analytics: SSR fires 0 of 27 `pushEvent` events | Confirmed by grep; SSR landing is plain `<a href>` markup |
| GTM container matches | Confirmed — `GTM-KB7BJLDS` on both; `sync-legacy` excludes `index.html`, so no double-load |
| Trip-builder/mobile commits covered by sync | Code-read only, **not executed** |

Verified in **Playwright WebKit at iPhone 13**, production vs local Next SSR:

| Probe | production (CRA) | local Next SSR |
|---|---|---|
| `documentElement.scrollWidth` vs viewport | 390 = 390, no overflow | 390 = 390, no overflow |
| `.cart-btn` present | yes | **no** |
| `.header-actions` present | yes | **no** |
| `.vc-fill` computed height | 7px | **0px** |
| `document.title` | Trivlu — Prague Stag Do. Planned in 10 Minutes. | Trivlu — Stag Do Trips, Sorted in Minutes |

`.vc-fill` at 0px height is the missing-`VoteDemoCard.css` bug measured directly: the vote
bars are in the DOM with no styles, so the hero card renders as unstyled text over the
hero photo.

**No mobile layout overflow exists on either side.** An earlier local headless-Chrome
screenshot appeared to show horizontal clipping, but production clipped identically, and
WebKit reports `scrollWidth == clientWidth` for both — it was a headless artifact. The
only over-wide elements on production are CookieYes' hidden preference panel (845px),
which is expected. Retracted as a finding.

Three different titles exist today: production pre-JS (`Trivlu — Group Travel Made
Easy`, from CRA `index.html`), CRA post-JS Helmet (`Trivlu — Prague Stag Do. Planned in
10 Minutes.`), and Next SSR (`Trivlu — Stag Do Trips, Sorted in Minutes`).

## Decisions taken

1. **CRA is canonical.** It is what production serves. SSR is made to match it, not the
   reverse.
2. **Eliminate the duplicate layer** rather than patch it. SSR pages render real CRA
   components.
3. **Scope: all SSR pages.** Every page under `app/(public)/` plus
   `app/destination/[slug]/`. A 1:1 CRA counterpart exists for each.
4. **Legacy must keep working standalone.** `myhive-react-app` is production. Every
   change to CRA source must be backwards-compatible with zero behaviour change when
   rendered inside the SPA.

## Architecture

The enabling fact: in Next, `'use client'` means "ships JS and hydrates", **not**
"skipped on the server". CRA components still server-render into the HTML crawlers see.
The only thing that does *not* reach that HTML is content gated behind `useEffect` —
which is exactly how `CatalogContext` loads data.

So each SSR page becomes a thin **server shell** that keeps what servers are good at and
delegates markup to the canonical component:

```
app/(public)/page.tsx            server: metadata, JSON-LD, data fetch
  └── <LegacyIsland>             'use client': SSR-safe provider stack
        └── HomePage.js          canonical CRA — activities injected as props
```

### 1. `components/site/LegacyIsland.tsx`

Provides the context stack CRA components require: `CatalogProvider`, `TripProvider`,
`DestinationModalProvider`, `HelmetProvider`.

Deliberately **excludes `AuthContext`** — it builds its OIDC config from `window` at
module scope, which is the sole reason `LegacyAppShim` needs `ssr: false`. Keeping it out
of the import graph is what makes server rendering possible at all.

`TripProvider` reads `localStorage` in a `useReducer` lazy initializer, which runs during
render including on the server. Every access is `try/catch`-wrapped, so the server-side
`ReferenceError` is swallowed and it degrades to defaults. Safe, but it means the cart
badge server-renders as absent and appears on hydration — see risks.

### 2. Navigation: a history bridge

CRA components use react-router `<Link to="…">` and `useNavigate()`. Inside a real
`BrowserRouter` on an SSR page, clicking "About" would perform *client-side* routing —
but an SSR page has no `<Routes>` tree, so it would navigate to a blank page.

Fix: mount a `Router` with a **custom history** whose `push`/`replace` call
`window.location.assign`. CRA `<Link>`s and `navigate()` then resolve to real full-page
loads, which is correct on SSR pages. The identical component still does client-side
routing inside the SPA, where a genuine `BrowserRouter` is present. One small file, and
it is what lets a single component serve both worlds.

### 3. Data injection seam

SEO-critical sections must not depend on a client-side fetch. `FeaturedActivitiesSection`
today holds its activities in local `useState`, populated by a `useEffect` that first
waits for `catalog.loading` to clear — so on the server it renders an empty grid twice
over. The seam must therefore seed the state *and* suppress the fetch:

```js
function FeaturedActivitiesSection({activities: injected}) {
    const {state: catalog} = useCatalog();
    const [activities, setActivities] = useState(injected ?? []);

    useEffect(() => {
        if (injected) return undefined;   // server already supplied them
        if (catalog.loading) return undefined;
        // …existing fetch + default-destination fallback, unchanged
    }, [injected, catalog.loading, catalog.destinations]);
```

Zero behaviour change in the SPA: prop omitted → identical fetch path as today. The
server shell passes the activities it already loads in `loadFeatured()`, which duplicates
this same featured-then-default-destination fallback and can be reused as-is.

`useTrip()` stays untouched — the "Already added" badge is genuinely client-only state and
correctly appears on hydration.

The same seam applies to any other section whose content must reach the initial HTML;
Phase 2 will identify them per page rather than guessing now.

### 4. Metadata reconciliation

CRA `<Helmet>` and Next `metadata` would otherwise compete for `<title>`. Since CRA is
canonical, Next's `metadata` is derived from the same copy so both agree and there is
nothing to diverge. Crawlers read the server HTML (Next `metadata`); Helmet's
client-side write matches it.

## Per-page plan

Phased so the pattern is proven on one page before the SEO-critical pages depend on it.

**Phase 1 — infrastructure + the reported symptoms**
- `LegacyIsland.tsx`, history bridge
- `Header.tsx` delegates to CRA `Header.js` → recovers cart, count badge,
  `TripBuilderDropdown`, `TripSetupModal`, breadcrumbs, pinned `.header-actions`,
  `checkoutOpen` collapse, smooth-scroll Activities link
- `app/(public)/page.tsx` → server shell + CRA `HomePage`. Resolves section order, all
  copy drift, `VoteDemoCard` (and its missing CSS), `StickyVoteCta`, the moment cards,
  the jsdelivr hotlinks, and all five hero/section `cta_click` events at once
- `FeaturedActivitiesSection` prop seam
- `Footer.tsx` and `PublicChrome.tsx`: diff each against its CRA counterpart and either
  delegate or record why a hand-written version must stay. `PublicChrome` also owns the
  global CSS imports, so it is where any other missing-stylesheet bug of the
  `VoteDemoCard.css` kind would live — audit its import list against what the CRA
  component tree actually imports.

**Phase 2 — SEO-critical detail pages**
- `destination/[slug]/activity/[aslug]`, `destination/[slug]/package/[pslug]`,
  `app/destination/[slug]`
- Highest risk: these carry the organic traffic the migration exists to serve. Each
  requires a before/after check that server-rendered body content did not shrink.

**Phase 3 — remaining pages**
- `about`, `contact`, `blog`, `blog/[slug]`, `terms`, `privacy-policy`,
  `cookie-policy`, `refund-policy`
- `contact` needs care: `ContactPage.js` calls `turnstile.render()` and there is an
  existing `ContactFormIsland.tsx` to reconcile.

## Analytics

Goal stated by the user: analytics must match production.

- Rendering CRA components restores all `pushEvent` call sites automatically — this is a
  consequence of the refactor, not separate work.
- **Open question requiring the GTM console, not the repo:** whether production fires
  `page_view` via a History Change trigger. If it does not, Next SSR pages *add* a
  pageview per navigation that production never fired, inflating counts. This is a
  divergence in the opposite direction from the missing events and must be settled before
  claiming parity.
- Verify no double-fire once CRA components render on SSR pages *and* in the SPA (same
  `window.dataLayer`, same container).
- Verification method: load each page with a `dataLayer` recorder and diff the event
  sequence against production for the same interaction.

## SEO guardrails

Non-negotiable: the refactor must not shrink server-rendered content.

- `scripts/smoke.mjs` run before and after.
- Captured baseline (`scratchpad/baseline-home.html`): 12 activity-card links, 1 `<h1>`,
  5 `<h2>`. Re-assert after conversion, allowing for the intended copy/order changes.
- For each converted page, diff `curl` output before/after and confirm body text and
  internal links did not disappear into a client-only render.

## Verification

- **Mobile: Playwright WebKit at an iPhone viewport**, not local desktop Chrome. Local
  headless Chrome already produced one false positive during this investigation, and the
  CookieYes overlay that several mobile fixes interact with does not exist locally.
  Playwright lives at `/Users/olga/.npm/_npx/e41f203b7505f1fb/node_modules`; it is
  CommonJS, so ESM scripts must `import pw from '…/playwright/index.js'` and destructure
  (`NODE_PATH` does not work for ESM). The reusable probe is
  `scratchpad/mobile-check.mjs` — it asserts overflow, cart presence and `.vc-fill`
  height, and should be re-run after each phase.
- **Itinerary/trip-builder:** the "no gaps" conclusion is code-reading only. Exercise the
  real flow — add an activity from an SSR activity page via `?add=`, confirm
  `useTripDeepLink` dispatches it, the cart count increments, and the itinerary opens.
- Cart: confirm the badge appears after hydration and the dropdown opens on SSR pages.
- Confirm CRA standalone (`myhive-react-app`) is unchanged — its own test suite passes.

## Risks

| Risk | Mitigation |
|---|---|
| SEO regression on detail pages | Phase 2 gated on before/after HTML diffs; `smoke.mjs` |
| Cart badge hydration mismatch (server has no `localStorage`) | Expected and self-correcting; add `suppressHydrationWarning` on that node rather than leaving warnings |
| `AuthContext` pulled into the server graph → render crash | Keep it out of `LegacyIsland`; assert by building |
| Duplicate GTM events | Container is loaded once in `app/layout.tsx`; `sync-legacy` excludes `index.html`. Verify with a `dataLayer` diff |
| Breaking production CRA | All CRA edits additive with context fallback; CRA tests must pass |
| Helmet/metadata title conflict | Derive Next `metadata` from the same copy |

## Out of scope

- Backend changes.
- Redesigning any UI. This is a parity exercise; CRA's current design is the target.
- The `cdn.jsdelivr.net/gh/cyrudi/sandbox` hotlinks disappear as a side effect of using
  CRA components, but migrating those assets to owned hosting is separate work.
- Deleting `myhive-react-app`. Legacy stays.
