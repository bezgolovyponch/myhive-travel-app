# 2026-10-15 — SEO URL restructure, phase 1: Prague hub URLs, 308 redirects, /ideas

Branch `feat/seo-prague-urls` (base `main`). Spec:
`docs/superpowers/specs/2026-10-15-seo-prague-urls-phase1-design.md`; plan:
`docs/superpowers/plans/2026-10-15-seo-prague-urls-phase1.md`. Source: the SEO manager's ТЗ
"новые URL и 308-редиректы trivlu.com" (Google Doc `1ZU_jG_AgJqENmaudAygCcq3aAqhhkT6X6HZDz0tU3T4`),
scoped down to what needs no backend schema change.

**This is phase 1 only.** Phase 2 (9 single categories + category pages) and phase 3 (German
slugs, `slug_de`) are NOT done and are separate specs.

## What moved

The Prague catalog left `/destination/prague/...` for keyword URLs, with German path
*segments* (not yet German slugs). Every old address is a single 308 to its new one. Internal
links, canonical, hreflang, sitemap and email deep links all emit the new addresses.

| Page | EN | DE |
| --- | --- | --- |
| Prague hub (catalog + `?tab=` Trip Builder + `?voteSession=`) | `/prague-stag-do` | `/de/jga-prag` |
| Activity | `/prague-stag-do/activities/{slug}` | `/de/jga-prag/aktivitaeten/{slug}` |
| Package detail | `/prague-stag-do/packages/{slug}` | `/de/jga-prag/pakete/{slug}` |
| Article index | `/ideas` | `/de/ratgeber` |
| Article | `/ideas/{slug}` | `/de/ratgeber/{slug}` |
| Other cities (unchanged) | `/destination/{city}`, `/destination/{city}/activity/{slug}`, `/destination/{city}/package/{slug}` | same with `/de` prefix |
| Landings (unchanged) | `/landing/prague`, `/landing/vote` | same with `/de` prefix |

Slugs are the existing EN slugs in both languages. There is no package overview page yet.

## Design: one table, three consumers

`myhive-react-app/src/i18n/routes.js` (already shared with Next through `legacy-src`) is the
single owner of the URL scheme:

- `DESTINATION_HUBS` — destination slug to EN-internal hub path (`{ prague: '/prague-stag-do' }`);
  cities absent here stay on `/destination/{slug}`.
- `LOCALIZED_PATHNAMES` — next-intl `pathnames` shape. EN is the internal form (= the Next
  route folder), DE is the public form; unlisted paths are identical in every locale.

Consumers:

1. **Next middleware** — `myhive-next/i18n/routing.ts` passes `pathnames: LOCALIZED_PATHNAMES`
   to `defineRouting`, so `/de/jga-prag/aktivitaeten/x` is rewritten to the file route under
   `app/[locale]/prague-stag-do/activities/...`. Only EN-named route folders exist.
2. **Link, canonical and sitemap helpers** (both apps) — pure builders `destinationPath`,
   `activityPath`, `packagePath`, `blogIndexPath`, `blogPostPath` return the EN-internal path;
   `localizeSegments`/`delocalizeSegments` translate segments; `routePath` (CRA `<Link>`,
   `navigate()`, `<Route path>`), `localizePath` (prefix + segments, for Next `canonical()`,
   sitemap, `useLocalePath`) and `localizeHref` (unchanged contract plus segments) sit on top;
   `matchCatalogPath` replaces the `/^\/destination\//` regexes in `Header.js`. All tolerate a
   `?query#hash` suffix and are idempotent on an already-German path.
3. **Backend** — `util/PublicPaths.java` mirrors `DESTINATION_HUBS` and the hub rows for email
   deep links (`EmailService`: vote dashboard, vote result, lead restore). Two copies by design
   (Java cannot read the JS); each carries a comment naming the other. Vote links `/vote/...`
   are unchanged.

