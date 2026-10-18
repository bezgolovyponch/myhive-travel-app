# SEO Prague URLs phase 1b Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prague moves once: final EN and DE slugs from the ТЗ map, a primary category per activity, map-driven 308s, Т-10 sitemaps with `lastmod`, 410 for the old backend sitemap, open robots.txt.

**Architecture:** One committed CSV (`myhive-next/seo/redirect-map.csv`) feeds a backend SQL generator (data), Next `redirects()` (rules) and the legacy sitemap. The backend grows `slug_de` + `primary_category_id` + `updated_at` with a DE-first slug lookup; Next resolves a page's EN/DE path pair once and hands it to metadata, sitemaps and the CRA language switcher through a context the chrome provides.

**Tech Stack:** Spring Boot 4 / Java 25 / Flyway (prod only) / H2 dev; Next 15 App Router + next-intl 4; React 19 CRA (source of UI, copied to `myhive-next/legacy-src` by `npm run sync-legacy`); vitest, Jest (CRA), JUnit 5, `node --test`.

**Spec:** `docs/superpowers/specs/2026-10-18-seo-prague-urls-phase1b-design.md` (read it first; phase 1 context in `docs/superpowers/specs/2026-10-15-seo-prague-urls-phase1-design.md` and `docs/changes/2026-10-15-seo-prague-urls-phase1.md`).

## Global Constraints

- Branch `feat/seo-prague-urls`; never `git add -A` / `git add .` (untracked research folders must stay out); commit messages end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Edit UI only in `myhive-react-app/src`; never touch `myhive-next/legacy-src` (generated; run `cd myhive-next && npm run sync-legacy` after CRA edits before Next tests/build).
- Repo files are CRLF; on this machine Bash heredocs collapse backslashes: write regexes and file content with the Edit/Write tools.
- Java: Google style, no wildcard imports, `@Override`, braces always, one declaration per line; tests use `expected`-prefixed variables; backend unit tests are required for every change.
- Backend dev seed: `application-dev.properties` loads `data.sql` then `data-prague.sql`; tests use H2 `create-drop`, no seed, Flyway off. Prod validates the schema: every new column/table needs the Flyway migration in Task 1.
- Steam may hold port 8080 and the owner's own servers may hold 8081/3000: pick free ports (`--server.port=8082`, Next `-p 3002`) for local runs; Next needs `BACKEND_URL` and `INTERNAL_API_TOKEN` matching the backend's `--internal.api.token`.
- Exact values: ten primary categories (table in spec §1), `STATIC_PAGES_LASTMOD` placeholder `2026-10-21`, `/ideas` H1 EN "Stag Do Tips, Ideas and Planning Advice for the Best Man", DE "JGA-Ratgeber: Tipps, Ideen und Planung für den Junggesellenabschied", redirect rule total 325, legacy sitemap 150 URLs, robots blocks only `Bytespider`.

## Review Focus

1. `/de/jga-prag/aktivitaeten/paintball` where the DE slug equals the EN slug (13 rows): must serve 200, never redirect to itself (Task 8 test `activityPage_sameSlugBothLocales_noRedirect`).
2. `redirect-map.csv` read on Windows carries `\r` at line ends: the parser must trim it or every rule gets a stray carriage return (Task 5 test `parseRedirectMap_acceptsCrlf`).
3. Admin saves an activity with `slugDe` equal to its own current value: must not be rejected as a duplicate (Task 2 test `updateActivity_keepsOwnSlugDeWithoutDuplicateError`).
4. The five `410` rows have empty `new_url` cells: they must produce no redirect rule and no legacy-sitemap entry (Task 5 test `legacyUrlRedirects_skips410Rows`, Task 6 test `legacyPragueEntries_excludes410Rows`).
5. A record whose `updatedAt` is null (API older than the backend deploy, or a row never touched): the sitemap must fall back to `STATIC_PAGES_LASTMOD`, never emit an empty `<lastmod>` (Task 6 test `buildSitemapFrom_nullUpdatedAtUsesStaticLastmod`).

---

### Task 1: Backend schema — `PrimaryCategory`, `slug_de`, `updated_at`, Flyway V15, dev seed

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/entity/PrimaryCategory.java`
- Create: `myhive-backend/src/main/java/com/myhive/backend/repository/PrimaryCategoryRepository.java`
- Create: `myhive-backend/src/main/resources/db/migration/V15__seo_slugs_primary_category_updated_at.sql`
- Modify: `entity/Activity.java`, `entity/BlogPost.java`, `entity/Destination.java`, `entity/Package.java`, `entity/Category.java` (same package)
- Modify: `repository/ActivityRepository.java`, `repository/BlogPostRepository.java`
- Modify: `myhive-backend/src/main/resources/data.sql` (seed the ten rows after the categories insert)
- Modify: `myhive-backend/src/test/java/com/myhive/backend/TestDataFactory.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/repository/PrimaryCategoryRepositoryTest.java` (follow the style of the existing repository tests in that package)

**Interfaces:**
- Produces: `PrimaryCategory { UUID id; String slug; String slugDe; String name; Map<String,Map<String,String>> translations; int sortOrder; LocalDateTime createdAt; LocalDateTime updatedAt }` (Lombok like `Category`, table `primary_categories`, column `sort_order`, `translations` via `TranslationsConverter`, `createdAt` `@CreationTimestamp`, `updatedAt` `@UpdateTimestamp`).
- `PrimaryCategoryRepository extends JpaRepository<PrimaryCategory, UUID>`: `List<PrimaryCategory> findAllByOrderBySortOrderAsc()`, `Optional<PrimaryCategory> findBySlug(String slug)`.
- `Activity`: `@Column(name = "slug_de", unique = true, length = 300) String slugDe`; `@ManyToOne(fetch = LAZY) @JoinColumn(name = "primary_category_id") PrimaryCategory primaryCategory`; `@UpdateTimestamp @Column(name = "updated_at") LocalDateTime updatedAt`.
- `BlogPost`: `slugDe` (same column spec) + `updatedAt`. `Destination`, `Package`, `Category`: `updatedAt`.
- `ActivityRepository`: `Optional<Activity> findBySlugDe(String slugDe)`, `boolean existsBySlugDe(String slugDe)`. `BlogPostRepository`: the same two.
- `TestDataFactory.primaryCategory(String slug)` → unsaved `PrimaryCategory` with `slugDe = slug + "-de"`, `name = slug`, `sortOrder = 1`.

- [ ] **Step 1: Write the failing repository test**

```java
@Test
void findBySlugDe_returnsActivityWithGermanSlug() {
  Destination destination = destinationRepository.save(TestDataFactory.destination());
  Activity activity = TestDataFactory.activity(destination);
  String expectedSlugDe = "axtwerfen";
  activity.setSlugDe(expectedSlugDe);
  activityRepository.save(activity);
  assertThat(activityRepository.findBySlugDe(expectedSlugDe)).get().extracting(Activity::getId).isEqualTo(activity.getId());
  assertThat(activityRepository.existsBySlugDe("missing")).isFalse();
}

