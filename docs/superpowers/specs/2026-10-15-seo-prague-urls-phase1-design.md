# SEO URL restructure, phase 1: Prague hub URLs, 308 redirects, /ideas

**Date:** 2026-10-15
**Source:** the SEO manager's ТЗ "новые URL и 308-редиректы trivlu.com" (Google Doc
`1ZU_jG_AgJqENmaudAygCcq3aAqhhkT6X6HZDz0tU3T4`), scoped down to what needs no
backend schema change. Phases 2 (9 single categories + category pages) and 3
(German slugs) are separate specs.

## Goal

Move the Prague catalog from `/destination/prague/...` to keyword URLs, with
German path *segments* (not yet German slugs), and make every old address a
single 308 to its new one. Internal links, canonical, hreflang, sitemap and
email deep links all emit the new addresses. Other destinations stay on
`/destination/{city}` untouched.

## New addresses

| Page | EN (internal form) | DE |
| --- | --- | --- |
| Prague hub (catalog + `?tab=` Trip Builder + `?voteSession=`) | `/prague-stag-do` | `/de/jga-prag` |
| Activity | `/prague-stag-do/activities/{slug}` | `/de/jga-prag/aktivitaeten/{slug}` |
| Package detail | `/prague-stag-do/packages/{slug}` | `/de/jga-prag/pakete/{slug}` |
| Article index | `/ideas` | `/de/ratgeber` |
| Article | `/ideas/{slug}` | `/de/ratgeber/{slug}` |
| Other cities | `/destination/{city}`, `/destination/{city}/activity/{slug}`, `/destination/{city}/package/{slug}` | same with `/de` prefix |
| Landings | `/landing/prague`, `/landing/vote` | unchanged |

Slugs are the existing EN slugs in both languages (phase 3 adds `slug_de`).
Packages move now, ahead of the ТЗ's 13.11 date, because prod has zero public
packages, so moving the route costs nothing and avoids a second redirect later.
No package overview page in this phase.

## Redirects (next.config `redirects()`, `permanent: true` = 308)

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

Deviation from the ТЗ: the activity/package rules are `:slug` masks scoped to
`prague`, not 72 literal rows, because the slug set lives in the database and a
literal list would rot. The ТЗ's objection to masks was about catching other
cities; a prague-scoped mask cannot. Query strings pass through (Next default).
The moved post keeps its EN slug on the DE side (no DE slugs yet); its slug
must be renamed in the admin to `how-to-plan-a-stag-do` on deploy day.

**410** for the five seed posts, EN and DE (10 URLs), from `middleware.ts`
(`redirects()` cannot emit 410). Redirects run before middleware in Next, so
there is deliberately NO generic `/blog/:slug` rule: it would swallow the 410s.
Any other `/blog/...` URL is a real 404 (the route folder is gone).

```
top-5-group-travel-destinations-for-2026
why-ai-is-changing-the-way-we-travel-together
bali-on-a-budget-a-group-travel-guide
the-rise-of-multi-traveler-experiences
weekend-getaway-ideas-for-large-groups
```

## Design

### One table, three consumers

`myhive-react-app/src/i18n/routes.js` (already shared with Next via
`legacy-src`) becomes the single owner of the URL scheme:

```js
// destination slug -> EN-internal hub path; cities absent here stay on /destination/{slug}
export const DESTINATION_HUBS = { prague: '/prague-stag-do' };

// next-intl `pathnames` shape. EN is the internal form (= the Next route
// folder); DE is the public form. Unlisted paths are identical in every locale.
export const LOCALIZED_PATHNAMES = {
  '/prague-stag-do':                    { en: '/prague-stag-do',                    de: '/jga-prag' },
  '/prague-stag-do/activities/[slug]':  { en: '/prague-stag-do/activities/[slug]',  de: '/jga-prag/aktivitaeten/[slug]' },
  '/prague-stag-do/packages/[slug]':    { en: '/prague-stag-do/packages/[slug]',    de: '/jga-prag/pakete/[slug]' },
  '/ideas':                             { en: '/ideas',                             de: '/ratgeber' },
  '/ideas/[slug]':                      { en: '/ideas/[slug]',                      de: '/ratgeber/[slug]' },
};
```

Consumers:

1. **Next middleware** — `i18n/routing.ts` passes `pathnames: LOCALIZED_PATHNAMES`
   to `defineRouting`, so `/de/jga-prag/aktivitaeten/x` is rewritten to the
   file route `app/[locale]/prague-stag-do/activities/[aslug]`. Only EN-named
   route folders exist.
2. **Link/canonical helpers** (both apps) — `localizePath`/`localizeHref` gain
   segment translation; see API below.
3. **Backend** — `util/PublicPaths.java` mirrors `DESTINATION_HUBS` and the
   hub rows of the table for email deep links. Two copies by design (Java
   cannot read the JS); each carries a comment naming the other.

### routes.js API

Pure builders return the **EN-internal** path (no locale, no prefix):

- `destinationPath(destSlug)` → `/prague-stag-do` | `/destination/krakow`
- `activityPath(destSlug, slug)` → `/prague-stag-do/activities/x` | `/destination/krakow/activity/x`
- `packagePath(destSlug, slug)` → `/prague-stag-do/packages/x` | `/destination/krakow/package/x`
- `blogIndexPath()` → `/ideas`; `blogPostPath(slug)` → `/ideas/x`

Translation (all tolerate a `?query#hash` suffix and leave it untouched):

