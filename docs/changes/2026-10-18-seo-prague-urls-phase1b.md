# 2026-10-18 — SEO URL restructure, phase 1b: final slugs, primary category, sitemaps

Branch `feat/seo-prague-urls` (base `main`). Spec:
`docs/superpowers/specs/2026-10-18-seo-prague-urls-phase1b-design.md`. Sources: the SEO
manager's two ТЗ (URL/308 redirects and Т-10 sitemaps). Builds on phase 1
(`docs/changes/2026-10-15-seo-prague-urls-phase1.md`), whose deploy checklist this document
replaces.

**Not in this phase:** the category pages (phase 2), German texts for the new activities and the
packages, CSV columns for `slug_de` / primary category.

## What moved

| Item | Count / result |
| --- | --- |
| Mapped Prague activities | 72 |
| EN slugs renamed | 47 (25 unchanged) |
| DE slugs equal to the EN slug | 13 |
| Prague activities on prod | 82; the 10 unmapped stay noindex on their EN slug under `/de/`, no primary category |
| Prague packages | 10, all noindex |
| Primary categories | 10 (shooting 5, driving 7, action 11, beer 10, food 9, boats 3, nightlife 8, strippers 5, pranks 11, transfers 3) |
| Redirect rules | 325 = 225 explicit map rows (75 x EN/DE/`/en`) + 6 pattern fallbacks + 94 stale-cart guards |
| Kept post | `how-to-plan-a-stress-free-group-trip` -> `how-to-plan-a-stag-do` (DE `jga-planen`) |
| Retired posts | 5, deleted by the data script (were 410 in phase 1) |
| `/api/sitemap.xml` | 410 with `Cache-Control: max-age=86400, public` |
| robots.txt | blocks only `Bytespider` (owner decision 2026-10-18); `/admin`, `/vote`, `/payment` (+ `/de/`) unchanged |
| `/ideas` H1 | EN "Stag Do Tips, Ideas and Planning Advice for the Best Man", DE "JGA-Ratgeber: Tipps, Ideen und Planung für den Junggesellenabschied" |

Activities now have a German slug (`slug_de`, nullable: null = the DE page lives on the EN
slug) and a primary category. The DE URL of an activity is
`/de/jga-prag/aktivitaeten/{slugDe}`; a post is `/de/ratgeber/{slugDe}`. Packages have no DE slug.

## Design: the map and its three consumers

`myhive-next/seo/redirect-map.csv` is the single source (header
`type,old_url_en,new_url_en,old_url_de,new_url_de,category_en,category_de,note`, 81 rows; `note`
is `301` / `410` / `landing`; no quoted cells, the parser rejects them). Consumers:

1. **Backend SQL generator** `myhive-backend/scripts/seo-map-to-sql.mjs` writes the prod data
   script, the dev seed block in `data-prague.sql`, and re-keys the DE translation corpus.
2. **Next `redirects()`** via `lib/redirect-map.ts` + `lib/redirects.ts`.
3. **Sitemaps**, the `legacy-prague.xml` map (every old URL of the `301` rows).

Other pieces:

- Backend: `slug_de` on `activities` and `blog_posts` (unique); new entity `PrimaryCategory`
  (`primary_categories`, ten seeded rows, own table so the constructor `categories` and their
  consumers are untouched); `activities.primary_category_id` FK; `updated_at`
  (`@UpdateTimestamp`) on activities, blog posts, destinations, packages, categories, primary
  categories; `GET /primary-categories?locale=`. `ActivityService`/`BlogPostService` look up
  by `slug_de` first for a non-EN locale, then fall back to `slug`. `slugDe` is trimmed,
  lower-cased, `^[a-z0-9-]+$`, unique per table; blank = NULL.
- CRA: `entitySlug(entity, locale)` in `routes.js` picks `slugDe` for non-EN; call sites that
  link an activity or post use it. `PageAlternatesContext` carries per-locale paths to the
  language switcher (`alternates[l] ?? localizeHref(path, l)`).
- Next: `PageMeta.path` accepts a per-locale record, so canonical and hreflang follow the pair.
  Five entity routes moved out of the `(public)` group and render `<PublicChrome alternates>`
  themselves, so the switcher links are correct in the SSR HTML. A request on the wrong slug
  for its locale (for example `/de/jga-prag/aktivitaeten/ak47-shooting`) answers a 308 to the
  right one. Landing `/landing/prague` uses the final slugs.