@Test
void save_stampsUpdatedAtAndMovesItOnUpdate() { /* save activity → updatedAt not null; set name, flush, save again → updatedAt after the first */ }

@Test
void primaryCategories_orderBySortOrder() { /* save sortOrder 2 then 1 → findAllByOrderBySortOrderAsc returns 1 then 2; findBySlug works */ }
```

- [ ] **Step 2: Run to verify failure**

Run: `cd myhive-backend && ./gradlew test --tests '*PrimaryCategoryRepositoryTest'`
Expected: compilation failure (missing entity / methods).

- [ ] **Step 3: Add the entity, repository, columns and factory method** per Interfaces. `PrimaryCategory` does not implement `Slugged` (no `SlugAssigner` use).

- [ ] **Step 4: Write `V15__seo_slugs_primary_category_updated_at.sql`** (Postgres, idempotent):

```sql
ALTER TABLE activities ADD COLUMN IF NOT EXISTS slug_de VARCHAR(300);
ALTER TABLE blog_posts ADD COLUMN IF NOT EXISTS slug_de VARCHAR(300);
CREATE UNIQUE INDEX IF NOT EXISTS ux_activities_slug_de ON activities (slug_de);
CREATE UNIQUE INDEX IF NOT EXISTS ux_blog_posts_slug_de ON blog_posts (slug_de);
CREATE TABLE IF NOT EXISTS primary_categories (
  id UUID PRIMARY KEY, slug VARCHAR(120) NOT NULL UNIQUE, slug_de VARCHAR(120) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL, translations TEXT, sort_order INTEGER NOT NULL,
  created_at TIMESTAMP, updated_at TIMESTAMP);
ALTER TABLE activities ADD COLUMN IF NOT EXISTS primary_category_id UUID;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_activities_primary_category') THEN
  ALTER TABLE activities ADD CONSTRAINT fk_activities_primary_category FOREIGN KEY (primary_category_id) REFERENCES primary_categories (id); END IF; END $$;
-- updated_at on activities, blog_posts, destinations, packages, categories (ADD COLUMN IF NOT EXISTS ... TIMESTAMP)
-- ten INSERT ... SELECT ... WHERE NOT EXISTS (SELECT 1 FROM primary_categories WHERE slug = '<slug>') rows,
--   ids 92222222-0000-0000-0000-000000000001 .. 10, values from spec §1 table, translations '{"de":{"name":"<de.name>"}}', created_at/updated_at now()
-- UPDATE <each of the six tables> SET updated_at = now() WHERE updated_at IS NULL;
```

- [ ] **Step 5: Seed `data.sql`** with the same ten rows (`INSERT INTO primary_categories (id, slug, slug_de, name, translations, sort_order, created_at) VALUES ...`), placed after the categories insert.

- [ ] **Step 6: Run the test and the whole backend suite**

Run: `./gradlew test --tests '*PrimaryCategoryRepositoryTest'` then `./gradlew test`
Expected: PASS; full suite green (was 1435 tests).

- [ ] **Step 7: Boot the dev profile once to prove the seed loads**

Run: `./gradlew bootRun --args='--spring.profiles.active=dev --server.port=8082'` (stop after startup), then `curl -s localhost:8082/activities/slug/axe-throwing | head -c 200`
Expected: startup without SQL errors; the activity JSON (fields from later tasks absent for now).

- [ ] **Step 8: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/entity myhive-backend/src/main/java/com/myhive/backend/repository myhive-backend/src/main/resources/db/migration/V15__seo_slugs_primary_category_updated_at.sql myhive-backend/src/main/resources/data.sql myhive-backend/src/test/java/com/myhive/backend/TestDataFactory.java myhive-backend/src/test/java/com/myhive/backend/repository/PrimaryCategoryRepositoryTest.java
git commit -m "feat(backend): primary_categories, slug_de and updated_at columns with Flyway V15 and dev seed"
```

---

### Task 2: Backend services and DTOs — DE-first slug lookup, `slugDe` writes, primary category, `/primary-categories`

**Files:**
- Create: `dto/PrimaryCategoryDTO.java`, `service/PrimaryCategoryService.java`, `controller/PrimaryCategoryController.java`, `util/LocalizedSlugs.java` (package `com.myhive.backend.*`)
- Modify: `dto/ActivityDTO.java`, `dto/BlogPostDTO.java`, `dto/DestinationDTO.java`, `dto/PackageDTO.java`
- Modify: `service/ActivityService.java` (`getActivityBySlug`, `createActivity`, `updateActivity`, `applyDtoToEntity`, `convertToDTO`), `service/BlogPostService.java` (same shape), `service/DestinationService.java` + `service/PackageService.java` (`updatedAt` in the DTO mapping)
- Modify: `config/SecurityConfig.java` (permit `GET /primary-categories` exactly like `/categories`)
- Test: `service/ActivityServiceTest.java`, `service/BlogPostServiceTest.java` (existing classes), create `service/PrimaryCategoryServiceTest.java`, `util/LocalizedSlugsTest.java`; extend `controller/PublicControllerIntegrationTest.java`

**Interfaces:**
- `LocalizedSlugs.normalize(String raw)`: trim + lower-case; null/blank → `null`; otherwise must match `^[a-z0-9-]+$` else `throw new BadRequestException("slugDe: use lowercase letters, digits and hyphens")`.
- `PrimaryCategoryDTO { UUID id; String slug; String slugDe; String name }` (Lombok `@Data @NoArgsConstructor @AllArgsConstructor`).
- `PrimaryCategoryService.getAll(String locale) → List<PrimaryCategoryDTO>` ordered by `sortOrder`; `public static PrimaryCategoryDTO toDTO(PrimaryCategory pc, String lc)` with `name = Translations.pick(pc.getTranslations(), lc, "name", pc.getName())`.
- `GET /primary-categories?locale=` → `List<PrimaryCategoryDTO>`.
- `ActivityDTO` new fields: `String slugDe` (`@Size(max = 280)`), `UUID primaryCategoryId`, `PrimaryCategoryDTO primaryCategory`, `LocalDateTime updatedAt`. `BlogPostDTO`: `slugDe`, `updatedAt`. `DestinationDTO`, `PackageDTO`: `updatedAt`.
- Write semantics (activities and posts): `slugDe == null` → unchanged; `""`/blank → cleared; otherwise `LocalizedSlugs.normalize` + uniqueness via `existsBySlugDe` / `findBySlugDe` excluding self → `BadRequestException("slugDe: already in use")`. `primaryCategoryId == null` → unchanged; unknown → `ResourceNotFoundException("PrimaryCategory", id)`.
- Read: `getActivityBySlug(slug, locale)`: `lc = Translations.normalize(locale)`; if `lc != null` try `findBySlugDe(slug)` first, then `findBySlug(slug)`; `lc == null` → `findBySlug` only. `BlogPostService.getBlogPostBySlug` identical. `convertToDTO` sets `slugDe`, `updatedAt`, `primaryCategoryId`, `primaryCategory` (localized name); `slug` stays the EN slug in every locale.

