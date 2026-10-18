# SEO Prague URLs, phase 1b: final slugs, primary category, sitemaps — design

Date: 2026-10-18. Branch: `feat/seo-prague-urls` (continues phase 1, not merged yet).
Owner decision (2026-10-18): "вариант В" — do NOT ship phase 1 as built; extend the branch so the
hub, activities and `/ideas` go live ONCE on their final EN and DE addresses, add the primary
category field with its import, the sitemap set from ТЗ Т-10, 410 for the old backend sitemap,
and open robots.txt for AI bots. Category pages (phase 2) wait for the SEO texts and are NOT in
this spec.

Sources: SEO manager's ТЗ "новые URL и 308-редиректы" (Google Doc
`1ZU_jG_AgJqENmaudAygCcq3aAqhhkT6X6HZDz0tU3T4`, updated 18.10), ТЗ Т-10 "sitemap trivlu.com"
(`1DVaxmFr8R6YNmBQQmvVR0mHc4OopFvtvCe6W6Tvq10U`), the chat answers of 18.10 (H1 for `/ideas`,
robots.txt text, GSC plan), phase 1 spec `2026-10-15-seo-prague-urls-phase1-design.md` and its
change doc `docs/changes/2026-10-15-seo-prague-urls-phase1.md`. Read those first; this document
only states what changes on top of phase 1.

## Intent

The ТЗ's one principle: every Prague address changes exactly once. Phase 1 kept the current EN
slugs and served DE pages on EN slugs, which would have meant a second move for 47 EN and ~67
DE activity addresses. Nothing is indexed yet, so doing the final slugs now is free; later it is
another 100+ redirects and a confused index.

## Goals

1. Prague activities live at `/prague-stag-do/activities/{slug_en}` and
   `/de/jga-prag/aktivitaeten/{slug_de}` with the slugs from the ТЗ map; the kept blog post at
   `/ideas/how-to-plan-a-stag-do` and `/de/ratgeber/jga-planen`.
2. Every old address from the map 308s in ONE hop to the final address of the same language.
3. Each Prague activity carries a primary category (one of ten), imported from the map; the
   existing many-to-many categories ("constructor labels") are untouched.
4. Sitemaps per ТЗ Т-10: index + per-type/per-language maps, `lastmod` from `updated_at`,
   hreflang incl. x-default, a temporary `legacy-prague.xml`; `/api/sitemap.xml` answers 410.
5. robots.txt opens every AI bot except Bytespider (owner decision 2026-10-18).
6. `/ideas` H1 texts from the SEO manager.
7. Language switcher, canonical, hreflang and sitemap all use the same EN/DE slug pair.

## Non-goals (explicitly later)

- Category pages, breadcrumbs through the category, BreadcrumbList with the category, "related
  activities" (phase 2, waits for texts). The field and the data land now so phase 2 is UI only.
- DE texts for the 10 new Prague activities and the 10 packages (content task).
- Packages overview page, tips section (ТЗ: 13.11 / 20.11).
- GA4 `page_type` / `add_to_trip.category` (ТЗ task 5).
- CSV import/export of `slug_de` / primary category (follow-up; the importer leaves both
  untouched on update and null on create).
- Admin CRUD for primary categories (seeded; ten rows; YAGNI).
- A `legacy_slug` column: stale carts and never-published phase-1 URLs are covered by redirect
  rules instead (see Redirects, rule group 3).

## Anti-goals

- Any intermediate address: no DE page on an EN slug for a mapped activity, no EN page on the
  old slug under the new scheme (both answer with a 308 to the final address).
- Touching `categories`, `activity_categories`, `package_categories`, quiz weights, vote
  sessions, the AI planner's category logic.
- Deleting redirect rules later: the map is permanent.

## Facts the design relies on (verified 2026-10-18)

- Map: 72 activity rows; 47 EN slugs change, 25 stay; DE slug equals the new EN slug for 13;
  no duplicate new slugs within EN or within DE. Categories: shooting 5, driving 7, action 11,
  beer 10, food 9, boats 3, nightlife 8, strippers 5, pranks 11, transfers 3.