- Admin: "German slug" field on activities and posts; "Primary category" select, required when
  creating an activity in the UI.

## Redirects

`legacyUrlRedirects()` returns rules in this order (first match wins), all `permanent: true`:

1. Explicit map rules: old EN -> new EN, old DE -> new DE, `/en` + old EN -> new EN (225).
2. Pattern fallbacks from `DESTINATION_HUBS` for what the map does not list (the 10 unmapped
   activities, packages), EN/DE/`/en` (6).
3. Stale-cart guards for the 47 renamed activities: `/prague-stag-do/activities/<old_en>` ->
   `<new_en>` and `/de/jga-prag/aktivitaeten/<old_en>` -> `<new_de>` (94). They protect carts in
   localStorage whose preview links carry the pre-rename slug.

Still no generic `/blog/:slug` rule (the 410s live in middleware, `lib/gone.ts`).

## Sitemaps

`app/sitemap.ts` is gone. `/sitemap.xml` is a `<sitemapindex>` over
`/sitemaps/{pages,activities,guides,articles}.xml` and `/de/sitemaps/...`; empty maps (guides)
are left out of the index. Entries carry `xhtml:link` en/de/x-default (x-default = EN), no
`priority`/`changefreq`. `lastmod` comes from `updated_at`; static pages use the constant
`STATIC_PAGES_LASTMOD = '2026-10-19'` in `lib/sitemaps.ts` (the release date).
`/sitemaps/legacy-prague.xml` lists 150 URLs (old EN and DE paths of the `301` rows), EN route
only, not in the index; remove it on 2026-11-24.

Release-day size is 154 URLs (5 pages + 72 activities per locale), not Т-10's 174, until the
category pages ship.

## Data migration

- **Flyway `V15`** (idempotent): `slug_de` on activities and blog posts with unique indexes,
  `primary_categories` with ten seeded rows, `activities.primary_category_id` FK, `updated_at`
  on six tables backfilled with `now()`.
- **Data script** `myhive-backend/prod-migration-seo-prague-urls.sql`: 72 activity UPDATEs
  (idempotent through `slug IN (old, new)`, set slug, `slug_de`, primary category,
  `updated_at`), the kept post -> `how-to-plan-a-stag-do` / `jga-planen`, DELETE of the five
  retired posts. Generated by `node myhive-backend/scripts/seo-map-to-sql.mjs`. Rows the map
  does not mention are untouched; `translations` travels with the row.
- The DE translations SQL (`prod-migration-translations-de.sql`) was regenerated for future runs
  only. Nothing to re-apply on prod.

## Deviations from the ТЗ

- `lastmod` moves on any admin save, not only on content edits (Hibernate `@UpdateTimestamp`).
- Legal pages are out of the sitemap (Т-10 lists only content-plan pages, about, contact).
- Indexable packages would go into `pages.xml` (none today).
- 154 URLs, not 174, until category pages exist.
- Primary category is required only in the admin UI; the backend stays lenient, so CSV creates
  and other cities are unaffected.
- The 10 unmapped activities need map rows (slug_en, slug_de, category) from the SEO manager or
  stay noindex on their EN slug.
- `Cache-Control` on the sitemap 410 is emitted as `max-age=86400, public` (same directives as
  the ТЗ, builder order).
- The `/en/` single-hop rules are an addition the ТЗ did not ask for.
- `check-redirects` in legacy mode reports prod-only records as warnings on dev data.
- German and English slugs share one URL space: a `slug_de` cannot equal another row's `slug`, and a `slug` (admin form, CSV create) cannot equal another row's `slug_de`; the services reject it with a 400, the CSV importer suffixes a colliding generated slug.

## Gotchas

- `git mv` of route directories fails while a process holds `.next`; the five routes were moved
  file by file.
- `node --test` needs a file path on Node 24.
- `tsc` reports stale `.next/types` errors until the next build.
- vitest cannot resolve the `@/` alias, hence the relative import in `lib/seo.ts`.
- `smoke.mjs` robots check needs `ALLOW_INDEXING=true` on the server under test (otherwise
  robots is Disallow-all by design).
- CRA `TripBuilder.test.js` times out occasionally under full-suite load; it passes alone and
  the final full run was clean.
- Carried over from phase 1: bash heredocs collapse backslashes (use Edit/Write); local E2E needs
  `INTERNAL_API_TOKEN` on both servers; Steam can hold port 8080 (the run used 8082/3002).