- `localizeSegments(path, locale)` — EN-internal → that locale's public
  segments, no prefix. Longest pattern wins, `[slug]` matches one segment
  (`:slug` route params pass through). Idempotent: input that matches no EN
  pattern is returned unchanged, so an already-German path is safe.
- `delocalizeSegments(path, locale)` — inverse.
- `routePath(path, locale = currentLocale())` — `localizeSegments` for CRA
  `<Link to>`, `navigate()` and `<Route path>` (the SPA's BrowserRouter adds
  the `/de` basename itself).
- `localizePath(path, locale)` — prefix + `localizeSegments` (Next
  `canonical()`, sitemap, `useLocalePath`).
- `localizeHref(href, locale)` — unchanged contract (prefix unless external
  or already prefixed) plus `localizeSegments` on the path part.
- `matchCatalogPath(pathname, locale = currentLocale())` →
  `{ destinationSlug, kind: 'hub'|'activity'|'package' } | null` for both
  the hub form and the legacy `/destination/...` form. Replaces the
  `/^\/destination\//` regexes in `Header.js`.

Why the CRA links carry locale segments themselves (instead of translating at
the router boundary): the SPA mounts a real `BrowserRouter` whose basename is
only the prefix; links inside it must already be `/jga-prag/...` or the URL
bar shows a non-canonical address. `currentLocale()` is correct in both worlds
(URL in the browser, `LocaleProvider`-bound during SSR). Next server code
never uses the implicit-locale helpers: it builds the EN-internal path and
passes it through `canonical(path, locale)`.

### CRA route table

`Layout.js` registers, for every locale, the localized variant of each hub
pattern (`routePath(pattern, l)` for `l` of `SUPPORTED_LOCALES`, deduped), so
`/prague-stag-do` and `/jga-prag` both render `DestinationPage` with a
`destinationSlug` prop; the old `/destination/:slug` routes stay for other
cities. `DestinationPage`, `ActivityDetailPage`, `PackageDetailPage` take the
destination slug from the prop when present, else from `useParams()`.
`/ideas` and `/ratgeber` render `BlogPage`/`BlogPostPage`.

### Next pages

- `app/[locale]/prague-stag-do/page.tsx` (hub, keeps the `?tab=`/`?voteSession=`
  → `LegacyAppShim` escape hatch and its `noindex`), and
  `app/[locale]/(public)/prague-stag-do/activities/[aslug]/page.tsx`,
  `.../packages/[pslug]/page.tsx`. Each is a thin file: the existing page
  bodies move into shared server modules under `components/site/catalog/`
  taking `{ destinationSlug, locale, ... }`, and the old `destination/[slug]`
  routes call the same modules. The destination slug of a hub folder is a
  constant in that file with a comment pointing at `DESTINATION_HUBS`.
- `(public)/blog` → `(public)/ideas`.
- Legacy wrappers (`LegacyDestination`, `LegacyActivityDetail`,
  `LegacyPackageDetail`, `LegacyBlog`, `LegacyBlogPost`) compute their
  `RouteMatch` pattern with `routePath(pattern, useLocale())`, since the
  bridge feeds them the public (German) path.
- `sitemap.ts`, `lib/seo.ts` breadcrumbs, `components/landing/data.ts`,
  `smoke.mjs` use the builders. hreflang/canonical/og:url/BreadcrumbList
  follow automatically through `canonical()`.

### Language switcher

Both switchers (`LanguageSwitcher.js`, `LandingLanguageSwitcher.tsx`) hold a
locale-free path that may carry German segments; they `delocalizeSegments`
it for the current locale, then `localizeHref` for the target.

### Backend

`PublicPaths.destinationPath(slug, localeCode)` → `/prague-stag-do`,
`/de/jga-prag`, `/destination/krakow`, `/de/destination/krakow`.
`EmailService` uses it for the three Trip Builder deep links (vote dashboard,
vote result, lead restore). Vote links (`/vote/...`) are unchanged. The
superseded `SitemapController` is left as is.

## Out of scope (later phases or owner actions)

- 9 single categories, category pages, German slugs, package overview page,
  article texts for `/ideas` (content plan), GA4 `page_type`.
- Rewriting `/destination/prague` occurrences inside DB texts (checked on
  prod at the end of this phase; an UPDATE is prepared only if any exist).
- README / CLAUDE.md / memory updates: after owner approval per the
  workflow rules.

## Acceptance (local, then prod after deploy)

`myhive-next/scripts/check-redirects.mjs <base-url>`: for every destination
from the API, old hub/activity/package URLs → exactly one 308 → 200 with
the query string preserved (EN and DE); the 10 blog URLs → 410 with no hop;
`/landing/prague`, `/de/landing/prague` → 200 with no hop; destinations
without a hub → 200 on the old URL with no hop; `/blog` → `/ideas` single hop.
`smoke.mjs` passes on the new URLs. CRA Jest, Next vitest, backend tests green.
`next build` green.

## Deploy-day checklist (owner)

1. Deploy backend then frontend (links in emails sent in between redirect fine).
2. Purge the Cloudflare cache.
3. Rename the blog post slug `how-to-plan-a-stress-free-group-trip` →
   `how-to-plan-a-stag-do` in the admin.
4. Run `check-redirects.mjs https://www.trivlu.com`; open hub + two
   activities in both languages by hand.
5. Submit the sitemap in GSC; the SEO manager watches GSC for four weeks.