- Prod has 82 Prague activities; the 10 not in the map (`brewery-tour`, `classic-beer-bike`,
  `escape-room-challenge-6`, `football-match-tickets`, `guided-bar-crawl`, `ice-bar`,
  `medieval-sword-fighting`, `rage-room`, `segway-tour`, `summer-bobsled`) are
  `seoIndexable=false` and have no DE translation. All 10 Prague packages are noindex.
- The kept post already has a DE translation on prod (title "So planst du eine stressfreie
  Gruppenreise"). No DB content links to `/destination/` or `/blog/` exist.
- Old backend sitemap `/api/sitemap.xml`: 559 URLs, all `https://trivlu.com` (no www), 93 Prague
  (hub + 82 activities + 10 packages). Copy saved: `docs/seo/legacy-api-sitemap-2026-10-18.xml`.
- No entity has `updated_at` except `TripLead` (`@UpdateTimestamp`). Flyway is at V14.
- Next.js: `redirects()` rules are matched in array order, first match wins (verified in the
  routing pipeline source via Context7). A static segment (`app/de/...`) wins over a dynamic
  sibling (`app/[locale]/...`). `generateSitemaps` only yields `/sitemap/[id].xml`, so Т-10's
  paths need Route Handlers. next-intl's middleware forwards incoming request headers.
- CRA `LanguageSwitcher` translates path segments only; the chrome (Header) is rendered by the
  `(public)` layout, above the page, so a page cannot hand it data through context during SSR.
  That layout does nothing but `setRequestLocale(locale)` + `<PublicChrome locale>`.
- Nothing outside the catalog tables stores an activity slug (`TripLead`, `VoteSession`,
  `BookingItem` hold ids and name snapshots); `EmailService` links to the hub only
  (`PublicPaths.destinationPath`); `AiPackageView` has no activity links. Old slugs survive only
  in browser carts (localStorage) and the lead restore snapshot, whose links are built with
  `activityPath(destinationSlug, item.slug)` at render time.
- `AdminActivities.js` is the only UI that creates activities; the CSV import is the other path.

## Design

### 1. Data model (backend)

**`slug_de` on `activities` and `blog_posts`.** `@Column(name = "slug_de", unique = true,
length = 300) private String slugDe;` nullable. Null means "no German slug: the DE page lives on
the EN slug" (the 10 new activities, the retired posts, other cities). Postgres and H2 both
allow many NULLs under UNIQUE.

**Primary category taxonomy: new entity `PrimaryCategory`**, table `primary_categories`:
`id UUID`, `slug` (unique, 120), `slug_de` (unique, 120), `name` (EN, 100), `translations`
(`TranslationsConverter`, `{"de":{"name":...}}`), `position int`, `created_at`, `updated_at`.
Seeded with exactly these ten rows (fixed UUIDs `92222222-0000-0000-0000-0000000000NN`, NN =
01..10, same in Flyway and `data.sql`). Display names are my call (the ТЗ gives Russian only);
they appear in the admin dropdown and the DTO until phase 2 brings the SEO texts:

| position | slug | slug_de | name | de.name |
|---|---|---|---|---|
| 1 | shooting | schiessen | Shooting | Schießen |
| 2 | driving | panzer-motorsport | Tanks & Motorsport | Panzer & Motorsport |
| 3 | action | action-sport | Action & Sport | Action & Sport |
| 4 | beer | bier | Beer & Drinks | Bier & Drinks |
| 5 | food | essen | Food | Essen |
| 6 | boats | boote | Boats | Boote |
| 7 | nightlife | nachtleben | Nightlife | Nachtleben |
| 8 | strippers | stripper | Strip Clubs (18+) | Stripclubs (18+) |
| 9 | pranks | streiche | Pranks & Shows | Streiche & Shows |
| 10 | transfers | transfers | Transfers | Transfers |

Why a new entity and not a flag on `Category`: the ТЗ says the constructor labels stay as they
are; every consumer of `categories` (filters, CSV, quiz weights, AI relevance, vote sessions)
would otherwise see ten new rows. Isolation is cheaper than auditing all of them.

`activities.primary_category_id UUID NULL` FK → `primary_categories(id)`, `@ManyToOne(LAZY)`.
Required for NEW activities in the admin form (ТЗ); the backend stays lenient (null allowed) so
the CSV create path, other cities and existing tests are unaffected. My call; documented.

**`updated_at`** (`@UpdateTimestamp @Column(name = "updated_at") LocalDateTime updatedAt`,
nullable column, filled by Hibernate on every insert and update) on `Activity`, `BlogPost`,
`Destination`, `Package`, `Category`, `PrimaryCategory`. It moves on any admin save, not only
on content edits — a known deviation from Т-10's "only when content changes"; the change doc
says so for the SEO manager. Raw SQL does not trigger it, so V15 backfills every NULL with
`now()` and the rename script sets it explicitly on the rows it touches (both ≈ release day).
The sitemap treats a NULL as "use the static constant".

**Flyway `V15__seo_slugs_primary_category_updated_at.sql`** (prod only, idempotent):
`ADD COLUMN IF NOT EXISTS` for `slug_de` (activities, blog_posts), `updated_at` on the six
tables, `CREATE TABLE IF NOT EXISTS primary_categories`, `activities.primary_category_id` + FK,
unique indexes on `slug_de`, the ten seed rows (`INSERT ... WHERE NOT EXISTS`), and
`UPDATE <table> SET updated_at = now() WHERE updated_at IS NULL` (Т-10: on release day every
address carries the release date). Dev/test keep Flyway off: `data.sql` gets the ten rows,
Hibernate creates the columns.

**Lookup by DE slug.** `SluggedRepository` stays EN-only. `ActivityRepository.findBySlugDe`,
`BlogPostRepository.findBySlugDe`. `ActivityService.getActivityBySlug(slug, locale)` and
`BlogPostService.getBlogPostBySlug(slug, locale)`: when `Translations.normalize(locale) != null`,
try `findBySlugDe(slug)` first, then `findBySlug(slug)` (fallback for records without a DE
slug); for EN only `findBySlug`. The generator asserts that no new DE slug collides with another
row's new EN slug, so the order is unambiguous for mapped data.

**DTOs.** `ActivityDTO`: `slugDe` (nullable), `primaryCategoryId` (write), `primaryCategory`
(`PrimaryCategoryDTO {id, slug, slugDe, name}` with `name` localized in place), `updatedAt`.
`BlogPostDTO`: `slugDe`, `updatedAt`. `DestinationDTO`, `PackageDTO`: `updatedAt`. The base
`slug` stays the EN slug in every locale (never localized in place: the frontend needs both).
Write semantics for `slugDe` (admin create/update): trimmed + lower-cased; blank or null → NULL;
must match `^[a-z0-9-]+$` else a 400 field error `slugDe`; must be unique among `slug_de` of the
same table (excluding self) else 400 `slugDe: already in use`. No transliteration (the ТЗ's
ae/oe/ue/ss convention is the editor's job; the import supplies correct values).
`primaryCategoryId`: unknown id → 404 `ResourceNotFoundException("PrimaryCategory", id)`; null →
cleared.

**Public read**: `GET /primary-categories?locale=` → `List<PrimaryCategoryDTO>` ordered by
`position` (for the admin dropdown now, category pages later). Permit-all like `/categories`.

### 2. The map and the import

**Single source**: `myhive-next/seo/redirect-map.csv`, committed, header
`type,old_url_en,new_url_en,old_url_de,new_url_de,category_en,category_de,note`, the ТЗ's 81
rows with the `note` column reduced to `301` / `410` / `landing` (no quotes, no commas inside
cells; the parser rejects any quoted cell). Already committed in `1c9d202d` together with the
old sitemap copy. It feeds three consumers: the backend SQL generator, Next's `redirects()`,
and the sitemaps (`legacy-prague`).

**Generator `myhive-backend/scripts/seo-map-to-sql.mjs`** (node, no deps, pure functions
exported for `node --test`): reads the CSV and writes

1. `myhive-backend/prod-migration-seo-prague-urls.sql` — `BEGIN; ... COMMIT;`, one statement per
   activity row:
   ```sql
   UPDATE activities SET slug = '<new_en>', slug_de = '<new_de>',
     primary_category_id = (SELECT id FROM primary_categories WHERE slug = '<category_en>'),
     updated_at = CURRENT_TIMESTAMP
   WHERE destination_id = (SELECT id FROM destinations WHERE slug = 'prague')
     AND slug IN ('<old_en>', '<new_en>');
   ```
   plus the kept post (`UPDATE blog_posts SET slug='how-to-plan-a-stag-do', slug_de='jga-planen',
   updated_at = CURRENT_TIMESTAMP WHERE slug IN (old, new)`) and `DELETE FROM blog_posts WHERE
   slug IN (<5 retired>)`. Idempotent (`slug IN (old, new)`), plain SQL that H2 also accepts.
   The retired posts are deleted here instead of by hand in the admin (phase 1 checklist item 3
   goes away). The `translations` column travels with the row, so the German content already on
   prod needs no re-apply; rows the map does not mention (the 10 unmapped activities, other
   cities) are not touched.
2. The same statements (without BEGIN/COMMIT) into `myhive-backend/src/main/resources/data-prague.sql`
   between markers `-- BEGIN seo-map (generated by scripts/seo-map-to-sql.mjs)` / `-- END seo-map`,
   so the dev seed shows the final slugs and categories, plus one dev-only line
   `UPDATE destinations SET seo_indexable = TRUE WHERE slug = 'prague'` so the local sitemap and
   smoke checks exercise the Prague URLs (today the dev hub is noindex, which hides them).
   (`data.sql` holds the retired posts; the DELETE there is harmless.)
3. Re-keys `myhive-backend/content/translations-de/activities-prague-*.json` and the `blog`
   section of `common.json` (old slug → new slug) — only so that a FUTURE run of
   `translations-to-sql.mjs` still matches rows; nothing is re-applied on prod now.

Asserts before writing: 72 activity rows, no duplicate new EN/DE slugs, no DE slug equal to a
different row's EN slug, every `category_en` among the ten, and the expected per-category
counts. Exit 1 with a message otherwise.

### 3. Redirects (Next)

`lib/redirects.ts` → `legacyUrlRedirects()` returns, in this order (order matters: first match
wins):

1. **Explicit map rules**, one triple per `301` row: `old_url_en → new_url_en`,
   `old_url_de → new_url_de`, `/en` + `old_url_en → new_url_en`. Hub, 72 activities, `/blog`,
   the kept post. 75 × 3 = 225.
2. **Pattern fallbacks** generated from `DESTINATION_HUBS` as today, for what the map does not
   list (the 10 unmapped activities, packages): `/destination/{hub}/activity/:slug →
   {hub}/activities/:slug` and `/destination/{hub}/package/:slug → {hub}/packages/:slug` for EN,
   DE and `/en`. 6 rules. The hub pattern is dropped (the map has it).
3. **Never-published scheme guards**, from map rows whose EN slug changed (47): 
   `/prague-stag-do/activities/<old_en> → /prague-stag-do/activities/<new_en>` and
   `/de/jga-prag/aktivitaeten/<old_en> → /de/jga-prag/aktivitaeten/<new_de>`. These protect
   carts persisted in localStorage before the rename (their preview links carry the old slug)
   and nothing else; 94 rules.

Total 325, all `permanent: true`. Still no generic `/blog/:slug` (410s live in middleware,
unchanged). The CSV is read with `fs` inside `lib/redirect-map.ts` (tiny parser: split on
`,`, reject a line with `"`), evaluated once at config load. Tests: rule count 325, order
(explicit before pattern before guards), no duplicate `source`, three spot rows, the `/en/`
variant, and that the kept post and `/blog` rows exist for both locales.

### 4. Pages, metadata, links (Next + CRA)

**Slug per locale.** New helper in `myhive-react-app/src/i18n/routes.js`:
`entitySlug(entity, locale = currentLocale())` → `locale !== 'en' && entity.slugDe ?
entity.slugDe : entity.slug`. Builders keep their signatures; call sites that link to an
activity or post pass `entitySlug(x)` instead of `x.slug`: `ActivityCard`,
`ActivityPreviewModal` (default link), `TripBuilder.getPreviewLink`, `PackageDetailPage`
activity links, `vote/ActivityVotePage`, `vote/CuratePage`, `BlogPage`, `BlogPostPage`
canonical, `ActivityDetailPage` canonical, landing `ActivityRows` (via `activityLink`), Next
`ActivityDetail`/`PackageDetail`/`ideas/[slug]` metadata and JSON-LD, sitemaps. Packages have
no DE slug (not in the ТЗ yet); `entitySlug` returns `slug` for them.

**Canonical + hreflang.** `lib/seo.ts`: `PageMeta.path` becomes `string | Record<string, string>`
(per-locale internal paths). `canonical(path, locale)` unchanged; `pageMetadata` picks
`paths[locale] ?? paths.en` for the canonical and builds `alternates.languages` per locale from
the matching entry, `x-default` from `en`. Activity/post metadata pass
`{ en: activityPath(dest, a.slug), de: activityPath(dest, a.slugDe ?? a.slug) }`.

**Wrong-slug requests (safety net, not the main path).** The map rules already send every OLD
address straight to the final slug of its language, and rule group 3 covers the stale-cart
variants. What remains are never-published variants such as `/de/jga-prag/aktivitaeten/axe-throwing`
(EN slug unchanged, DE slug `axtwerfen`) or `/de/ratgeber/how-to-plan-a-stag-do`, which the
DE-first-then-EN lookup would otherwise serve as duplicates. So in `ActivityDetailPage` and the
blog post page: if the resolved record's slug for the current locale differs from the requested
one, `permanentRedirect` (308) to the right address. Generic: it also covers DE slugs an admin
sets later. Not needed for packages (no DE slug).

**Language switcher gets the pair.** The chrome moves from the `(public)` layout into the five
entity pages: `(public)/prague-stag-do/activities/[aslug]`, `.../packages/[pslug]`,
`(public)/destination/[slug]/activity/[aslug]`, `.../package/[pslug]`, `(public)/ideas/[slug]`
move out of the route group (same URLs) and render `<PublicChrome locale alternates={...}>`
themselves, exactly as the hub page already renders its own chrome. The `(public)/layout.tsx`
stays for the other eight pages (home, about, contact, four legal pages, `/ideas`); it holds
nothing else (no metadata, Suspense or analytics), so the moved pages lose nothing. `PublicChrome` →
`LegacyChrome` gains an optional `alternates: Record<string, string>` prop (absolute or
locale-prefixed paths per locale) and provides it through a new CRA context
`PageAlternatesContext` (`myhive-react-app/src/i18n/index.js`, default `{}`).
`LanguageSwitcher` uses `alternates[l] ?? localizeHref(path, l)`. The standalone CRA build and
its tests see the default and behave as before. This keeps ISR (no `headers()` in a layout) and
gives crawlers correct switcher links in the SSR HTML (Screaming Frog: 0 links to 3xx).

**Landing `/landing/prague`.** `LandingActivity` gains `slugDe`; `activityLink(dest, activity,
locale)` picks the slug; `DECK_SLUGS` and the curated pool slugs are rewritten to the new EN
slugs (entries whose slug never existed live stay as they are: pre-existing, noted in the
change doc). `toCartItem` keeps `slug` (EN).

**Admin.** `AdminActivities.js`: field "German slug" (`slugDe`, optional, validator
`/^[a-z0-9-]+$/`), select "Primary category" (`primaryCategoryId`, options from
`adminApi.getPrimaryCategories()` → `GET /primary-categories`), required when creating
(validate step), optional on edit of existing records without one. `AdminBlog.js`: "German slug".

**`/ideas` H1.** `blog.listTitle`: EN "Stag Do Tips, Ideas and Planning Advice for the Best
Man", DE "JGA-Ratgeber: Tipps, Ideen und Planung für den Junggesellenabschied". `meta.blog.*`
unchanged until the SEO manager sends title/description.

### 5. Sitemaps (Next, ТЗ Т-10)

Delete `app/sitemap.ts`. New `lib/sitemaps.ts` with pure builders + XML renderers, and three
Route Handlers (`export const revalidate = 3600`):

- `app/sitemap.xml/route.ts` — `<sitemapindex>` listing every NON-empty map for both locales
  (`/sitemaps/<name>.xml`, `/de/sitemaps/<name>.xml`), each with `lastmod` = the newest entry.
- `app/sitemaps/[name]/route.ts` (EN) and `app/de/sitemaps/[name]/route.ts` (DE), `name` ∈
  `pages.xml | activities.xml | guides.xml | articles.xml`, plus `legacy-prague.xml` under EN
  only; anything else 404.

Entry = `{ loc, lastmod, alternates: {en, de} }` → `<url><loc/><lastmod/><xhtml:link
rel="alternate" hreflang="en|de|x-default"/></url>`, x-default = EN, `xmlns:xhtml` declared.
No `priority`, no `changefreq`. Rules: only pages that answer 200 and are indexable (the
`seoIndexable` gates of today incl. the destination parent rule); absolute `https://www.trivlu.com`
(`SITE_URL`), lower-case, no trailing slash, no params.

| map | content (per locale) | lastmod |
|---|---|---|
| pages | `/`, `/about`, `/contact`, `/ideas`, every indexable destination's hub or `/destination/{slug}`, indexable packages (none today), category pages (phase 2) | static pages: `STATIC_PAGES_LASTMOD` constant in `lib/sitemaps.ts` (release date; bumped by hand when static content changes); hub: `destination.updatedAt`; `/ideas`: newest indexable post's `updatedAt` or the constant |
| activities | indexable activities of indexable destinations: EN `activityPath(dest, slug)`, DE with `slugDe ?? slug` | `activity.updatedAt` |
| guides | tips section — empty until 20.11 | — |
| articles | indexable posts: `/ideas/{slug}`, `/de/ratgeber/{slugDe ?? slug}` | `post.updatedAt ?? post.date` |
| legacy-prague (EN path only, not in the index) | every `old_url_en` and `old_url_de` of the map's `301` rows, absolute on `SITE_URL` (150 URLs); remove on 2026-11-24 | the constant |

Legal pages (`/terms`, `/privacy-policy`, `/cookie-policy`, `/refund-policy`) leave the sitemap:
Т-10 lists only content-plan pages, about and contact. They stay indexable and footer-linked.
Expected size on release day with the kept post noindex and no category pages yet: pages 5 + 
activities 72 per locale → 154 URLs (Т-10's 174 includes the 20 category pages).

`/api/sitemap.xml` → backend `SitemapController` becomes a 410 stub on the same `/sitemap.xml`
mapping (prod's `/api` context path and Next's `/api/:path*` rewrite are what make it
`/api/sitemap.xml`; `SecurityConfig` keeps `permitAll`), `Cache-Control: public, max-age=86400`
(a cached 410 is the intended permanent answer). Generation code and its two integration tests
go; one test asserts 410.

Local verification: with the dev-seed change in §2 (Prague destination indexable) the local
`/sitemap.xml` lists the Prague hub and activities, so `smoke.mjs` can assert on them without
prod data. The hub's DE alternate is `/de/jga-prag` (via `localizePath`).

### 6. robots.txt

`AI_TRAINING_BOTS` → `['Bytespider']`; comment explains the 2026-10-18 decision (SEO manager:
AI assistants should be able to read and cite the site; Google-Extended, GPTBot, ClaudeBot,
CCBot, Applebot-Extended, Meta-ExternalAgent, anthropic-ai opened). Output equals the SEO
manager's file: `*` disallows `/admin`, `/vote`, `/payment` (+ `/de/` variants), `Bytespider`
disallow `/`, `Sitemap: https://www.trivlu.com/sitemap.xml`. Cloudflare's "Block AI bots"
switch is a dashboard action for the owner (deploy checklist).

### 7. Acceptance tooling

- `myhive-next/scripts/check-redirects.mjs`: rebuilt around the CSV: for each `301` row, EN,
  DE and `/en` old → single 308 → 200 at the new address; `410` rows → 410 on both locales;
  `landing` → 200 no hops; unmapped Prague activities and packages (from the API) → single 308
  via the patterns; the 47 guard rules → single 308; `?utm_source=test` survives on the hub.
  Catalog section (via the API): 72 Prague activities have `slugDe` and `primaryCategory`,
  per-category counts match the ТЗ, 10 have neither. Option `--legacy-sitemap <file>`: every
  `<loc>` path of the saved backend sitemap → Prague paths end in one 308 at the final address,
  other paths answer 200 (or 404 for records that no longer exist, reported as warnings). When
  the base is `https://www.trivlu.com`, three extra checks on `https://trivlu.com/...` and
  `http://www.trivlu.com/...` allow exactly two hops (Cloudflare + ours). Fails on zero
  coverage, prints `checks: N errors: N`, throttles to avoid Cloudflare rules.
- `scripts/smoke.mjs`: `/sitemap.xml` is a `<sitemapindex>` with ≥ 2 `<sitemap>`;
  `/sitemaps/pages.xml` and `/de/sitemaps/pages.xml` ≥ 5 `<url>` each with three hreflang links;
  `/sitemaps/legacy-prague.xml` 150 `<url>`; `/api/sitemap.xml` 410; robots has `Bytespider`
  and no `GPTBot`; DE activity page hreflang en/de point at the pair.
- Backend tests (required by CLAUDE.md): DE-first lookup + fallback, `slugDe` validation and
  uniqueness, `primaryCategoryId` write/clear/404, `updatedAt` stamped on insert and update,
  DTO mapping incl. localized primary category name, `/primary-categories` ordering, sitemap 410,
  admin create with primary category. `node --test myhive-backend/scripts` for the generator.
- Vitest: redirect rules (count/order/dupes/spots), sitemap builders (empty map excluded from
  the index, x-default, lastmod, legacy list = 150, route name validation), `pageMetadata` with
  per-locale paths, gone/matcher unchanged.
- CRA Jest: `entitySlug`, `LanguageSwitcher` with and without alternates, `AdminActivities`
  new fields and the required primary category on create, `BlogPage` H1.
- Local E2E as in phase 1 (backend 8081 + Next with `BACKEND_URL`/`INTERNAL_API_TOKEN`), then
  `check-redirects.mjs` and `smoke.mjs`; `next build`, `tsc`, all three test suites green.

### 8. Deploy order (replaces phase 1's)

1. Before merging: set `STATIC_PAGES_LASTMOD` in `myhive-next/lib/sitemaps.ts` to the release date.
2. Pause the FRONTEND auto-deploy on Render; merge. Backend deploys first: Flyway V15 runs
   (columns, table, seed, `updated_at = now()`).
3. Run `myhive-backend/prod-migration-seo-prague-urls.sql` on the prod database (psql / Render
   shell). Window: old frontend keeps working (its links now carry the new slugs under the old
   scheme, which it resolves), only bookmarked old-slug URLs 404 for a few minutes.
4. Deploy the frontend. Purge the Cloudflare cache. Turn off Cloudflare "Block AI bots".
5. `node myhive-next/scripts/check-redirects.mjs https://www.trivlu.com --legacy-sitemap
   docs/seo/legacy-api-sitemap-2026-10-18.xml`; `node myhive-next/scripts/smoke.mjs https://www.trivlu.com`;
   open the hub and two activities in both languages; flip the switcher on five activities.
6. SEO manager: GSC domain property + `/de/` property, submit `/sitemap.xml` and
   `/sitemaps/legacy-prague.xml`, remove `/api/sitemap.xml`, Bing, annotation in GA4.
7. 2026-11-24: delete `legacy-prague.xml` (code + GSC).

If single addresses are wrong: fix the rule, redeploy, never roll the move back (Google saw
308s). If the site 5xx's: roll back both services and move to the next Tuesday.

## What is left to the builder

- Exact file layout of `lib/sitemaps.ts`, XML escaping, how the three handlers share one
  builder; the `fs` read in `lib/redirect-map.ts` must work under `next build` and in vitest.
- The `PageAlternatesContext` plumbing through `PublicChrome` → `LegacyChrome` (prop) and the
  five page moves; keep `RouteMatch` patterns and `revalidate` as they are.
- Validation plumbing for `slugDe` (service-level `BadRequestException` with a field error vs
  Bean Validation) — match how `slug` errors surface in the admin today.
- Whether `PrimaryCategoryDTO` lives in `dto/` as a record or a Lombok class (follow neighbours).
- Jest/Vitest fixtures; TestDataFactory additions (`primaryCategory()`).

## Verification before "done"

All of §7 run and quoted in the change doc `docs/changes/2026-10-18-seo-prague-urls-phase1b.md`
(deviations from both ТЗ, gotchas, the exact SQL run order, counts). README/CLAUDE.md updates
follow owner approval per the workflow rule.