## Verification

Local end-to-end (backend dev on :8082, Next dev on :3002, `INTERNAL_API_TOKEN` set on both):

| Item | Result |
| --- | --- |
| `check-redirects.mjs --legacy-sitemap docs/seo/legacy-api-sitemap-2026-10-18.xml` | `checks: 1088`, `errors: 0`, `warnings: 461`; catalog `72 mapped, 0 unmapped` |
| `smoke.mjs` (`ALLOW_INDEXING=true`) | `RESULT: 1 FAILURES`: `ak47-shooting` description 160 chars (> 155), dev-seed data, pre-existing |
| `next build` / `tsc --noEmit` | green / clean |
| Next vitest | 67 / 67 |
| CRA Jest | 101 suites, 781 tests passed |
| Backend `./gradlew test` | 1457 tests, 0 failures, 0 errors, 8 skipped |

Two script bugs were fixed during the run (`32440fdc`, then the classification fix `25552bbb`);
the first run had `errors: 50`, none from application code.

Hand checks on the local build:

- `/de/jga-prag/aktivitaeten/paintball` -> 200
- `/de/jga-prag/aktivitaeten/ak47-shooting` -> 308 to `/de/jga-prag/aktivitaeten/kalaschnikow-schiessen`
- `/de/ratgeber/how-to-plan-a-stag-do` -> 308 to `/de/ratgeber/jga-planen`
- `/de/ratgeber/jga-planen` -> 200, `noindex, follow`
- `/de/jga-prag/aktivitaeten/kalaschnikow-schiessen` -> 200, H1 "Schießen mit AK-47 und Glock 17", canonical = itself, hreflang en = `/prague-stag-do/activities/ak47-shooting`, switcher EN link the same (Playwright)
- `/prague-stag-do?tab=trip-builder` -> `noindex, follow`, SPA mounts
- Admin activity form select: not checked (no dev admin credentials)

Docker dry-run (postgres:18) of V15 and the prod SQL: each ran twice without error, idempotent;
`primary_categories` count 10; `slug_de` populated (for example beer-tasting/bierverkostung,
go-karting/kartfahren). The only dump available, `neon_backup_clean.sql`, is a stale 5-table
dump (activities, blog_posts, booking_items, bookings, destinations), so empty stub tables for
`packages`, `categories`, `activity_categories` were created first. **The migration has not been
run against the real prod schema**; run V15 on prod with the usual attention (it is idempotent
and needs `packages` and `categories` to exist, which prod has).

## Deploy order

Replaces phase 1's checklist. Spec §8:

