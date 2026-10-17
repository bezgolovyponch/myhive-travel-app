# 2026-10-15 — SEO URL restructure, phase 1: Prague hub URLs, 308 redirects, /ideas

Branch `feat/seo-prague-urls` (base `main`). Spec:
`docs/superpowers/specs/2026-10-15-seo-prague-urls-phase1-design.md`; plan:
`docs/superpowers/plans/2026-10-15-seo-prague-urls-phase1.md`. Source: the SEO manager's ТЗ
"новые URL и 308-редиректы trivlu.com" (Google Doc `1ZU_jG_AgJqENmaudAygCcq3aAqhhkT6X6HZDz0tU3T4`),
scoped down to what needs no backend schema change.

**This is phase 1 only.** Phase 2 (9 single categories + category pages) and phase 3 (German
slugs, `slug_de`) are NOT done and are separate specs.

The section label changed from "Blog" to "Ideas" (DE "Ratgeber") in the header, footer, landing
footer, breadcrumb and back-link; the page title/H1 are untouched (content plan).

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
  (first `check-redirects` run, before the fix round: 69 FAIL, all of this kind). The script sends `X-Internal-Token`
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
0b7d1cec test(next): check-redirects counts its checks, fails on zero coverage, reports aborts and missing Location
```

## Verification

Test counts: CRA Jest 769, backend 1435, Next vitest 37 (including the middleware-matcher test),
`tsc` clean, `next build` green.

Local end-to-end run (backend on :8081, Next on :3000, `INTERNAL_API_TOKEN` set on both):

| Item | Result |
| --- | --- |
| `check-redirects.mjs` | PASS, `checks: 192`, `errors: 0` (hub prague: 72 activities, 9 packages) |
| `smoke.mjs` | FAIL, 1 failure: pre-existing dev data (escaped description length 160 > 155 on `ak-47-glock-17-shooting`; page code identical to `main`) |
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

`check-redirects.mjs` behaviour (after fix round `0b7d1cec`): prints the base URL and an ISO
timestamp first, then per-hub activity/package counts; counts every assertion (`checks: N`
before `errors: N`). It FAILs when a hub has zero activities, when no hub destination exists, or
when zero assertions ran (so an empty run can no longer pass as `errors: 0`); a rejected fetch or
non-JSON body becomes `FAIL script aborted: ...` with errors >= 1; a redirect without `Location`
is an explicit FAIL. 192 = 2 locales x (2 hub + 72 activity + 9 package) + 2 x (blog 1 + gone 5 +
404 + landing + not-a-city) + 6 non-hub destination checks.

Verbatim output lines:

```
check-redirects base=http://localhost:3000 at 2026-10-15T19:23:32.222Z
hub prague: 72 activities, 9 packages
checks: 192
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

1. Deploy the FRONTEND first, then the backend. The new frontend 308s the old backend's `/destination/prague?...` email links; a new backend emails `/prague-stag-do?tab=trip-builder&restore=...` and `/de/jga-prag?...`, which the old frontend 404s (its catch-all calls notFound) during the Next build window. Render auto-deploy on merge must not start the backend first: pause backend auto-deploy, or deploy the backend manually once the frontend is live.
2. Purge the Cloudflare cache.
3. Immediately after the frontend is live (until then `/ideas/how-to-plan-a-stag-do` 404s and the old `/blog/...` URL 308s into it), rename the blog post slug `how-to-plan-a-stress-free-group-trip` to `how-to-plan-a-stag-do`
   in the admin. In the same sitting delete the five retired posts (`top-5-group-travel-destinations-for-2026`,
   `why-ai-is-changing-the-way-we-travel-together`, `bali-on-a-budget-a-group-travel-guide`,
   `the-rise-of-multi-traveler-experiences`, `weekend-getaway-ideas-for-large-groups`): their old `/blog/...`
   URLs answer 410, but while the records exist `/ideas` still lists them and `/ideas/<slug>` serves them
   (200 + noindex; seen in the local browser check 2026-10-15).
4. Run `node myhive-next/scripts/check-redirects.mjs https://www.trivlu.com`; open the hub and two
   activities in both languages by hand. On prod also confirm the sitemap contains
   `prague-stag-do` (the one check the dev seed cannot show).
   Then check by hand, EN and DE: `curl -sI https://www.trivlu.com/blog/how-to-plan-a-stress-free-group-trip` → 308 → 200.
5. Submit the sitemap in GSC; the SEO manager watches GSC for four weeks.
6. Further post-deploy checks: `curl -s https://www.trivlu.com/de/jga-prag/aktivitaeten/<slug> | grep '<h1'` confirms the DE SSR body. If `/api/sitemap.xml` (the superseded backend sitemap, which still lists old URLs) was ever submitted in GSC, remove it there. Review GA4 explorations/audiences keyed on `/destination/` or `/blog` page paths (owner / SEO manager). `check-redirects.mjs` against prod fires ~300 requests and may hit Cloudflare rate rules: run it with a pause or retry on 403/429.

## Open items

The deferred minors from the review ledger were addressed on 2026-10-17:

- CRA `a79626f1`: `routes.js` strictness, `Object.hasOwn` for the hub lookup, JSDoc types,
  single localization (no double `lp(routePath(...))`), `Layout.js` style.
- Backend `b6f2ff5e`: `PublicPaths` reuses `Translations.normalize`, a null slug is a
  programming error (NPE), `PublicPathsTest` uses AssertJ.
- Next (the commit carrying this note): `legacyUrlRedirects()` moved to `lib/redirects.ts` with an
  exact-rule test, plus single-hop rules for `/en/`-prefixed old URLs; `isGone` collapses repeated
  slashes; shared `components/site/catalog/text.ts`; the `DESTINATION_HUBS` casts are gone;
  `smoke.mjs` parses hreflang link tags in any attribute order; `check-redirects.mjs` names the
  failing URL when it aborts.

Deliberately still open:

- The kept-post redirect (`/blog/how-to-plan-a-stress-free-group-trip`) can only be checked live
  after the slug is renamed in the admin.
- `check-redirects.mjs` has no rate-limit delay (see step 6 above).
- Docs pending owner approval (workflow rule 3): README, CLAUDE.md (URL scheme, `routes.js` as
  owner, `PublicPaths` mirror) and memory updates.
- **Phase 2:** 9 single categories + category pages, package overview page, article texts for
  `/ideas`, GA4 `page_type`. **Phase 3:** German slugs (`slug_de`), after which the moved post's
  DE URL can carry a German slug.