- [ ] **Step 1: Write the failing tests** (names and assertions; use `expected*` variables):

`LocalizedSlugsTest`: `normalize_trimsAndLowerCases` (`"  Axtwerfen "` → `"axtwerfen"`), `normalize_blankIsNull`, `normalize_rejectsUmlautsAndSpaces` (expects `BadRequestException`).

`ActivityServiceTest`: `getActivityBySlug_germanLocalePrefersGermanSlug`, `getActivityBySlug_germanLocaleFallsBackToEnglishSlug`, `getActivityBySlug_englishLocaleIgnoresGermanSlug` (DE slug lookup with locale `en` → `ResourceNotFoundException`), `createActivity_storesNormalizedSlugDeAndPrimaryCategory`, `createActivity_rejectsDuplicateSlugDe`, `updateActivity_keepsOwnSlugDeWithoutDuplicateError`, `updateActivity_blankSlugDeClearsIt`, `updateActivity_nullPrimaryCategoryIdLeavesCategoryUnchanged`, `updateActivity_unknownPrimaryCategoryIs404`, `convertToDTO_localizesPrimaryCategoryNameAndKeepsEnglishSlug` (locale `de`: `primaryCategory.name == "Schießen"`, `slug == "ak47-shooting"`, `slugDe == "kalaschnikow-schiessen"`).

`BlogPostServiceTest`: `getBlogPostBySlug_germanLocalePrefersGermanSlug`, `getBlogPostBySlug_germanLocaleFallsBackToEnglishSlug`, `updateBlogPost_storesSlugDe`.

`PrimaryCategoryServiceTest`: `getAll_ordersBySortOrderAndLocalizesName`.

`PublicControllerIntegrationTest`: `primaryCategories_arePublicAndOrdered` (`GET /primary-categories?locale=de` → 200, first `slug` by sort order, `name` localized).

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests '*ActivityServiceTest' --tests '*BlogPostServiceTest' --tests '*PrimaryCategoryServiceTest' --tests '*LocalizedSlugsTest' --tests '*PublicControllerIntegrationTest'`
Expected: compilation failures / assertion failures.

- [ ] **Step 3: Implement** per Interfaces. Keep `SlugAssigner` for the EN slug untouched; resolve `slugDe` right after it in both create and update; put the uniqueness check in a private `resolveSlugDe(String requested, Activity self)` (and the BlogPost twin). Mirror the `/categories` `permitAll` matcher for `/primary-categories`.

- [ ] **Step 4: Run the targeted tests, then the full suite**

Run: same command as Step 2, then `./gradlew test`
Expected: PASS; suite green.

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto myhive-backend/src/main/java/com/myhive/backend/service myhive-backend/src/main/java/com/myhive/backend/controller/PrimaryCategoryController.java myhive-backend/src/main/java/com/myhive/backend/util/LocalizedSlugs.java myhive-backend/src/main/java/com/myhive/backend/config/SecurityConfig.java myhive-backend/src/test/java/com/myhive/backend/service myhive-backend/src/test/java/com/myhive/backend/util/LocalizedSlugsTest.java myhive-backend/src/test/java/com/myhive/backend/controller/PublicControllerIntegrationTest.java
git commit -m "feat(backend): German slugs resolved DE-first, primary category on activities, /primary-categories"
```

---

### Task 3: Backend `/sitemap.xml` answers 410

**Files:**
- Modify: `controller/SitemapController.java` (strip to the stub), `src/test/java/com/myhive/backend/controller/PublicControllerIntegrationTest.java` (replace the two sitemap tests)