1. Before merging: set `STATIC_PAGES_LASTMOD` in `myhive-next/lib/sitemaps.ts` to the release date.
2. Pause the FRONTEND auto-deploy on Render, or put `[skip render]` in the merge commit message
   (Render's documented skip phrase; both services log a `commit_ignored` event) and trigger the
   two deploys by hand in order; merge. Backend deploys first: Flyway V15 runs
   (columns, table, seed, `updated_at = now()`).
3. Run `psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f myhive-backend/prod-migration-seo-prague-urls.sql`
   on the prod database (Render shell). The script aborts and rolls back on a slug collision or a wrong count. Window: old frontend keeps working (its links now carry the new slugs under the old
   scheme, which it resolves), only bookmarked old-slug URLs 404 for a few minutes.
   Before it, check that no activity of another city sits on a new EN or DE slug:
   `activities.slug` is globally unique and the script's `WHERE slug IN (old, new)` would update
   that foreign row too. On 2026-10-19 Riga's `stag-arrest-prank` (noindex) had to become
   `stag-arrest-prank-riga` first.
4. Deploy the frontend **with "Clear build cache & deploy"** (Render restores the previous build's
   `.next/cache/fetch-cache`, and the build-time prerender of `/ideas`, `/`, legal pages would
   embed pre-migration catalog data). Purge the Cloudflare cache. Turn off Cloudflare "Block AI bots".
5. `node myhive-next/scripts/check-redirects.mjs https://www.trivlu.com --legacy-sitemap
   docs/seo/legacy-api-sitemap-2026-10-18.xml`; `node myhive-next/scripts/smoke.mjs https://www.trivlu.com`;
   open the hub and two activities in both languages; flip the switcher on five activities.
6. SEO manager: GSC domain property + `/de/` property, submit `/sitemap.xml` and
   `/sitemaps/legacy-prague.xml`, remove `/api/sitemap.xml`, Bing, annotation in GA4.
7. 2026-11-24: delete `legacy-prague.xml` (code + GSC).

If single addresses are wrong: fix the rule, redeploy, never roll the move back (Google saw
308s). If the site 5xx's: roll back both services and move to the next Tuesday.

Exact commands:

```
# before merge: set STATIC_PAGES_LASTMOD in myhive-next/lib/sitemaps.ts to the release date
# 1. Render: pause FRONTEND auto-deploy (or merge with "[skip render]" and deploy by hand), wait for the backend deploy (Flyway V15)
# 2. data script (Render shell, or local psql with the external URL)
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f myhive-backend/prod-migration-seo-prague-urls.sql
# 3. deploy the frontend; purge Cloudflare; turn off Cloudflare "Block AI bots"
# 4. acceptance
cd myhive-next && npm run sync-legacy
node scripts/check-redirects.mjs https://www.trivlu.com --legacy-sitemap ../docs/seo/legacy-api-sitemap-2026-10-18.xml
node scripts/smoke.mjs https://www.trivlu.com
```

Expect `errors: 0` and unmapped = 10 from `check-redirects`. Hand checks: the hub; two
activities in both languages; the switcher on five activities; `/de/ratgeber/jga-planen` is 200
with `noindex`; `/api/sitemap.xml` is 410; `/robots.txt` shows only the `Bytespider` block.
Prod `check-redirects` fires many requests and may hit Cloudflare rate rules (the script
throttles; retry on 403/429).

## Release log 2026-10-19

- `STATIC_PAGES_LASTMOD` set to `2026-10-19` (`04dd4f3c`); merge commit `14b8f439` on `main` carries
  `[skip render]`, both Render services logged `commit_ignored`, deploys were triggered through the
  Render API in order.
- Read-only pre-checks on prod before anything moved: Flyway at V14, 82 Prague activities, all 72
  mapped rows on their old slugs, the 10 unmapped ones `noindex`, 6 posts (all `noindex`, all with
  German text). One collision: Riga's `stag-arrest-prank` on a slug Prague moves to (see step 3).
- Backend deploy `dep-dbb3h9vavr4c738js25g` live 15:37 UTC; V15 applied (10 primary categories,
  `updated_at` on all 552 activities).
- Data script run from a laptop with `psql` (the Riga rename first, in its own transaction):
  73 `UPDATE 1`, `DELETE 5`, post-check passed, 72 Prague rows with `slug_de` and a primary
  category, one post left (`how-to-plan-a-stag-do` / `jga-planen`).
- Frontend deploy `dep-dbb3jijbc2fs73dd9al0` live about three minutes later; old-slug URLs were
  404 for that window only.
- Hand checks through Cloudflare all as expected (hub 200 both languages, every old URL one 308
  hop including `?tab=` query strings, `/de/jga-prag/aktivitaeten/ak47-shooting` → the German slug,
  retired post 410, `/api/sitemap.xml` 410, sitemap index with four maps, robots with only
  `Bytespider`, `noindex` on the kept DE post, canonical + three hreflang links on a DE activity).
  Cloudflare served the new `robots.txt` straight away (`cf-cache-status: EXPIRED`, revalidated) and
  does not cache the sitemaps (`DYNAMIC`), so no purge was needed.
- `check-redirects` on prod: 1177 checks, 0 errors, 1 warning (the Riga activity's old legacy-sitemap
  URL answers 404 after its rename; it was `noindex`, accepted). `smoke` found `/ideas` and
  `/de/ratgeber` still listing the six old posts although `/api/blog` had one: the pages are
  prerendered at build time (`revalidate = 3600`) and Render restored the previous build's
  cache (544 MB), which carries Next's persisted fetch cache (`.next/cache/fetch-cache`); an
  expired entry is served stale to the prerender and only revalidated in the background. Fixed by
  a second frontend deploy with "Clear build cache" (`dep-dbb3qbigjjms73ctg28g`). Rule for the
  next data migration: deploy the frontend with the build cache cleared, or expect prerendered
  pages to show pre-migration catalog data for up to two ISR cycles.
- `smoke` also reported `/contact` without `info@trivlu.com`: through Cloudflare the address is
  rewritten by "Email Address Obfuscation" (`/cdn-cgi/l/email-protection`, `data-cfemail`). The
  page is fine; the script now accepts the obfuscated form.
- Cloudflare: the AI-bot block lives in Security → Settings → "Configure AI bot policies" (the
  old "Block AI bots" switch); Search and Agent were already "Allow", Training was "Disallow" and
  is now "Allow (do not block)" (saved 2026-10-19, after the owner logged in). "Bot Preference
  Sync" stays off so Cloudflare does not prepend its own rules to our `robots.txt`.
- Still with people: GSC/Bing/GA4 steps (SEO manager).

## Open items

- Phase 2: category pages (need the SEO texts); the sitemap grows from 154 to 174 URLs.
- German texts for the 10 new activities and the 10 packages.
- 2026-11-24: remove `legacy-prague.xml` (code + GSC entry).
- CSV import/export columns for `slug_de` and primary category.
- Map rows (slug_en, slug_de, category) for the 10 unmapped activities, from the SEO manager.
- Composite slug uniqueness (destination + slug): the Riga collision shows the global unique
  `activities.slug` bites with every new city; the Ф3 trigger ("second city") has fired.
- Deferred review minors (the final review triaged all of them as "can wait"; the
  `loadCatalog` one was fixed in `cd865f58`):
  - `applySlugDe` twin in two services
  - `editingRef` written during render in `AdminActivities` (cleaner: `validate(form, editing)`)
  - `mapByNew` keyed by slug only in `check-redirects` (fix before a second city enters the map)
  - unguarded `.json()` in `smoke.mjs`
  - legal pages / x-default polish
- Residual notes from the final review, parked with the owner's call pending:
  - an English request on a German slug (`/prague-stag-do/activities/bierbad`) answers 404; a
    308 to the English slug would be friendlier, the spec is silent
  - the 94 stale-cart guards have no `/en/` variants (two hops, never-published URLs)
  - `app/sitemaps/[name]` and `app/de/sitemaps/[name]` accept any name; a random
    `/sitemaps/x.xml` 404 gets its own ISR cache entry (`dynamicParams = false` would close it)
  - an unknown `primaryCategoryId` in an admin write answers 404, not 400
  - re-running the data script moves `updated_at` (sitemap `lastmod`) on all 72 rows even
    when nothing changes
  - `?locale=<anything but en>` triggers the DE-first slug lookup (`Translations.normalize`
    folds only `en`); only `en`/`de` are ever sent
  - the slug-collision preflight in the prod script is not scoped to Prague and does not
    exclude the old EN slugs of renamed rows (fail-closed; cannot trigger on the current map)

## Commits

```
c09f93c1 feat(backend): primary_categories, slug_de and updated_at columns with Flyway V15 and dev seed
b980727d feat(backend): German slugs resolved DE-first, primary category on activities, /primary-categories
88ecf81f feat(backend): /sitemap.xml answers 410 (superseded by Next sitemaps, T-10)
df2b80de feat(backend): generate the Prague slug/category import from the redirect map; dev seed on final slugs
f458dea9 test(backend): negative validation and dev-block tests for seo-map-to-sql; CRLF-stable prod file
43233e46 feat(next): 308 rules generated from the redirect map, with pattern fallbacks and stale-cart guards
0b62f9bf test(next): pin guard-rule destinations for a renamed activity
029b9450 feat(next): sitemap index with per-type/per-language maps, lastmod and legacy-prague map (T-10)
4ed32fdf feat(cra): entitySlug picks the locale's slug; language switcher honours page alternates
24da5350 feat(next): EN/DE slug pair drives canonical, hreflang, page alternates and wrong-slug 308s
2f0bdd29 feat(ui): activity and post links use the locale's slug; /ideas H1 from SEO; landing on final slugs
174d0af5 feat(admin): German slug fields and a required primary category for new activities
8e9990f9 feat(next): robots.txt blocks only Bytespider (owner decision 2026-10-18)
d6ea2ce6 test(next): assert the exact robots rule set
ba594491 chore(next): redirect check driven by the map (+legacy sitemap mode); smoke covers the sitemap index
32440fdc fix(next): check-redirects accepts the wrong-slug hop on DE fallbacks and warns on prod-only catalog records
080123e8 fix(next): smoke follows the activity's German slug and the new /ideas H1
25552bbb fix(next): check-redirects classifies unmapped activities by their current slug
```