**Why CRA links carry locale segments themselves.** The SPA mounts a real `BrowserRouter` whose
basename only adds the `/de` prefix. Links inside it must already be `/jga-prag/...`, or the URL
bar shows a non-canonical address. `currentLocale()` is correct in both worlds (URL in the
browser, `LocaleProvider`-bound during SSR). Next server code never uses the implicit-locale
helpers: it builds the EN-internal path and passes it through `canonical(path, locale)`.

Other pieces: `Layout.js` registers the localized variant of every hub pattern per locale;
`DestinationPage`/`ActivityDetailPage`/`PackageDetailPage` take the destination slug from a
`destinationSlug` prop when present, else `useParams()`; the existing Next page bodies moved
into shared server modules under `components/site/catalog/` that both the hub folders and the
old `destination/[slug]` routes call; `(public)/blog` became `(public)/ideas`; the legacy
wrappers compute their `RouteMatch` pattern with `routePath(pattern, useLocale())`; both
language switchers `delocalizeSegments` the current path, then `localizeHref` for the target.

## Redirects (`next.config.ts` `redirects()`, `permanent: true` = 308)

```
/destination/prague                     -> /prague-stag-do
/de/destination/prague                  -> /de/jga-prag
/destination/prague/activity/:slug      -> /prague-stag-do/activities/:slug
/de/destination/prague/activity/:slug   -> /de/jga-prag/aktivitaeten/:slug
/destination/prague/package/:slug       -> /prague-stag-do/packages/:slug
/de/destination/prague/package/:slug    -> /de/jga-prag/pakete/:slug
/blog                                   -> /ideas
/de/blog                                -> /de/ratgeber
/blog/how-to-plan-a-stress-free-group-trip     -> /ideas/how-to-plan-a-stag-do
/de/blog/how-to-plan-a-stress-free-group-trip  -> /de/ratgeber/how-to-plan-a-stag-do
```

Query strings pass through (Next default).

**410 Gone** for the five seed posts, EN and DE (10 URLs), from `myhive-next/middleware.ts`
(slug list in `lib/gone.ts`):
`top-5-group-travel-destinations-for-2026`, `why-ai-is-changing-the-way-we-travel-together`,
`bali-on-a-budget-a-group-travel-guide`, `the-rise-of-multi-traveler-experiences`,
`weekend-getaway-ideas-for-large-groups`.

Why middleware: `redirects()` cannot emit a 410. Why there is **no generic `/blog/:slug` rule**:
Next runs redirects before middleware, so such a rule would turn the 410s into 308s. Any other
`/blog/...` URL is a real 404 (the route folder is gone).

## Deviations from the ТЗ

- **Prague-scoped `:slug` masks instead of 72 literal rows.** The slug set lives in the database;
  a literal list would rot. The ТЗ's objection to masks was about catching other cities, and a
  mask scoped to `prague` cannot.
- **Packages moved now**, ahead of the ТЗ's 13.11 date: prod has zero public packages, so the
  move costs nothing and avoids a second redirect later.
- **German slugs deferred (phase 3).** The moved post keeps its EN slug on `/de/ratgeber`.
- **`/jga-prag` without the `/de` prefix answers 307 to `/prague-stag-do`, not 404.** With
  `localeDetection: false` an unprefixed German path would be an unknown EN route, but next-intl
  matched it against the DE pattern. Accepted: it is a temporary redirect to the right place and
  no 500.
- **`LegacyBlog` is not wrapped in `RouteMatch`** (the plan said to). `BlogPage` reads no params
  and the original wrapper had none; wrapping broke the `/en/ideas` prerender (see gotchas).

## Gotchas discovered

- **Bash heredocs on this machine collapse backslashes.** The middleware matcher
  `'.*\..*'` became `'.*..*'`, which would have matched only `/`. Caught in review and fixed
  (`50d55707`); now pinned by `myhive-next/lib/__tests__/middleware-matcher.test.ts`. Write
  regex or path content with Edit/Write, never heredocs.
- **Local E2E needs `INTERNAL_API_TOKEN` set on both servers** (backend and Next). Without it
  the backend's 100 req/min/IP limiter returns 429 to SSR fetches and Next surfaces it as 500
  (first `check-redirects` run: 69 FAIL, all of this kind). The script sends `X-Internal-Token`
  from its own env var.