**Interfaces:**
- `GET /sitemap.xml` → `410 Gone`, body `Gone`, `Content-Type: text/plain`, `Cache-Control: public, max-age=86400`. Mapping and `SecurityConfig` `permitAll` unchanged. The class keeps its name; a header comment says why (ТЗ Т-10, superseded by Next's `/sitemap.xml`, copy in `docs/seo/legacy-api-sitemap-2026-10-18.xml`).

- [ ] **Step 1: Replace `sitemap_returnsValidXml` and `sitemapIncludesPackageUrls` with `sitemap_isGoneWithLongCache`** asserting status 410 and the `Cache-Control` header.
- [ ] **Step 2: Run** `./gradlew test --tests '*PublicControllerIntegrationTest'` → Expected: FAIL (200).
- [ ] **Step 3: Rewrite `SitemapController`**: delete the service fields, `frontendUrl`, `appendUrl`, `escapeXml`; keep one method returning the stub response.
- [ ] **Step 4: Run** the test class and `./gradlew build` → Expected: PASS, build green (unused-import checks included).
- [ ] **Step 5: Commit** `git add myhive-backend/src/main/java/com/myhive/backend/controller/SitemapController.java myhive-backend/src/test/java/com/myhive/backend/controller/PublicControllerIntegrationTest.java && git commit -m "feat(backend): /sitemap.xml answers 410 (superseded by Next sitemaps, T-10)"`

---

### Task 4: Map → SQL generator, prod data script, dev seed block, translation re-key

**Files:**
- Create: `myhive-backend/scripts/seo-map-to-sql.mjs`, `myhive-backend/scripts/seo-map-to-sql.test.mjs`
- Create (generated): `myhive-backend/prod-migration-seo-prague-urls.sql`
- Modify (generated block): `myhive-backend/src/main/resources/data-prague.sql`
- Modify (re-keyed): `myhive-backend/content/translations-de/activities-prague-{1,2,3}.json`, `common.json` (blog section), then regenerate `myhive-backend/prod-migration-translations-de.sql` with the existing `node scripts/translations-to-sql.mjs de`
- Input: `myhive-next/seo/redirect-map.csv` (committed in `1c9d202d`)

**Interfaces (exports of `seo-map-to-sql.mjs`, pure, no deps):**
- `parseMap(csvText) → Row[]` where `Row = { type, oldEn, newEn, oldDe, newDe, categoryEn, categoryDe, note }`; throws on a `"` in any line, on a field count other than 8, trims `\r`.
- `validateRows(rows)`: throws unless 72 `activity` rows, no duplicate `newEn`/`newDe` slugs, no `newDe` equal to a different row's `newEn`, every `categoryEn` in the ten slugs, per-category counts exactly `{shooting:5, driving:7, action:11, beer:10, food:9, boats:3, nightlife:8, strippers:5, pranks:11, transfers:3}`, and no activity `oldEn` slug equal to any row's `newEn` or `newDe` slug (a guard rule would otherwise redirect a live page).
- `slugOf(url) → string` (last path segment).
- `activityUpdate(row) → string` (the exact statement from spec §2, with `updated_at = CURRENT_TIMESTAMP`), `blogUpdates() → string` (kept post rename + DELETE of the five retired slugs listed in `myhive-next/lib/gone.ts`), `prodSql(rows) → string` (`BEGIN;` … `COMMIT;`), `devBlock(rows) → string` (same statements without the transaction, plus `UPDATE destinations SET seo_indexable = TRUE WHERE slug = 'prague';`, wrapped in `-- BEGIN seo-map (generated by scripts/seo-map-to-sql.mjs)` / `-- END seo-map`), `rekey(jsonObject, renames: Map<old,new>) → jsonObject` (renames keys under `activities` and `blog`).
- CLI: `node scripts/seo-map-to-sql.mjs` (from `myhive-backend`) writes the prod file, replaces the marked block in `data-prague.sql` (appends it if absent), re-keys the four JSON files in place; `--check` only validates and prints counts.

- [ ] **Step 1: Write the failing `node --test` file** with: `parseMap_readsCrlfAndRejectsQuotes`, `validateRows_acceptsTheCommittedMap` (reads the real CSV), `validateRows_rejectsDuplicateNewSlug`, `activityUpdate_isIdempotentOnOldOrNewSlug` (statement contains `slug IN ('ak-47-glock-17-shooting', 'ak47-shooting')`, `slug_de = 'kalaschnikow-schiessen'`, `WHERE slug = 'shooting'`, `updated_at = CURRENT_TIMESTAMP`), `prodSql_wrapsInTransactionAndDeletesRetiredPosts`, `rekey_renamesOnlyMappedKeys`.
- [ ] **Step 2: Run** `node --test myhive-backend/scripts/` → Expected: FAIL (module missing).
- [ ] **Step 3: Implement the module and CLI.**
- [ ] **Step 4: Run the tests, then the CLI, then the translations generator**

Run: `node --test myhive-backend/scripts/` → PASS. Then `cd myhive-backend && node scripts/seo-map-to-sql.mjs && node scripts/translations-to-sql.mjs de`.
Expected: `prod-migration-seo-prague-urls.sql` has 72 activity UPDATEs + blog statements; `data-prague.sql` ends with the marked block; `git diff --stat` shows the three activity JSON files, `common.json`, and `prod-migration-translations-de.sql` whose `WHERE slug =` values now use the new slugs.

- [ ] **Step 5: Prove the dev seed applies**: start the dev backend (`--server.port=8082`), then `curl -s "localhost:8082/activities/slug/ak47-shooting"` → 200 with `"slugDe":"kalaschnikow-schiessen"` and `"primaryCategory":{"slug":"shooting"...}`; `curl -s "localhost:8082/activities/slug/kalaschnikow-schiessen?locale=de"` → 200; `curl -s -o /dev/null -w '%{http_code}' localhost:8082/blog/slug/how-to-plan-a-stag-do` → 200; `.../blog/slug/top-5-group-travel-destinations-for-2026` → 404. Stop the server.
- [ ] **Step 6: Commit**

```bash
git add myhive-backend/scripts/seo-map-to-sql.mjs myhive-backend/scripts/seo-map-to-sql.test.mjs myhive-backend/prod-migration-seo-prague-urls.sql myhive-backend/src/main/resources/data-prague.sql myhive-backend/content/translations-de myhive-backend/prod-migration-translations-de.sql
git commit -m "feat(backend): generate the Prague slug/category import from the redirect map; dev seed on final slugs"
```

---

### Task 5: Next — map parser and the 325 redirect rules

**Files:**
- Create: `myhive-next/lib/redirect-map.ts`, `myhive-next/lib/__tests__/redirect-map.test.ts`
- Modify: `myhive-next/lib/redirects.ts`, `myhive-next/lib/__tests__/redirects.test.ts` (rewrite), `myhive-next/next.config.ts` (unchanged call, verify)

**Interfaces:**
- `export interface MapRow { type: 'hub' | 'activity' | 'landing' | 'blog-index' | 'blog'; oldEn: string; newEn: string; oldDe: string; newDe: string; categoryEn: string; categoryDe: string; note: '301' | '410' | 'landing' }`
- `export function parseRedirectMap(csv: string): MapRow[]` (same rules as the backend parser: 8 fields, no `"`, trims `\r`, skips the header).
- `export function loadRedirectMap(): MapRow[]` reads `path.join(process.cwd(), 'seo', 'redirect-map.csv')` (cwd is `myhive-next` for `next build`, `next dev` and `vitest`).
- `export function slugOf(url: string): string`.
- `export function legacyUrlRedirects(rows: MapRow[] = loadRedirectMap()): RedirectRule[]` returning, in order: (1) for each `note === '301'` row: `oldEn → newEn`, `oldDe → newDe`, `/en${oldEn} → newEn`; (2) pattern fallbacks from `DESTINATION_HUBS`: `/destination/{hub}/activity/:slug → {hub}/activities/:slug` and `/destination/{hub}/package/:slug → {hub}/packages/:slug`, each for EN, DE (`localizePath`) and `/en` (6 rules); (3) guards for activity rows where `slugOf(oldEn) !== slugOf(newEn)`: `/prague-stag-do/activities/<oldEnSlug> → newEn` and `/de/jga-prag/aktivitaeten/<oldEnSlug> → newDe` (build both from `localizePath(activityPath('prague', oldSlug), locale)` so the hub table stays the single owner). All `permanent: true`.

- [ ] **Step 1: Write the failing tests**

`redirect-map.test.ts`: `parseRedirectMap_acceptsCrlf` (a 3-line CSV joined with `\r\n` → fields without `\r`), `parseRedirectMap_rejectsQuotedCell`, `loadRedirectMap_readsCommittedFile` (81 rows, 72 activities).

`redirects.test.ts`: `legacyUrlRedirects_has325RulesInGroupOrder` (first rule `/destination/prague → /prague-stag-do`; rule index 225 is the EN activity pattern; the last 94 are guards), `legacyUrlRedirects_noDuplicateSources`, `legacyUrlRedirects_spotRows` (`/de/destination/prague/activity/ak-47-glock-17-shooting → /de/jga-prag/aktivitaeten/kalaschnikow-schiessen`, `/en/blog → /ideas`, `/de/blog/how-to-plan-a-stress-free-group-trip → /de/ratgeber/jga-planen`), `legacyUrlRedirects_skips410Rows` (no source contains `top-5-group-travel-destinations-for-2026`), `legacyUrlRedirects_guardSourcesAreNeverLivePages` (no guard source equals any row's `newEn`/`newDe`), `legacyUrlRedirects_landingRowProducesNoRule`.

- [ ] **Step 2: Run** `cd myhive-next && npm run sync-legacy && npx vitest run lib/__tests__/redirect-map.test.ts lib/__tests__/redirects.test.ts` → Expected: FAIL.
- [ ] **Step 3: Implement** `redirect-map.ts` and rewrite `redirects.ts` (keep the file header comment; cite spec §3).
- [ ] **Step 4: Run** the two test files, then `npx vitest run`, then `npx tsc --noEmit` → Expected: all PASS.
- [ ] **Step 5: Commit** `git add myhive-next/lib/redirect-map.ts myhive-next/lib/redirects.ts myhive-next/lib/__tests__/redirect-map.test.ts myhive-next/lib/__tests__/redirects.test.ts && git commit -m "feat(next): 308 rules generated from the redirect map, with pattern fallbacks and stale-cart guards"`

---

### Task 6: Next — sitemap index, per-type/per-language maps, legacy map, API types

**Files:**
- Create: `myhive-next/lib/sitemaps.ts`, `myhive-next/lib/__tests__/sitemaps.test.ts`, `myhive-next/app/sitemap.xml/route.ts`, `myhive-next/app/sitemaps/[name]/route.ts`, `myhive-next/app/de/sitemaps/[name]/route.ts`
- Delete: `myhive-next/app/sitemap.ts`
- Modify: `myhive-next/lib/api.ts` (types only), `myhive-next/lib/seo.ts` (nothing yet; Task 7)

**Interfaces:**
- `lib/api.ts` types: `Activity.slugDe?: string | null`, `Activity.primaryCategory?: { id: string; slug: string; slugDe?: string | null; name: string } | null`, `Activity.updatedAt?: string | null`; `BlogPost.slugDe?`, `BlogPost.updatedAt?`; `Destination.updatedAt?`; `TripPackage.updatedAt?`.
- `lib/sitemaps.ts`:
  - `export const STATIC_PAGES_LASTMOD = '2026-10-21';` (set to the release date before merge — deploy checklist)
  - `export type SitemapName = 'pages' | 'activities' | 'guides' | 'articles'; export const SITEMAP_NAMES: readonly SitemapName[]`
  - `export interface SitemapEntry { loc: string; lastmod: string; alternates?: Record<string, string> }`
  - `export interface Catalog { destinations: Destination[]; activitiesByDestination: Record<string, Activity[]>; packagesByDestination: Record<string, TripPackage[]>; posts: BlogPost[] }`
  - `export async function loadCatalog(): Promise<Catalog>` (the `api.*` calls `app/sitemap.ts` made today, `seoIndexable` gates applied here incl. the destination parent rule; backend unreachable → empty catalog)
  - `export function buildSitemapFrom(catalog: Catalog, name: SitemapName, locale: string): SitemapEntry[]` per spec §5 table; `lastmod = dateOnly(x.updatedAt) ?? STATIC_PAGES_LASTMOD`; DE activity path uses `entitySlug`-equivalent `a.slugDe ?? a.slug`; alternates `{ en, de }` built with `canonical(path, locale)` from `lib/seo.ts`, x-default added by the renderer from `en`.
  - `export function legacyPragueEntries(rows: MapRow[]): SitemapEntry[]` (every `oldEn` and `oldDe` of `301` rows, `loc = SITE_URL + path`, `lastmod = STATIC_PAGES_LASTMOD`, no alternates).
  - `export function indexItems(catalog: Catalog): { loc: string; lastmod: string }[]` (non-empty maps × locales; `loc = ${SITE_URL}/sitemaps/<name>.xml` or `${SITE_URL}/de/sitemaps/<name>.xml`; `lastmod` = max of that map's entries).
  - `export function urlsetXml(entries: SitemapEntry[]): string` (`xmlns:xhtml`, `<xhtml:link rel="alternate" hreflang="en|de|x-default" href>` with x-default = `alternates.en`), `export function sitemapIndexXml(items): string`, `export function resolveSitemapName(file: string, locale: string): SitemapName | 'legacy-prague' | null` (`'<name>.xml'` → name; `legacy-prague.xml` only when `locale === 'en'`).
  - `export async function sitemapResponse(file: string, locale: string): Promise<Response>` → 404 for `null`, else `application/xml` body.
- Routes: `app/sitemap.xml/route.ts` `GET` → `sitemapIndexXml(indexItems(await loadCatalog()))`; `app/sitemaps/[name]/route.ts` `GET(_, { params })` → `sitemapResponse(name, 'en')`; `app/de/sitemaps/[name]/route.ts` → `sitemapResponse(name, 'de')`. Each exports `revalidate = 3600`.

- [ ] **Step 1: Write the failing tests** against a fixture catalog (one indexable Prague destination with `updatedAt '2026-10-20T10:00:00'`, three activities: one with `slugDe`, one without, one `seoIndexable: false`; one noindex post):

`buildSitemapFrom_pagesHasHomeAboutContactIdeasAndHub` (EN 5 entries; hub lastmod `2026-10-20`; `/de/jga-prag` in alternates), `buildSitemapFrom_activitiesUsesGermanSlugAndSkipsNoindex` (DE loc ends with the DE slug; the activity without `slugDe` uses the EN slug; 2 entries), `buildSitemapFrom_nullUpdatedAtUsesStaticLastmod`, `buildSitemapFrom_articlesEmptyWhenAllPostsNoindex`, `indexItems_excludesEmptyMaps` (4 items: pages+activities × 2 locales), `legacyPragueEntries_excludes410Rows` (150 entries from the committed map; none contains `top-5-`), `urlsetXml_rendersXDefaultFromEnglish`, `resolveSitemapName_legacyOnlyEnglish` (`'legacy-prague.xml','de'` → null; `'pages.xml','de'` → `'pages'`; `'evil.xml'` → null).

- [ ] **Step 2: Run** `npx vitest run lib/__tests__/sitemaps.test.ts` → FAIL.
- [ ] **Step 3: Implement** `lib/sitemaps.ts`, the three route files, delete `app/sitemap.ts`, extend the API types.
- [ ] **Step 4: Run** `npx vitest run && npx tsc --noEmit` → PASS.
- [ ] **Step 5: Commit** `git add myhive-next/lib/sitemaps.ts myhive-next/lib/__tests__/sitemaps.test.ts myhive-next/app/sitemap.xml myhive-next/app/sitemaps myhive-next/app/de myhive-next/lib/api.ts && git rm -q myhive-next/app/sitemap.ts && git commit -m "feat(next): sitemap index with per-type/per-language maps, lastmod and legacy-prague map (T-10)"`

---

### Task 7: CRA i18n — `entitySlug`, `PageAlternatesContext`, language switcher

**Files:**
- Modify: `myhive-react-app/src/i18n/routes.js` (`entitySlug`), `myhive-react-app/src/i18n/index.js` (context + hook + re-export), `myhive-react-app/src/components/LanguageSwitcher.js`
- Tests: `myhive-react-app/src/i18n/routes.test.js`, `myhive-react-app/src/components/LanguageSwitcher.test.js`

**Interfaces:**
- `routes.js`: `export function entitySlug(entity, locale = currentLocale())` → `locale !== DEFAULT_LOCALE && entity.slugDe ? entity.slugDe : entity.slug`.
- `i18n/index.js`: `export const PageAlternatesContext = createContext({})`; `export function usePageAlternates()`; re-export `entitySlug`.
- `LanguageSwitcher`: `const alternates = usePageAlternates(); href={alternates[l] ?? localizeHref(path, l)}` (an alternate is a locale-prefixed site path, used as-is, no query appended).

- [ ] **Step 1: Write the failing tests**

`routes.test.js`: `entitySlug_prefersGermanSlugForDe`, `entitySlug_fallsBackToEnglishSlug`, `entitySlug_englishIgnoresGermanSlug`.
`LanguageSwitcher.test.js`: `usesPageAlternatesWhenProvided` (render inside `PageAlternatesContext.Provider value={{de: '/de/jga-prag/aktivitaeten/axtwerfen'}}` at `/prague-stag-do/activities/axe-throwing` → the DE link href is the provided one), `fallsBackToSegmentTranslation` (existing behaviour kept).

- [ ] **Step 2: Run** `cd myhive-react-app && CI=true npx react-scripts test --watchAll=false src/i18n src/components/LanguageSwitcher.test.js` → FAIL.
- [ ] **Step 3: Implement** per Interfaces.
- [ ] **Step 4: Run** `CI=true npx react-scripts test --watchAll=false` → PASS (all CRA tests).
- [ ] **Step 5: Commit** `git add myhive-react-app/src/i18n myhive-react-app/src/components/LanguageSwitcher.js myhive-react-app/src/components/LanguageSwitcher.test.js && git commit -m "feat(cra): entitySlug picks the locale's slug; language switcher honours page alternates"`

---

### Task 8: Next — per-locale canonical/hreflang, wrong-slug 308, chrome with page alternates

**Files:**
- Modify: `myhive-next/lib/seo.ts`; create `myhive-next/lib/slug-redirect.ts`, `myhive-next/lib/__tests__/seo.test.ts`, `myhive-next/lib/__tests__/slug-redirect.test.ts`
- Modify: `myhive-next/components/site/catalog/ActivityDetail.tsx`, `PackageDetail.tsx`; `myhive-next/components/site/PublicChrome.tsx`, `LegacyChrome.tsx`
- Move (`git mv`, same URLs): `app/[locale]/(public)/prague-stag-do/activities` → `app/[locale]/prague-stag-do/activities`; `(public)/prague-stag-do/packages` → `app/[locale]/prague-stag-do/packages`; `(public)/destination/[slug]/activity` and `.../package` → `app/[locale]/destination/[slug]/activity|package`; `(public)/ideas/[slug]` → `app/[locale]/ideas/[slug]`
- Modify: the moved `ideas/[slug]/page.tsx`

**Interfaces:**
- Consumes: `entitySlug` and `PageAlternatesContext` from Task 7 (via `legacy-src/i18n`, run `npm run sync-legacy` first); `Activity.slugDe` / `BlogPost.slugDe` types from Task 6.
- `lib/seo.ts`: `PageMeta.path: string | Partial<Record<string, string>>`; `export function pathForLocale(path: PageMeta['path'], locale: string): string` (`string` → itself; object → `path[locale] ?? path[routing.defaultLocale]`); `pageMetadata` uses it for the canonical and for each `alternates.languages` entry, `x-default` from the default locale.
- `lib/slug-redirect.ts`: `export function wrongSlugTarget(requested: string, entity: { slug: string; slugDe?: string | null }, buildPath: (slug: string) => string, locale: string): string | null` → `null` when `entitySlug(entity, locale) === requested`, else `localizePath(buildPath(expectedSlug), locale)`.
- `ActivityDetail.tsx`: `export function activityPaths(destinationSlug: string, a: Activity): Record<string, string>` (`{ en: activityPath(dest, a.slug), de: activityPath(dest, a.slugDe ?? a.slug) }`, internal paths); `export function activityAlternates(destinationSlug, a): Record<string, string>` (`localizePath` of each); `activityMetadata` passes `path: activityPaths(...)`; `ActivityDetailPage` → after the city guard: `const target = wrongSlugTarget(activitySlug, activity, (s) => activityPath(destinationSlug, s), locale); if (target) permanentRedirect(target);` then returns `<PublicChrome locale={locale} alternates={activityAlternates(destinationSlug, activity)}>…existing JSX…</PublicChrome>`; the breadcrumb JSON-LD uses `entitySlug(activity, locale)`.
- `PackageDetail.tsx`: `PackageDetailPage` wraps its JSX in `<PublicChrome locale={locale}>` (no alternates).
- `ideas/[slug]/page.tsx`: same pattern with `blogPostPath(post.slug)` / `blogPostPath(post.slugDe ?? post.slug)`, `wrongSlugTarget`, and `<PublicChrome locale alternates>`.
- `PublicChrome` prop `alternates?: Record<string, string>` → `LegacyChrome alternates` → `<PageAlternatesContext.Provider value={alternates ?? EMPTY}>` around `<ChromeInner>` (module-level `EMPTY` constant so the context value is referentially stable).

- [ ] **Step 1: Write the failing tests**

`lib/__tests__/seo.test.ts`: `pageMetadata_perLocalePathsBuildCanonicalAndHreflang` (locale `de`, paths `{en: '/prague-stag-do/activities/axe-throwing', de: '/prague-stag-do/activities/axtwerfen'}` → canonical `https://www.trivlu.com/de/jga-prag/aktivitaeten/axtwerfen`, `languages.en` ends `/prague-stag-do/activities/axe-throwing`, `x-default` equals `languages.en`), `pageMetadata_stringPathUnchanged`.
`lib/__tests__/slug-redirect.test.ts`: `activityPage_sameSlugBothLocales_noRedirect` (`slugDe === slug`, locale `de` → null), `wrongSlugTarget_deRequestOnEnglishSlugRedirects` (→ `/de/jga-prag/aktivitaeten/<slugDe>`), `wrongSlugTarget_noGermanSlugServesEnglishSlug` (`slugDe` null, DE request on the EN slug → null), `wrongSlugTarget_enRequestMatchesEnglishSlug` (→ null).

- [ ] **Step 2: Run** `cd myhive-next && npm run sync-legacy && npx vitest run lib/__tests__/seo.test.ts lib/__tests__/slug-redirect.test.ts` → FAIL.
- [ ] **Step 3: Implement** `lib/seo.ts`, `lib/slug-redirect.ts`, the catalog modules, the chrome props, the five `git mv` moves and the page edits. Keep `RouteMatch` patterns and `revalidate` as they are.
- [ ] **Step 4: Run** `npx vitest run`, `npx tsc --noEmit` → PASS. Then `BACKEND_URL=http://localhost:9 npx next build` is NOT required here (Task 13 builds); instead `npx next lint` if configured, else skip.
- [ ] **Step 5: Commit** (list the moved paths explicitly) `git add myhive-next/lib/seo.ts myhive-next/lib/slug-redirect.ts myhive-next/lib/__tests__ myhive-next/components/site "myhive-next/app/[locale]" && git commit -m "feat(next): EN/DE slug pair drives canonical, hreflang, page alternates and wrong-slug 308s"`

---

### Task 9: CRA — links use `entitySlug`, `/ideas` H1, landing data on final slugs

**Files:**
- Modify: `myhive-react-app/src/components/ActivityCard.js:18`, `ActivityPreviewModal.js:71`, `TripBuilder.js:473`, `pages/PackageDetailPage.js:91`, `pages/vote/ActivityVotePage.js:218`, `pages/vote/CuratePage.js:114`, `pages/BlogPage.js:48`, `pages/BlogPostPage.js:45`, `pages/ActivityDetailPage.js:166`
- Modify: `myhive-react-app/src/i18n/messages/en.json`, `de.json` (`blog.listTitle`)
- Modify: `myhive-next/components/landing/data.ts`, `components/landing/ActivityRows.tsx:29`, test `components/landing/__tests__/data.test.ts`
- Tests: `myhive-react-app/src/components/ActivityCard.test.js`, `pages/BlogPage.test.js`

**Interfaces:**
- Every listed call site replaces `x.slug` with `entitySlug(x)` inside `activityPath(...)` / `blogPostPath(...)` (the canonical `<link>`s use `entitySlug(x, locale)` with the page's `locale`).
- `data.ts`: `LandingActivity.slugDe: string | null`; `toLandingActivity` copies `a.slugDe ?? null`; `export function activityLink(destinationSlug: string, activity: Pick<LandingActivity, 'slug' | 'slugDe'>, locale: string): string` → `activityPath(destinationSlug, entitySlug(activity, locale))`; `DECK_SLUGS` = `['ak47-shooting','tank-driving','beer-spa','go-karting','rafting','river-cruise','irish-pub-dinner','car-demolition']`; every curated-pool `slug` that equals an `old_url_en` slug of a renamed map row becomes its `new_url_en` slug (mechanical pass over the map; slugs the map does not know stay).
- `ActivityRows.tsx`: `activityLink(destinationSlug, a, locale)` where `locale` is what the component already uses for `lp`.

- [ ] **Step 1: Write the failing tests**: `ActivityCard.test.js` `linksToGermanSlugUnderDe` (render with locale `de` and `slugDe` → href `/de/jga-prag/aktivitaeten/<slugDe>`); `BlogPage.test.js` `rendersTheSeoH1` (EN string above); `data.test.ts` `deckAndCuratedSlugsAreNeverRenamedOldSlugs` (load the map via `parseRedirectMap(fs.readFileSync('seo/redirect-map.csv'))`; no DECK/curated slug equals a renamed row's old slug) and `activityLink_usesGermanSlugForDe`.
- [ ] **Step 2: Run** the three test files → FAIL.
- [ ] **Step 3: Implement** the call-site edits, the dictionary strings, `data.ts`/`ActivityRows.tsx` changes.
- [ ] **Step 4: Run** `CI=true npx react-scripts test --watchAll=false` (CRA, all), then `cd myhive-next && npm run sync-legacy && npx vitest run && npx tsc --noEmit` → PASS.
- [ ] **Step 5: Commit** `git add myhive-react-app/src myhive-next/components/landing && git commit -m "feat(ui): activity and post links use the locale's slug; /ideas H1 from SEO; landing on final slugs"`

---

### Task 10: Admin — German slug fields and the primary category select

**Files:**
- Modify: `myhive-react-app/src/services/adminApi.js`, `pages/AdminActivities.js`, `pages/AdminBlog.js`
- Tests: `services/adminApi.test.js`, `pages/AdminActivities.test.js`, `pages/AdminBlog.test.js` (create if absent, following `AdminActivities.test.js`)

**Interfaces:**
- `adminApi.getPrimaryCategories()` → `GET /primary-categories` (JWT header as every adminApi call).
- `AdminActivities`: `EMPTY_FORM` adds `slugDe: ''`, `primaryCategoryId: ''`; `mapItemToForm` adds `slugDe: a.slugDe || ''`, `primaryCategoryId: a.primaryCategoryId || ''`; `buildPayload` sends `slugDe: form.slugDe.trim()` (empty string clears) and `primaryCategoryId: form.primaryCategoryId || null`; `validate` adds `slugFormat(form.slugDe)` under key `slugDe` and, when the form has no `id` (create), `primaryCategoryId` required with message `Primary category is required`; a `Form.Select` "Primary category" with options from `getPrimaryCategories()` (label `name`, value `id`) placed next to the slug field; a text input "German slug (optional)" with placeholder `ae, oe, ue, ss — no umlauts`.
- `AdminBlog`: `slugDe` text input, same payload rule (`''` clears).

- [ ] **Step 1: Write the failing tests**: `adminApi.test.js` `getPrimaryCategories_hitsPublicEndpointWithToken`; `AdminActivities.test.js` `createRequiresPrimaryCategory` (submit a new activity without a selection → error text rendered, `createActivity` not called), `editSendsSlugDeAndPrimaryCategory` (payload contains both); `AdminBlog.test.js` `savesGermanSlug`.
- [ ] **Step 2: Run** the three files → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `CI=true npx react-scripts test --watchAll=false` → PASS.
- [ ] **Step 5: Commit** `git add myhive-react-app/src/services myhive-react-app/src/pages && git commit -m "feat(admin): German slug fields and a required primary category for new activities"`

---

### Task 11: robots.txt opens AI bots except Bytespider

**Files:**
- Modify: `myhive-next/app/robots.ts`; create `myhive-next/app/__tests__/robots.test.ts`

**Interfaces:** constant renamed `BLOCKED_BOTS = ['Bytespider']`; the comment block replaced by a short one citing the 2026-10-18 decision (SEO manager: assistants must be able to read and cite the site; training opt-outs dropped on purpose; Cloudflare "Block AI bots" switch is off per the deploy checklist). Output otherwise unchanged.

- [ ] **Step 1: Test** `robots_blocksOnlyBytespiderWhenIndexingAllowed` (`process.env.ALLOW_INDEXING = 'true'`; rules contain `{ userAgent: ['Bytespider'], disallow: '/' }`; no rule mentions `GPTBot`; `sitemap` ends with `/sitemap.xml`; `*` disallows the six service paths) and `robots_disallowsAllWithoutFlag`.
- [ ] **Step 2: Run** `npx vitest run app/__tests__/robots.test.ts` → FAIL.
- [ ] **Step 3: Implement.** - [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `git add myhive-next/app/robots.ts myhive-next/app/__tests__/robots.test.ts && git commit -m "feat(next): robots.txt blocks only Bytespider (owner decision 2026-10-18)"`

---

### Task 12: Acceptance scripts — `check-redirects.mjs` from the map, `smoke.mjs` for the new sitemaps

**Files:**
- Modify: `myhive-next/scripts/check-redirects.mjs` (rewrite), `myhive-next/scripts/smoke.mjs`

**Interfaces:**
- `node scripts/check-redirects.mjs <base> [--legacy-sitemap <file>] [--delay-ms <n>]`. Reads `seo/redirect-map.csv` with a copy of the parser logic (plain `.mjs`, cannot import TS). Checks: every `301` row EN/DE/`/en` old → single 308 → 200 at new; `410` rows → 410 both locales; `landing` → 200 no hops; hub `?utm_source=test&tab=trip-builder` keeps the query; unmapped Prague activities and all Prague packages (from `/api/destinations` + `/api/activities?destinationId=` + `/api/packages?destinationId=`, `X-Internal-Token` when set) → single 308 via the patterns; guard rules for renamed rows (both locales) → single 308; `/blog/not-a-post` 404, `/destination/not-a-city` 404; catalog section: 72 Prague activities with `slugDe` and `primaryCategory`, counts per `primaryCategory.slug` equal the ТЗ numbers, exactly 10 with neither. `--legacy-sitemap`: each `<loc>` path → Prague hub/activity/package paths: single 308 to the final address; other paths: 200 (404 → warning, not error). When `base` host is `www.trivlu.com`: `https://trivlu.com/destination/prague`, `http://www.trivlu.com/destination/prague`, `https://trivlu.com/blog` → ≤ 2 hops ending 200 at the mapped target. Output `checks: N errors: N warnings: N`, exit 1 on errors or zero checks; sleep `--delay-ms` (default 0 locally, 150 when the host is `www.trivlu.com`).
- `smoke.mjs`: `/sitemap.xml` must contain `<sitemapindex` and ≥ 2 `<sitemap>`; `/sitemaps/pages.xml` and `/de/sitemaps/pages.xml` ≥ 5 `<url>` and every `<url>` has three `hreflang` links; `/sitemaps/legacy-prague.xml` exactly 150 `<url>`; `/de/sitemaps/legacy-prague.xml` 404; `/api/sitemap.xml` 410; `/robots.txt` contains `Bytespider` and not `GPTBot`; keep the existing DE activity hreflang check but resolve the EN counterpart from the page's own `<link hreflang="en">` instead of assuming the same slug.

- [ ] **Step 1: Rewrite `check-redirects.mjs`** per Interfaces (keep `follow`, `report`, `expectSingle308`, `expectDirect`, `getJson` helpers; add `expectAtMostTwoHops`).
- [ ] **Step 2: Update `smoke.mjs`.**
- [ ] **Step 3: Dry-run both against a stub**: `node scripts/check-redirects.mjs http://localhost:9 2>&1 | tail -3` must print the abort with the URL and exit 1 (no silent zero-coverage).
- [ ] **Step 4: Commit** `git add myhive-next/scripts/check-redirects.mjs myhive-next/scripts/smoke.mjs && git commit -m "chore(next): redirect check driven by the map (+legacy sitemap mode); smoke covers the sitemap index"`

---

### Task 13: Local end-to-end run

**Files:** none new (fix whatever the run reveals; each fix is its own commit).

- [ ] **Step 1: Start the backend** `cd myhive-backend && ./gradlew bootRun --args='--spring.profiles.active=dev --server.port=8082 --internal.api.token=local'` (background; confirm `localhost:8082/primary-categories` returns 10 rows).
- [ ] **Step 2: Start Next** `cd myhive-next && BACKEND_URL=http://localhost:8082 INTERNAL_API_TOKEN=local npx next dev -p 3002` (PowerShell: `$env:BACKEND_URL='http://localhost:8082'; $env:INTERNAL_API_TOKEN='local'`).
- [ ] **Step 3: Run** `node scripts/check-redirects.mjs http://localhost:3002 --legacy-sitemap ../docs/seo/legacy-api-sitemap-2026-10-18.xml` → Expected: `errors: 0`, `checks` ≥ 600 (75×3 + 6 + 94 + guards + legacy 559 + catalog). Note: the legacy file lists prod-only records (10 packages exist in dev too? if not, they report as warnings, not errors).
- [ ] **Step 4: Run** `node scripts/smoke.mjs http://localhost:3002` → Expected: only the known pre-existing dev-data description-length failure, if any; sitemap/robots checks pass.
- [ ] **Step 5: Browser checks (Playwright MCP or curl)**: `/de/jga-prag/aktivitaeten/kalaschnikow-schiessen` 200 with German H1 and the switcher's EN link `/prague-stag-do/activities/ak47-shooting`; `/de/jga-prag/aktivitaeten/ak47-shooting` → 308 to the DE slug; `/de/jga-prag/aktivitaeten/paintball` 200 (same slug both locales); `/ideas` H1 is the SEO string; `/de/ratgeber/jga-planen` 200 + noindex; `/prague-stag-do?tab=trip-builder` still mounts the SPA with noindex; admin activity form shows the select (login needed: skip if no dev credentials, note it).
- [ ] **Step 6: `npx next build`** with the env vars → green; `npx tsc --noEmit` green; all three suites green. Stop both servers (only the ones this task started).
- [ ] **Step 7: Commit any fixes** with their own messages.

---

### Task 14: Change doc, phase-1 doc pointer, graph update

**Files:**
- Create: `docs/changes/2026-10-18-seo-prague-urls-phase1b.md`
- Modify: `docs/changes/2026-10-15-seo-prague-urls-phase1.md` (top note: deploy checklist superseded by phase 1b; items 1 and 3 no longer apply)

- [ ] **Step 1: Write the change doc**: what moved (counts), the three consumers of the map, deploy order from spec §8 verbatim with the exact SQL command (`psql "$DATABASE_URL" -f myhive-backend/prod-migration-seo-prague-urls.sql`), deviations from both ТЗ (lastmod moves on any save; legal pages out of the sitemap; packages in `pages.xml`; 154 not 174 URLs until category pages; primary category required only in the admin UI; the 10 unmapped activities stay noindex on EN slugs), gotchas met, verification output quoted from Task 13 (checks/errors, test counts), open items (phase 2 category pages, DE texts for 10 activities + packages, `STATIC_PAGES_LASTMOD` bump, remove `legacy-prague.xml` on 2026-11-24, CSV columns for `slug_de`/primary category).
- [ ] **Step 2: Run** `graphify update .` from the repo root (AST-only).
- [ ] **Step 3: Commit** `git add docs/changes/2026-10-18-seo-prague-urls-phase1b.md docs/changes/2026-10-15-seo-prague-urls-phase1.md && git commit -m "docs: phase 1b change log and deploy order"` (graphify output stays untracked).