- **Steam can hold port 8080** (steamwebhelper): the run used the backend on 8081 and
  `BACKEND_URL=http://localhost:8081`.
- **Smoke description-length check counts HTML-escaped characters.** One dev-seed activity
  (`ak-47-glock-17-shooting`) reports 160 > 155; the code is identical to `main`, so this is
  not a regression.
- **The dev seed has `seoIndexable=false`**, so `app/sitemap.ts` skips every destination and the
  sitemap's positive check (`prague-stag-do` present) only works on prod data. The sitemap does
  contain `/ideas` and `/de/ratgeber` with correct alternates.
- **`/en/...` prerendered pages see the `/en` prefix in `LegacyRouter`**, which is why wrapping
  `LegacyBlog` in `RouteMatch` broke `/en/ideas`.
- `scripts/check-redirects.mjs` duplicates the five gone slugs (Node cannot import `lib/gone.ts`);
  a comment names `lib/gone.ts` as the owner. `routes.js` prints a harmless
  `MODULE_TYPELESS_PACKAGE_JSON` warning under plain Node.

## Commits

```
7f97099e docs(seo): design + plan for the Prague URL move, phase 1 (hub URLs, 308s, /ideas)
daf59b88 feat(i18n): URL scheme table + localized path helpers for the Prague hub and /ideas
d5ab4660 feat(email): Trip Builder deep links use the Prague hub URLs (PublicPaths mirror of routes.js)
fec0573b feat(cra): Prague hub + /ideas routes, links, breadcrumbs and language switcher on the new URL scheme
e9a07edb feat(next): next-intl pathnames for the German segments, 308 redirects from the old Prague/blog URLs, 410 for retired posts
50d55707 fix(next): restore the escaped dot in the middleware matcher and pin it with a test
f30150ed feat(next): Prague hub, activity and package routes on /prague-stag-do, blog on /ideas; sitemap, landings and legacy wrappers follow the table
90522a30 test(next): check-redirects.mjs - single-hop 308 / 410 / untouched-city acceptance for the URL move
```

## Verification

Test counts: CRA Jest 769, backend 1435, Next vitest 37 (including the middleware-matcher test),
`tsc` clean, `next build` green.

Local end-to-end run (backend on :8081, Next on :3000, `INTERNAL_API_TOKEN` set on both):

| Item | Result |
| --- | --- |
| `check-redirects.mjs` | PASS, final run `errors: 0` |
| `smoke.mjs` | PASS except one dev-data failure (description length, see gotchas) |
| `/de/prague-stag-do` | 307, `location: /de/jga-prag` |
| `/jga-prag` | 307, `location: /prague-stag-do` |
| `/de/ideas` | 307, `location: /de/ratgeber` |
| `/prague-stag-do?tab=trip-builder` | 200, `<meta name="robots" content="noindex, follow"/>` |
| canonical on `/de/jga-prag` | `http://localhost:3000/de/jga-prag` |
| alternates | en `/prague-stag-do`, de `/de/jga-prag`, x-default `/prague-stag-do` |
| sitemap has `/destination/prague` | 0 (PASS) |
| sitemap has `prague-stag-do` | not verifiable locally (0, dev seed not indexable) |
| Browser: language switcher | `/de/jga-prag` + English -> `/prague-stag-do` |
| Browser: activity card | "Bootsfahrt auf der Moldau" -> `/de/jga-prag/aktivitaeten/river-boat-cruise` |
| Browser: Trip Builder deep link | `/prague-stag-do?tab=trip-builder` renders the itinerary via the SPA shim |

Verbatim output lines:

```
errors: 0

## /de/prague-stag-do
HTTP/1.1 307 Temporary Redirect
location: /de/jga-prag
## /jga-prag
HTTP/1.1 307 Temporary Redirect
location: /prague-stag-do
## /de/ideas
HTTP/1.1 307 Temporary Redirect
location: /de/ratgeber
## tab
HTTP/1.1 200 OK
<meta name="robots" content="noindex, follow"/>
## canonical
<link rel="canonical" href="http://localhost:3000/de/jga-prag"/>
## alternate
<link rel="alternate" hrefLang="en" href="http://localhost:3000/prague-stag-do"/>
<link rel="alternate" hrefLang="de" href="http://localhost:3000/de/jga-prag"/>
<link rel="alternate" hrefLang="x-default" href="http://localhost:3000/prague-stag-do"/>

== /prague-stag-do [200] h1x1 | Prague Stag Do — Activities & Packages | Trivlu
== /ideas [200] h1x1 | Stag Do Planning Guides & Ideas | Trivlu Blog
== /prague-stag-do/activities/ak-47-glock-17-shooting [200] h1x1 | AK-47 and Glock 17 Shooting in Prague | Trivlu
== /de/jga-prag [200]
== /de/jga-prag/aktivitaeten/ak-47-glock-17-shooting [200]
== /destination/atlantis -> 404
== /ideas/not-a-real-post -> 404
== /prague-stag-do/activities/not-a-real-activity -> 404
== /sitemap.xml [200]: 12 urls
== /admin [200] legacy-shim=yes
== /vote/new [200] legacy-shim=yes

RESULT: 1 FAILURES
  - /prague-stag-do/activities/ak-47-glock-17-shooting: description 160 chars (>155)
```

Prod content check (2026-10-15, public API EN + raw views of destinations, activities, packages,
blog, categories): 0 occurrences of `/destination/` or `/blog/` inside texts, so no DB UPDATE is
needed for phase 1.

## Deploy-day checklist (owner)

1. Deploy backend, then frontend (links in emails sent in between redirect fine).
2. Purge the Cloudflare cache.
3. Rename the blog post slug `how-to-plan-a-stress-free-group-trip` to `how-to-plan-a-stag-do`
   in the admin.
4. Run `node myhive-next/scripts/check-redirects.mjs https://www.trivlu.com`; open the hub and two
   activities in both languages by hand. On prod also confirm the sitemap contains
   `prague-stag-do` (the one check the dev seed cannot show).
5. Submit the sitemap in GSC; the SEO manager watches GSC for four weeks.

## Open items

Deferred minors from the review ledger (none blocks the merge):

- `routes.js`: the `TRANSLATIONS` sort is a no-op (regexes are anchored); `matchCatalogPath` hub
  branch uses `startsWith` (accepts empty/nested slug) while the legacy branch is strict;
  `DESTINATION_HUBS[destSlug]` sees inherited props (use `Object.hasOwn` or a Map); edge-case
  tests are thin (`localizeHref('/de?x=1')`, `'/ideas/'`, nested child).
- `PublicPaths`: `PublicPathsTest` pins `/destination/null` for a null slug and uses JUnit
  `assertEquals` where the suite uses AssertJ; the literal `'en'` check could reuse
  `Translations.normalize` like `EmailService.localePrefix`.
- CRA: `ActivityCard.js` and `ActivityPreviewModal.js` double-localize
  (`lp(routePath(...))`, safe through idempotence); `Layout.js` style drift and a dropped
  rationale comment above `localizedPaths`.
- Next: `legacyUrlRedirects()` is untested (unexported in `next.config.ts`); `isGone` ignores
  double slashes; `ActivityDetail.tsx`/`PackageDetail.tsx` duplicate `capitalizeFirst`,
  `destinationNameFromSlug`, `truncate` (lift into `components/site/catalog/text.ts`); the
  `(DESTINATION_HUBS as Record<string,string>)` cast x3 in the legacy wrappers; `smoke.mjs`
  hreflang regex assumes attribute order; stale "cannot drift" header comments in the shared
  catalog modules.
- Docs pending owner approval (workflow rule 3): README, CLAUDE.md (URL scheme, `routes.js` as
  owner, `PublicPaths` mirror) and memory updates.
- **Phase 2:** 9 single categories + category pages, package overview page, article texts for
  `/ideas`, GA4 `page_type`. **Phase 3:** German slugs (`slug_de`), after which the moved post's
  DE URL can carry a German slug.
