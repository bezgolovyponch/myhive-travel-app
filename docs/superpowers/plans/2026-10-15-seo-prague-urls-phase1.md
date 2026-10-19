# SEO Prague URLs, phase 1: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve the Prague catalog on `/prague-stag-do` (DE `/de/jga-prag`) and the articles on `/ideas` (DE `/de/ratgeber`), with every old address a single 308 and all internal links, canonical/hreflang, sitemap and email deep links on the new addresses.

**Architecture:** One table in `myhive-react-app/src/i18n/routes.js` (`DESTINATION_HUBS` + `LOCALIZED_PATHNAMES`, next-intl `pathnames` shape) owns the scheme. next-intl's middleware rewrites German public paths onto EN-named Next route folders; `localizePath`/`localizeHref` translate segments for links/canonical; CRA components build locale-segment paths with `routePath(...)` because the SPA's `BrowserRouter` only adds the `/de` basename. The backend mirrors the hub table in `PublicPaths.java` for emails.

**Tech Stack:** Next 15 App Router + next-intl 4 (`pathnames`, middleware), React 19 CRA + react-router 7, Spring Boot 4 / Java 25, Jest (CRA), vitest (Next), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-15-seo-prague-urls-phase1-design.md`

## Global Constraints

- Branch `feat/seo-prague-urls`, work in the main checkout. **Never `git add -A` / `git add .`**: the tree has untracked research folders that must not be committed. Add explicit paths only. If `git commit` fails on `.git/index.lock`, wait a second and retry (another task may be committing).
- Edit CRA UI only under `myhive-react-app/src/`; `myhive-next/legacy-src/` is a generated copy (gitignored). Run `cd myhive-next && npm run sync-legacy` after CRA edits before any Next build/test that depends on them.
- Google Java style per CLAUDE.md: no wildcard imports, braces always, `@Override`, one variable per declaration. Backend unit tests for every backend change.
- Old `/destination/{city}` routes stay for cities without a hub. Only `prague` has a hub in this phase.
- Slugs stay the EN slugs in both languages. No DB schema change in this phase.
- Commit messages end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Test style: `expected`-prefixed variables for values used in arrange and assert.

## Review Focus

1. `/de/prague-stag-do/...` (German prefix with English segments) must not 500: a redirect to `/de/jga-prag/...` or a 404 are both fine. Pinned by Task 6 (E2E curl, result recorded in the change doc).
2. Query strings on redirected URLs (`?tab=trip-builder&restore=TOKEN` from emails already sent, `?utm_source=`) must survive the 308. Pinned by Task 6 `check-redirects.mjs`.
3. A city without a hub (`/destination/krakow`) must not redirect and must not 500. Pinned by Task 6 `check-redirects.mjs`.
4. The language switcher on a German activity page must link to the English pair, never to `/jga-prag/...` without the prefix or to `/de/prague-stag-do/...`. Pinned by Task 1 `delocalizeSegments` tests and Task 3 switcher test.
5. `/blog/<unknown>` must be a 404 (not 410, not a redirect to `/ideas/<unknown>`). Pinned by Task 6 `check-redirects.mjs`.

---

### Task 1: URL scheme table and helpers in routes.js

**Files:**
- Modify: `myhive-react-app/src/i18n/routes.js`
- Create: `myhive-react-app/src/i18n/routes.test.js`
- Modify: `myhive-react-app/src/i18n/index.js:14` (re-export the new helpers)

**Interfaces:**
- Consumes: nothing new.
- Produces (all later tasks import these from `../i18n` in CRA or `../legacy-src/i18n/routes` in Next):
  - `DESTINATION_HUBS: Record<string,string>` — `{ prague: '/prague-stag-do' }`
  - `LOCALIZED_PATHNAMES: Record<string, Record<'en'|'de', string>>`
  - `destinationPath(destSlug) => string`, `activityPath(destSlug, slug) => string`, `packagePath(destSlug, slug) => string`, `blogIndexPath() => '/ideas'`, `blogPostPath(slug) => string` — EN-internal paths, no prefix.
  - `localizeSegments(path, locale) => string`, `delocalizeSegments(path, locale) => string`, `routePath(path, locale = currentLocale()) => string`
  - `localizePath(path, locale)` and `localizeHref(href, locale)` — existing names, now segment-aware.
  - `matchCatalogPath(pathname, locale = currentLocale()) => { destinationSlug: string, kind: 'hub'|'activity'|'package' } | null`

- [ ] **Step 1: Write the failing tests**

Create `myhive-react-app/src/i18n/routes.test.js`:

```js
import {
  DESTINATION_HUBS,
  LOCALIZED_PATHNAMES,
  activityPath,
  blogIndexPath,
  blogPostPath,
  delocalizeSegments,
  destinationPath,
  localizeHref,
  localizePath,
  localizeSegments,
  matchCatalogPath,
  packagePath,
  routePath,
} from './routes';

describe('path builders', () => {
  it('maps a hub destination to its keyword path and others to /destination', () => {
    expect(DESTINATION_HUBS.prague).toBe('/prague-stag-do');
    expect(destinationPath('prague')).toBe('/prague-stag-do');
    expect(destinationPath('krakow')).toBe('/destination/krakow');
    expect(activityPath('prague', 'beer-spa')).toBe('/prague-stag-do/activities/beer-spa');
    expect(activityPath('krakow', 'beer-spa')).toBe('/destination/krakow/activity/beer-spa');
    expect(packagePath('prague', 'weekend')).toBe('/prague-stag-do/packages/weekend');
    expect(packagePath('krakow', 'weekend')).toBe('/destination/krakow/package/weekend');
    expect(blogIndexPath()).toBe('/ideas');
    expect(blogPostPath('how-to')).toBe('/ideas/how-to');
  });

  it('declares every hub path in the localized table', () => {
    Object.values(DESTINATION_HUBS).forEach((hub) => {
      expect(LOCALIZED_PATHNAMES[hub]).toBeDefined();
      expect(LOCALIZED_PATHNAMES[`${hub}/activities/[slug]`]).toBeDefined();
      expect(LOCALIZED_PATHNAMES[`${hub}/packages/[slug]`]).toBeDefined();
    });
  });
});

describe('localizeSegments / delocalizeSegments', () => {
  it('is the identity for English', () => {
    expect(localizeSegments('/prague-stag-do/activities/beer-spa', 'en')).toBe('/prague-stag-do/activities/beer-spa');
    expect(localizeSegments('/about', 'en')).toBe('/about');
  });

  it('translates the hub, activity, package and ideas segments for German', () => {
    expect(localizeSegments('/prague-stag-do', 'de')).toBe('/jga-prag');
    expect(localizeSegments('/prague-stag-do/activities/beer-spa', 'de')).toBe('/jga-prag/aktivitaeten/beer-spa');
    expect(localizeSegments('/prague-stag-do/packages/weekend', 'de')).toBe('/jga-prag/pakete/weekend');
    expect(localizeSegments('/ideas', 'de')).toBe('/ratgeber');
    expect(localizeSegments('/ideas/how-to', 'de')).toBe('/ratgeber/how-to');
  });

  it('leaves unlisted and already-German paths alone (idempotent)', () => {
    expect(localizeSegments('/about', 'de')).toBe('/about');
    expect(localizeSegments('/destination/krakow/activity/x', 'de')).toBe('/destination/krakow/activity/x');
    expect(localizeSegments('/jga-prag/aktivitaeten/beer-spa', 'de')).toBe('/jga-prag/aktivitaeten/beer-spa');
    expect(localizeSegments('/prague-stag-do/packages', 'de')).toBe('/prague-stag-do/packages');
  });

  it('keeps query and hash and react-router params', () => {
    expect(localizeSegments('/prague-stag-do?tab=trip-builder&restore=T#x', 'de')).toBe('/jga-prag?tab=trip-builder&restore=T#x');
    expect(localizeSegments('/prague-stag-do/activities/:slug', 'de')).toBe('/jga-prag/aktivitaeten/:slug');
  });

  it('delocalizes back to the internal form', () => {
    expect(delocalizeSegments('/jga-prag', 'de')).toBe('/prague-stag-do');
    expect(delocalizeSegments('/jga-prag/aktivitaeten/beer-spa?x=1', 'de')).toBe('/prague-stag-do/activities/beer-spa?x=1');
    expect(delocalizeSegments('/ratgeber/how-to', 'de')).toBe('/ideas/how-to');
    expect(delocalizeSegments('/about', 'de')).toBe('/about');
    expect(delocalizeSegments('/prague-stag-do', 'en')).toBe('/prague-stag-do');
  });

  it('routePath defaults to the current locale (English in tests)', () => {
    expect(routePath('/prague-stag-do')).toBe('/prague-stag-do');
    expect(routePath('/prague-stag-do', 'de')).toBe('/jga-prag');
  });
});

describe('localizePath / localizeHref', () => {
  it('prefixes and translates for German, keeps English bare', () => {
    expect(localizePath('/prague-stag-do/activities/beer-spa', 'de')).toBe('/de/jga-prag/aktivitaeten/beer-spa');
    expect(localizePath('/prague-stag-do/activities/beer-spa', 'en')).toBe('/prague-stag-do/activities/beer-spa');
    expect(localizePath('/', 'de')).toBe('/de');
    expect(localizePath('/about', 'de')).toBe('/de/about');
    expect(localizePath('/prague-stag-do?tab=activities', 'de')).toBe('/de/jga-prag?tab=activities');
  });

  it('localizeHref keeps external and already-prefixed hrefs and translates the rest', () => {
    expect(localizeHref('https://x.test/prague-stag-do', 'de')).toBe('https://x.test/prague-stag-do');
    expect(localizeHref('/de/jga-prag', 'de')).toBe('/de/jga-prag');
    expect(localizeHref('/prague-stag-do?tab=trip-builder#top', 'de')).toBe('/de/jga-prag?tab=trip-builder#top');
    expect(localizeHref('/jga-prag', 'de')).toBe('/de/jga-prag');
  });
});

describe('matchCatalogPath', () => {
  it('recognizes hub, activity and package pages in both forms', () => {
    expect(matchCatalogPath('/prague-stag-do', 'en')).toEqual({ destinationSlug: 'prague', kind: 'hub' });
    expect(matchCatalogPath('/jga-prag?tab=activities', 'de')).toEqual({ destinationSlug: 'prague', kind: 'hub' });
    expect(matchCatalogPath('/jga-prag/aktivitaeten/beer-spa', 'de')).toEqual({ destinationSlug: 'prague', kind: 'activity' });
    expect(matchCatalogPath('/prague-stag-do/packages/weekend', 'en')).toEqual({ destinationSlug: 'prague', kind: 'package' });
    expect(matchCatalogPath('/destination/krakow', 'en')).toEqual({ destinationSlug: 'krakow', kind: 'hub' });
    expect(matchCatalogPath('/destination/krakow/activity/x', 'de')).toEqual({ destinationSlug: 'krakow', kind: 'activity' });
  });

  it('returns null elsewhere', () => {
    expect(matchCatalogPath('/about', 'en')).toBeNull();
    expect(matchCatalogPath('/ideas/how-to', 'en')).toBeNull();
    expect(matchCatalogPath('/', 'de')).toBeNull();
  });
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd myhive-react-app && npx react-scripts test --watchAll=false src/i18n/routes.test.js`
Expected: FAIL (`destinationPath is not a function`, etc.)

- [ ] **Step 3: Implement in `routes.js`**

Add after `DEFAULT_LOCALE`:

```js
// ---- URL scheme --------------------------------------------------------------
// Single owner of the public URL scheme, consumed by: the CRA router and link
// helpers (this file), Next's next-intl `pathnames` (myhive-next/i18n/routing.ts),
// Next's redirects (myhive-next/next.config.ts) and — as a hand-kept Java
// mirror — the backend's email deep links (util/PublicPaths.java).
// Destination slug -> EN-internal hub path. Cities absent here keep the old
// /destination/{slug} routes untouched.
export const DESTINATION_HUBS = { prague: '/prague-stag-do' };

// next-intl `pathnames` shape. EN is the internal form (= the Next route
// folder name); other locales are the public form the middleware rewrites.
// Paths not listed here are identical in every locale.
export const LOCALIZED_PATHNAMES = {
  '/prague-stag-do': { en: '/prague-stag-do', de: '/jga-prag' },
  '/prague-stag-do/activities/[slug]': {
    en: '/prague-stag-do/activities/[slug]',
    de: '/jga-prag/aktivitaeten/[slug]',
  },
  '/prague-stag-do/packages/[slug]': {
    en: '/prague-stag-do/packages/[slug]',
    de: '/jga-prag/pakete/[slug]',
  },
  '/ideas': { en: '/ideas', de: '/ratgeber' },
  '/ideas/[slug]': { en: '/ideas/[slug]', de: '/ratgeber/[slug]' },
};

/** EN-internal path of a destination's catalog page. */
export function destinationPath(destSlug) {
  return DESTINATION_HUBS[destSlug] ?? `/destination/${destSlug}`;
}

/** EN-internal path of an activity detail page. */
export function activityPath(destSlug, slug) {
  const hub = DESTINATION_HUBS[destSlug];
  return hub ? `${hub}/activities/${slug}` : `/destination/${destSlug}/activity/${slug}`;
}

/** EN-internal path of a package detail page. */
export function packagePath(destSlug, slug) {
  const hub = DESTINATION_HUBS[destSlug];
  return hub ? `${hub}/packages/${slug}` : `/destination/${destSlug}/package/${slug}`;
}

export function blogIndexPath() {
  return '/ideas';
}

export function blogPostPath(slug) {
  return `/ideas/${slug}`;
}

// '/a/[slug]' -> /^\/a\/([^/]+)$/ ; a react-router ':slug' is one segment too.
function patternToRegex(pattern) {
  const escaped = pattern
    .replace(/[.*+?^${}()|\\]/g, '\\$&')
    .replace(/\[[^\]]+\]/g, '([^/]+)');
  return new RegExp(`^${escaped}$`);
}

function fillPattern(pattern, captures) {
  let i = 0;
  return pattern.replace(/\[[^\]]+\]/g, () => captures[i++]);
}

// Longest pattern first so '/x/activities/[slug]' is tried before '/x'.
const TRANSLATIONS = Object.entries(LOCALIZED_PATHNAMES)
  .sort(([a], [b]) => b.length - a.length)
  .map(([internal, perLocale]) => ({
    internal,
    perLocale,
    internalRegex: patternToRegex(internal),
    publicRegexes: Object.fromEntries(
      Object.entries(perLocale).map(([l, p]) => [l, patternToRegex(p)])
    ),
  }));

/** ['/path', '?query#hash'] */
function splitSuffix(href) {
  const pathEnd = href.length - href.replace(/^[^?#]*/, '').length;
  return [href.slice(0, pathEnd), href.slice(pathEnd)];
}

/**
 * EN-internal path -> the locale's public segments, without the prefix.
 * Idempotent: anything that matches no internal pattern (unlisted paths,
 * already-translated paths) comes back unchanged. Query/hash are kept.
 */
export function localizeSegments(path, locale) {
  if (!locale || locale === DEFAULT_LOCALE) return path;
  const [p, rest] = splitSuffix(path);
  for (const { internalRegex, perLocale } of TRANSLATIONS) {
    const m = p.match(internalRegex);
    if (m) return fillPattern(perLocale[locale] ?? perLocale[DEFAULT_LOCALE], m.slice(1)) + rest;
  }
  return path;
}

/** Inverse of localizeSegments: a locale's public path -> EN-internal. */
export function delocalizeSegments(path, locale) {
  if (!locale || locale === DEFAULT_LOCALE) return path;
  const [p, rest] = splitSuffix(path);
  for (const { internal, publicRegexes } of TRANSLATIONS) {
    const regex = publicRegexes[locale];
    const m = regex ? p.match(regex) : null;
    if (m) return fillPattern(internal, m.slice(1)) + rest;
  }
  return path;
}

/**
 * The path CRA code hands to <Link to>, navigate() and <Route path>: the
 * current locale's segments WITHOUT the prefix — the SPA's BrowserRouter adds
 * the basename, LegacyRouter adds it on the Next side.
 */
export function routePath(path, locale = currentLocale()) {
  return localizeSegments(path, locale);
}

/**
 * Which catalog page a locale-free pathname is, in either the hub form
 * ('/jga-prag/aktivitaeten/x') or the legacy form ('/destination/krakow').
 */
export function matchCatalogPath(pathname, locale = currentLocale()) {
  const [p] = splitSuffix(pathname);
  const internal = delocalizeSegments(p, locale);
  const legacy = internal.match(/^\/destination\/([^/]+)(?:\/(activity|package)\/[^/]+)?$/);
  if (legacy) return { destinationSlug: legacy[1], kind: legacy[2] ?? 'hub' };
  for (const [destinationSlug, hub] of Object.entries(DESTINATION_HUBS)) {
    if (internal === hub) return { destinationSlug, kind: 'hub' };
    if (internal.startsWith(`${hub}/activities/`)) return { destinationSlug, kind: 'activity' };
    if (internal.startsWith(`${hub}/packages/`)) return { destinationSlug, kind: 'package' };
  }
  return null;
}
```

Replace the existing `localizePath` and `localizeHref`:

```js
/** Prefix + translate an EN-internal path for a locale. '/'+'de' -> '/de'. */
export function localizePath(path, locale) {
  if (!locale || locale === DEFAULT_LOCALE) return path;
  const [p, rest] = splitSuffix(localizeSegments(path, locale));
  return p === '/' ? `/${locale}${rest}` : `/${locale}${p}${rest}`;
}

/**
 * Locale-prefix (and translate the segments of) an href unless it is
 * external or already prefixed. Accepts hrefs with search/hash.
 */
export function localizeHref(href, locale) {
  if (!locale || locale === DEFAULT_LOCALE) return href;
  if (!href.startsWith('/')) return href;
  const seg = firstSegment(splitSuffix(href)[0]);
  if (SUPPORTED_LOCALES.includes(seg) && seg !== DEFAULT_LOCALE) return href;
  return localizePath(href, locale);
}
```

Note `currentLocale` is declared later in the file than these functions use it; that is fine (hoisted function declaration). Keep `splitLocale`, `bindSsrLocale`, `currentLocale`, `localeField`, `withLocaleParam` unchanged.

In `myhive-react-app/src/i18n/index.js` line 14 extend the re-export:

```js
export {
  DEFAULT_LOCALE, SUPPORTED_LOCALES, DESTINATION_HUBS, LOCALIZED_PATHNAMES,
  localizePath, localizeHref, splitLocale, localizeSegments, delocalizeSegments, routePath,
  destinationPath, activityPath, packagePath, blogIndexPath, blogPostPath, matchCatalogPath,
} from './routes';
```

- [ ] **Step 4: Run the new tests and the whole CRA suite**

Run: `cd myhive-react-app && npx react-scripts test --watchAll=false src/i18n/routes.test.js`
Expected: PASS.
Run: `cd myhive-react-app && CI=true npm test -- --watchAll=false`
Expected: PASS (no consumer changed yet; `/destination/...` paths are not in the table so behavior is unchanged).

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/i18n/routes.js myhive-react-app/src/i18n/routes.test.js myhive-react-app/src/i18n/index.js
git commit -m "feat(i18n): URL scheme table + localized path helpers for the Prague hub and /ideas"
```

---

### Task 2: Backend `PublicPaths` and email deep links

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/util/PublicPaths.java`
- Create: `myhive-backend/src/test/java/com/myhive/backend/util/PublicPathsTest.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java` (`dashboardUrlFor` ~line 419, `restoreUrlFor` ~531, `resultUrlFor` ~613 and their call sites at ~329, ~343-400, ~478)
- Modify: tests that assert the old links: `myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java`, `.../service/TripReminderTemplateRenderTest.java`, `.../controller/PublicControllerIntegrationTest.java` (grep `/destination/`).

**Interfaces:**
- Produces: `PublicPaths.destinationPath(String destinationSlug, String localeCode) -> String` returning the locale-prefixed public path (`/prague-stag-do`, `/de/jga-prag`, `/destination/krakow`, `/de/destination/krakow`). `null`/blank locale = English.

- [ ] **Step 1: Write the failing test**

```java
package com.myhive.backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PublicPathsTest {

    @Test
    void hubDestinationGetsKeywordPathPerLocale() {
        assertEquals("/prague-stag-do", PublicPaths.destinationPath("prague", null));
        assertEquals("/prague-stag-do", PublicPaths.destinationPath("prague", "en"));
        assertEquals("/de/jga-prag", PublicPaths.destinationPath("prague", "de"));
    }

    @Test
    void otherDestinationsKeepTheLegacyRoute() {
        assertEquals("/destination/krakow", PublicPaths.destinationPath("krakow", ""));
        assertEquals("/de/destination/krakow", PublicPaths.destinationPath("krakow", "de"));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd myhive-backend && ./gradlew test --tests '*PublicPathsTest'`
Expected: compilation failure (class missing).

- [ ] **Step 3: Implement `PublicPaths`**

```java
package com.myhive.backend.util;

import java.util.Map;

/**
 * Public (frontend) URL paths the backend has to build itself, i.e. in emails.
 * Hand-kept mirror of DESTINATION_HUBS / LOCALIZED_PATHNAMES in
 * myhive-react-app/src/i18n/routes.js — change both together.
 */
public final class PublicPaths {

    private static final String DEFAULT_LOCALE = "en";

    /** destination slug -> (locale -> hub path). Cities absent here stay on /destination/{slug}. */
    private static final Map<String, Map<String, String>> HUBS = Map.of(
            "prague", Map.of("en", "/prague-stag-do", "de", "/jga-prag"));

    private PublicPaths() {}

    /** Locale-prefixed catalog path: "/prague-stag-do", "/de/jga-prag", "/de/destination/krakow". */
    public static String destinationPath(String destinationSlug, String localeCode) {
        String locale = localeCode == null || localeCode.isBlank() ? DEFAULT_LOCALE : localeCode;
        String prefix = DEFAULT_LOCALE.equals(locale) ? "" : "/" + locale;
        Map<String, String> hub = HUBS.get(destinationSlug);
        if (hub != null) {
            return prefix + hub.getOrDefault(locale, hub.get(DEFAULT_LOCALE));
        }
        return prefix + "/destination/" + destinationSlug;
    }
}
```

- [ ] **Step 4: Rewire `EmailService`**

Read the three helpers and their callers first. Today callers pass `base = frontendUrl + localePrefix(locale)`. Change each helper to take the **raw** `frontendUrl` plus the locale and build the Trip Builder link as `frontendUrl + PublicPaths.destinationPath(slug, locale) + "?tab=..."`; keep the `/vote/...` branch of `resultUrlFor` on `frontendUrl + localePrefix(locale) + "/vote/..."`. Shape:

```java
private static String dashboardUrlFor(VoteSession session, String frontendUrl) {
    return frontendUrl + PublicPaths.destinationPath(session.getDestination().getSlug(), session.getLocale())
            + "?tab=trip-builder&voteSession=" + session.getShareToken()
            + "&manager=" + session.getManagerToken();
}

private static String restoreUrlFor(TripLead lead, String frontendUrl) {
    String destinationSlug = lead.getDestination() == null ? null : lead.getDestination().getSlug();
    if (destinationSlug == null) {
        return frontendUrl + localePrefix(lead.getLocale());
    }
    return frontendUrl + PublicPaths.destinationPath(destinationSlug, lead.getLocale())
            + "?tab=trip-builder&restore=" + lead.getRestoreToken();
}

private static String resultUrlFor(VoteSession session, String frontendUrl) {
    String destinationSlug = session.getDestination().getSlug();
    if (session.getVoteMode() == VoteMode.CART || destinationSlug == null) {
        return frontendUrl + localePrefix(session.getLocale()) + "/vote/" + session.getShareToken() + "/result";
    }
    return frontendUrl + PublicPaths.destinationPath(destinationSlug, session.getLocale())
            + "?tab=trip-builder&voteSession=" + session.getShareToken();
}
```

Update every call site to pass the raw `frontendUrl` (not `base`) to these three. Check what `localePrefix` accepts (a `String` code or a `Locale`) and adapt the argument. Do not change any `/vote/...` link.

- [ ] **Step 5: Update the link assertions in existing tests and add locale cases**

Grep `/destination/` in `src/test`. For fixtures whose destination slug is `prague`, the expected links become `/prague-stag-do?tab=...` (EN) and `/de/jga-prag?tab=...` (DE); for any other slug they stay `/destination/<slug>`. Add to `EmailServiceTest` one test per helper exercising a German session/lead with slug `prague`, e.g.:

```java
@Test
void voteResultEmailLinksToTheGermanHub() {
    VoteSession session = TestDataFactory.voteSession(...); // slug "prague", locale "de", mode QUIZ
    String expectedUrl = "https://www.trivlu.com/de/jga-prag?tab=trip-builder&voteSession=" + session.getShareToken();
    // act: emailService.sendVoteResult(session, List.of(), "https://www.trivlu.com")
    // assert: the captured template variable "resultUrl" equals expectedUrl
}
```

Follow the capture style the file already uses (the existing tests show how the Thymeleaf variables or the sent message are inspected).

- [ ] **Step 6: Run the backend tests**

Run: `cd myhive-backend && ./gradlew test --tests '*PublicPathsTest' --tests '*EmailServiceTest' --tests '*TripReminderTemplateRenderTest' --tests '*PublicControllerIntegrationTest'`
Expected: PASS. Then `./gradlew test` for the full suite: PASS.

- [ ] **Step 7: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/util/PublicPaths.java myhive-backend/src/test/java/com/myhive/backend/util/PublicPathsTest.java myhive-backend/src/main/java/com/myhive/backend/service/EmailService.java myhive-backend/src/test/java/com/myhive/backend/service/EmailServiceTest.java
# plus the other test files you touched
git commit -m "feat(email): Trip Builder deep links use the Prague hub URLs (PublicPaths mirror of routes.js)"
```

---

### Task 3: CRA consumers — routes, pages, links, breadcrumbs, switcher

**Files:**
- Modify: `myhive-react-app/src/components/Layout.js:57-63`
- Modify: `myhive-react-app/src/pages/DestinationPage.js:26`, `pages/ActivityDetailPage.js:28,164,170`, `pages/PackageDetailPage.js:16,58,64,89`
- Modify: `components/Header.js:40-48,84,94`, `components/Footer.js:20`, `components/LanguageSwitcher.js:41`
- Modify link builders: `components/ActivityCard.js:18`, `components/ActivityPreviewModal.js:71`, `components/ai/AiPackageView.js:133`, `components/DestinationCard.js:20`, `components/home/FeaturedActivitiesSection.js:78`, `components/PackageCard.js:10`, `components/TripBuilder.js:473,898`, `components/TripBuilderDropdown.js:98`, `pages/AiPlannerPage.js:320`, `pages/BlogPage.js:47`, `pages/BlogPostPage.js:33,67`, `pages/HomePage.js:49`, `pages/vote/ActivityVotePage.js:218`, `pages/vote/CuratePage.js:107,114`, `pages/vote/VoteResultPage.js:95,155`, `utils/groupVote.js:73`, `pages/AdminBlog.js:189` (placeholder text only)
- Modify tests under `myhive-react-app/src/**/*.test.js` whose expectations include `/destination/prague` or `/blog` (list: `grep -rln -e "/destination/" -e "/blog" src --include=*.test.js`).

**Interfaces:**
- Consumes from Task 1: `routePath`, `destinationPath`, `activityPath`, `packagePath`, `blogIndexPath`, `blogPostPath`, `matchCatalogPath`, `delocalizeSegments`, `DESTINATION_HUBS`, `SUPPORTED_LOCALES`, `localizePath` (all via `'../i18n'`).
- Produces: `DestinationPage`, `ActivityDetailPage`, `PackageDetailPage` accept an optional `destinationSlug` prop that wins over the route param. Next's legacy wrappers (Task 5) pass it.

- [ ] **Step 1: Write the failing tests**

In `components/Header.test.js` add (adapt to the file's existing render helper / router setup):

```js
it('shows the destination breadcrumb on the German hub URL', () => {
  // render Header at MemoryRouter initialEntries ['/jga-prag?tab=activities'] with
  // window.location.pathname stubbed to '/de/jga-prag' so currentLocale() is 'de'
  // and a catalog containing { slug: 'prague', name: 'Prague' }
  // expect the breadcrumb link to have href '/jga-prag?tab=activities'
});
```

In `components/LanguageSwitcher.test.js` (create if missing) add:

```js
it('links the German activity page to its English pair', () => {
  // render at MemoryRouter initialEntries ['/jga-prag/aktivitaeten/beer-spa'] inside
  // LocaleProvider locale 'de'; open the menu; the English item href must be
  // '/prague-stag-do/activities/beer-spa'
});
it('links the English hub to the German hub', () => {
  // at '/prague-stag-do?tab=activities' with locale 'en' the German item href must be
  // '/de/jga-prag?tab=activities'
});
```

In `pages/DestinationPage.test.js` add:

```js
it('uses the destinationSlug prop when the hub route carries no param', () => {
  // render <DestinationPage destinationSlug="prague" /> at '/prague-stag-do'
  // expect api.getDestinationBySlug (or whatever the page calls) to be called with 'prague'
});
```

Write real tests following the surrounding files' helpers (they mock `services/api`, wrap in `AppProviders`/`LocaleProvider`, use `MemoryRouter`). Replace the comments above with code.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd myhive-react-app && npx react-scripts test --watchAll=false src/components/Header.test.js src/components/LanguageSwitcher.test.js src/pages/DestinationPage.test.js`
Expected: FAIL.

- [ ] **Step 3: Route table in `Layout.js`**

Replace lines 58-63 with routes generated from the table. Add imports `DESTINATION_HUBS, SUPPORTED_LOCALES, routePath` from `'../i18n'` and:

```js
// One <Route> per locale variant of a pattern ('/prague-stag-do' and '/jga-prag'
// both render the Prague catalog): the SPA's BrowserRouter only strips the
// locale prefix, the segments stay as the public URL spells them.
function localizedPaths(pattern) {
  return [...new Set(SUPPORTED_LOCALES.map((l) => routePath(pattern, l)))];
}

function hubRoutes() {
  return Object.entries(DESTINATION_HUBS).flatMap(([destinationSlug, hub]) => [
    ...localizedPaths(hub).map((p) => (
      <Route key={p} path={p} element={<DestinationPage destinationSlug={destinationSlug}/>}/>
    )),
    ...localizedPaths(`${hub}/activities/:slug`).map((p) => (
      <Route key={p} path={p} element={<ActivityDetailPage destinationSlug={destinationSlug}/>}/>
    )),
    ...localizedPaths(`${hub}/packages/:slug`).map((p) => (
      <Route key={p} path={p} element={<PackageDetailPage destinationSlug={destinationSlug}/>}/>
    )),
  ]);
}
```

and inside `<Routes>`:

```jsx
{hubRoutes()}
<Route path="/destination/:slug" element={<DestinationPage/>}/>
<Route path="/destination/:destinationSlug/activity/:slug" element={<ActivityDetailPage/>}/>
<Route path="/destination/:destSlug/package/:slug" element={<PackageDetailPage/>}/>
{localizedPaths('/ideas').map((p) => <Route key={p} path={p} element={<BlogPage/>}/>)}
{localizedPaths('/ideas/:slug').map((p) => <Route key={p} path={p} element={<BlogPostPage/>}/>)}
```

Remove the `/blog` routes.

- [ ] **Step 4: Pages take the slug from the prop**

`DestinationPage.js`: signature `function DestinationPage({initial, destinationSlug})`; replace `const {slug} = useParams();` with
```js
const {slug: slugParam} = useParams();
const slug = destinationSlug ?? slugParam;
```
Canonical (line 233): `href={`${SITE_URL}${localizePath(destinationPath(destination.slug), locale)}`}` using `const locale = useLocale();` (import `useLocale`, `localizePath`, `destinationPath`).

`ActivityDetailPage.js`: `function ActivityDetailPage({activity: initialActivity, destinationSlug: destinationSlugProp})` (keep whatever the first prop is called today); `const {destinationSlug: destinationSlugParam, slug} = useParams(); const destinationSlug = destinationSlugProp ?? destinationSlugParam;`. Line 164 canonical → `localizePath(activityPath(destSlug, activity.slug), locale)`; line 170 → `to={routePath(`${destinationPath(destSlug)}?tab=activities`)}`.

`PackageDetailPage.js`: same pattern with `destSlug`; line 58 canonical → `localizePath(packagePath(resolvedDestSlug, pkg.slug), locale)`; 64 → `routePath(destinationPath(resolvedDestSlug))`; 89 → `routePath(activityPath(resolvedDestSlug, activity.slug))`.

- [ ] **Step 5: Header breadcrumbs and switcher**

`Header.js` lines 40-48:
```js
const catalogMatch = matchCatalogPath(location.pathname);
const destinationSlug = catalogMatch?.destinationSlug ?? null;
const destination = destinationSlug ? catalog.destinations.find((item) => item.slug === destinationSlug) : null;
const isDetailPage = catalogMatch != null && catalogMatch.kind !== 'hub';
const showBreadcrumbs = Boolean(destinationSlug) && !isDetailPage;
```
(keep whatever else those lines compute). Line 84: `to={routePath(blogIndexPath())}`; line 94: `to={routePath(`${destinationPath(destinationSlug)}?tab=activities`)}`.

`LanguageSwitcher.js` line 41:
```js
// location.pathname is locale-free but spelled in the CURRENT locale's segments
// (/jga-prag/...): go back to the internal form so the other locale's href is
// its proper pair and not "/de + same path".
const path = delocalizeSegments(location.pathname, locale) + location.search;
```

`Footer.js:20`: `to={routePath(blogIndexPath())}`.

- [ ] **Step 6: Every other link builder**

Replace each template literal with the helper (imports from `'../i18n'` / `'../../i18n'`):

| File:line | New expression |
| --- | --- |
| `ActivityCard.js:18` | ``routePath(activityPath(destSlug, activity.slug || activity.id))`` |
| `ActivityPreviewModal.js:71` | `routePath(activityPath(activity.destinationSlug, activity.slug))` (keep the `: null` branch) |
| `ai/AiPackageView.js:133` | ``lp(`${destinationPath(destinationSlug)}?tab=activities`)`` |
| `DestinationCard.js:20` | ``navigate(routePath(destinationPath(destination.slug || destination.id)))`` |
| `home/FeaturedActivitiesSection.js:78` | `to={routePath(destinationPath(mainDestination.slug))}` |
| `PackageCard.js:10` | `to={routePath(packagePath(pkg.destinationSlug, pkg.slug))}` |
| `TripBuilder.js:473` | `routePath(activityPath(activity.destinationSlug, activity.slug))` |
| `TripBuilder.js:898` | ``lp(`${destinationPath(destinationSlug)}?tab=activities`)`` |
| `TripBuilderDropdown.js:98` | ``routePath(`${destinationPath(destSlug)}?tab=trip-builder`)`` (keep the `organizerVote` branch) |
| `AiPlannerPage.js:320` | ``navigate(routePath(`${destinationPath(destination.slug)}?tab=trip-builder&book=1`))`` |
| `BlogPage.js:47` | ``to={routePath(blogPostPath(post.slug || post.id))}`` |
| `BlogPostPage.js:33,67` | `to={routePath(blogIndexPath())}` |
| `HomePage.js:49` | ``explorePath={routePath(`${destinationPath(exploreActivitiesSlug)}?tab=activities`)}`` |
| `vote/ActivityVotePage.js:218` | `routePath(activityPath(activity.destinationSlug, activity.slug))` |
| `vote/CuratePage.js:107` | ``navigate(routePath(`${destinationPath(setup.destination.slug)}?tab=trip-builder`), { replace: true })`` |
| `vote/CuratePage.js:114` | `routePath(activityPath(activity.destinationSlug, activity.slug))` |
| `vote/VoteResultPage.js:95,155` | ``routePath(`${destinationPath(data.destinationSlug)}?tab=trip-builder&voteSession=${shareToken}`)`` |
| `utils/groupVote.js:73` | ``routePath(`${destinationPath(destinationSlug)}?tab=trip-builder&voteSession=${shareToken}`)`` |
| `AdminBlog.js:189` | placeholder text `[links](/prague-stag-do)` |

`lp` is the existing `useLocalePath()` result where present (it now translates segments through `localizePath`).

- [ ] **Step 7: Run the full CRA suite and fix expectations**

Run: `cd myhive-react-app && CI=true npm test -- --watchAll=false`
Fix tests whose fixtures use slug `prague` and asserted `/destination/prague...` → the hub form; fixtures with other slugs keep the legacy form (that is the intended behavior, not a regression). Expected: PASS, including the three new tests.

- [ ] **Step 8: Commit**

```bash
git add myhive-react-app/src/components myhive-react-app/src/pages myhive-react-app/src/utils/groupVote.js
git commit -m "feat(cra): Prague hub + /ideas routes, links, breadcrumbs and language switcher on the new URL scheme"
```
(`git add` of these directories is fine: they contain only tracked UI source; verify with `git status --short` that nothing under `docs/research` or `graphify-out` is staged.)

---

### Task 4: Next routing — next-intl pathnames, 308 redirects, 410 middleware

**Files:**
- Modify: `myhive-next/i18n/routing.ts`
- Modify: `myhive-next/middleware.ts`
- Create: `myhive-next/lib/gone.ts`, `myhive-next/lib/__tests__/gone.test.ts`
- Modify: `myhive-next/next.config.ts`
- Check: how `legacy-src` is typed for TS imports (`grep -rn "legacy-src" myhive-next/tsconfig.json myhive-next/*.d.ts`); if a declaration file lists the module's exports, add the Task 1 exports to it.

**Interfaces:**
- Consumes from Task 1 (via `./legacy-src/i18n/routes`): `LOCALIZED_PATHNAMES`, `DESTINATION_HUBS`, `SUPPORTED_LOCALES`, `localizePath`.
- Produces: `isGone(pathname: string): boolean`, `GONE_BLOG_SLUGS`.

- [ ] **Step 1: Failing test for the 410 list**

`myhive-next/lib/__tests__/gone.test.ts`:
```ts
import { describe, expect, it } from 'vitest';
import { GONE_BLOG_SLUGS, isGone } from '../gone';

describe('isGone', () => {
  it('marks the five retired seed posts in both locales, trailing slash tolerated', () => {
    expect(GONE_BLOG_SLUGS).toHaveLength(5);
    expect(isGone('/blog/bali-on-a-budget-a-group-travel-guide')).toBe(true);
    expect(isGone('/de/blog/bali-on-a-budget-a-group-travel-guide/')).toBe(true);
  });
  it('leaves everything else alone', () => {
    expect(isGone('/blog')).toBe(false);
    expect(isGone('/blog/how-to-plan-a-stress-free-group-trip')).toBe(false);
    expect(isGone('/ideas/bali-on-a-budget-a-group-travel-guide')).toBe(false);
  });
});
```
Run: `cd myhive-next && npx vitest run lib` → FAIL (module missing).

- [ ] **Step 2: `lib/gone.ts`**

```ts
// Retired seed posts (ТЗ 2026-10: "410, not 404 and not a redirect to the
// homepage"). next.config redirects() cannot emit 410, so middleware does.
export const GONE_BLOG_SLUGS = [
  'top-5-group-travel-destinations-for-2026',
  'why-ai-is-changing-the-way-we-travel-together',
  'bali-on-a-budget-a-group-travel-guide',
  'the-rise-of-multi-traveler-experiences',
  'weekend-getaway-ideas-for-large-groups',
] as const;

const GONE = new Set(GONE_BLOG_SLUGS.flatMap((s) => [`/blog/${s}`, `/de/blog/${s}`]));

export function isGone(pathname: string): boolean {
  return GONE.has(pathname.replace(/\/+$/, '') || '/');
}
```

- [ ] **Step 3: Middleware**

```ts
import createMiddleware from 'next-intl/middleware';
import { NextResponse, type NextRequest } from 'next/server';
import { routing } from './i18n/routing';
import { isGone } from './lib/gone';

// Maps URLs onto the [locale] segment and, via routing.pathnames, German
// public paths (/de/jga-prag/...) onto the EN-named route folders.
const intl = createMiddleware(routing);

export default function middleware(req: NextRequest) {
  // 410 before locale handling: these URLs are gone in every locale.
  if (isGone(req.nextUrl.pathname)) {
    return new NextResponse('Gone', { status: 410, headers: { 'content-type': 'text/plain' } });
  }
  return intl(req);
}

export const config = {
  matcher: '/((?!api|_next|_vercel|.*\\..*).*)',
};
```
Keep the existing matcher comment.

- [ ] **Step 4: `i18n/routing.ts` pathnames**

Before editing, confirm with Context7 (`resolve-library-id` "next-intl", then `query-docs` "localized pathnames defineRouting pathnames middleware") two facts and write the answer as a comment: (a) pathnames not listed are shared across locales; (b) what the middleware does for `/de/<internal path>` (redirect to the localized path or pass through). Then:

```ts
import { SUPPORTED_LOCALES, DEFAULT_LOCALE, LOCALIZED_PATHNAMES } from '../legacy-src/i18n/routes';

export const routing = defineRouting({
  locales: SUPPORTED_LOCALES,
  defaultLocale: DEFAULT_LOCALE,
  localePrefix: 'as-needed',
  localeDetection: false,
  localeCookie: false,
  // German public segments (/de/jga-prag/aktivitaeten/[slug]) are rewritten by
  // the middleware onto the EN-named route folders. The table is owned by the
  // legacy i18n module so CRA links and Next routing cannot disagree.
  pathnames: LOCALIZED_PATHNAMES,
});
```
If TypeScript rejects the untyped import, cast: `pathnames: LOCALIZED_PATHNAMES as Record<string, Record<'en' | 'de', string>>` (or extend the `.d.ts` found in the check above).

- [ ] **Step 5: Redirects in `next.config.ts`**

Add the import `import { DESTINATION_HUBS, SUPPORTED_LOCALES, localizePath } from './legacy-src/i18n/routes';` and:

```ts
// 308s from the pre-2026-10 URL scheme (spec: docs/superpowers/specs/2026-10-15-seo-prague-urls-phase1-design.md).
// Rules are generated from the same table the links use, scoped to hub
// destinations only, so cities on /destination/{slug} are never touched.
function legacyUrlRedirects() {
  const rules: { source: string; destination: string; permanent: true }[] = [];
  const rule = (source: string, destination: string) => rules.push({ source, destination, permanent: true });
  for (const [slug, hub] of Object.entries(DESTINATION_HUBS)) {
    for (const locale of SUPPORTED_LOCALES) {
      rule(localizePath(`/destination/${slug}`, locale), localizePath(hub, locale));
      rule(localizePath(`/destination/${slug}/activity/:slug`, locale), localizePath(`${hub}/activities/:slug`, locale));
      rule(localizePath(`/destination/${slug}/package/:slug`, locale), localizePath(`${hub}/packages/:slug`, locale));
    }
  }
  for (const locale of SUPPORTED_LOCALES) {
    rule(localizePath('/blog', locale), localizePath('/ideas', locale));
    // The one kept post; its slug is renamed in the admin on deploy day. No
    // generic /blog/:slug rule on purpose: redirects run before middleware and
    // would swallow the 410s (lib/gone.ts).
    rule(localizePath('/blog/how-to-plan-a-stress-free-group-trip', locale), localizePath('/ideas/how-to-plan-a-stag-do', locale));
  }
  return rules;
}
```
and in `nextConfig`: `async redirects() { return legacyUrlRedirects(); },`.

Verify the config loads: `cd myhive-next && npm run sync-legacy && npx next build` (set `BACKEND_URL=http://localhost:8080` in the environment for the build). If Next's config loader cannot import `./legacy-src/i18n/routes` (ESM/TS loader error), fall back to: a hand-written rule list in `next.config.ts` plus `lib/__tests__/redirects.test.ts` that imports the list (export it from a new `lib/redirects.ts`) and asserts it deep-equals the generated one. Record which path you took in the commit message.

- [ ] **Step 6: Run and commit**

Run: `cd myhive-next && npx vitest run` → PASS. `npx tsc --noEmit` → clean (or the same pre-existing errors as on `main`; check `git stash`-free by comparing with `git show main:...` if needed).

```bash
git add myhive-next/i18n/routing.ts myhive-next/middleware.ts myhive-next/lib/gone.ts myhive-next/lib/__tests__/gone.test.ts myhive-next/next.config.ts
git commit -m "feat(next): next-intl pathnames for the German segments, 308 redirects from the old Prague/blog URLs, 410 for retired posts"
```

---

### Task 5: Next pages, legacy wrappers, sitemap, landings, smoke script

**Files:**
- Create: `myhive-next/components/site/catalog/DestinationCatalog.tsx`, `ActivityDetail.tsx`, `PackageDetail.tsx` (server modules; move the bodies of the three existing route files there)
- Create: `myhive-next/app/[locale]/prague-stag-do/page.tsx`, `myhive-next/app/[locale]/(public)/prague-stag-do/activities/[aslug]/page.tsx`, `myhive-next/app/[locale]/(public)/prague-stag-do/packages/[pslug]/page.tsx`
- Modify (thin): `myhive-next/app/[locale]/destination/[slug]/page.tsx`, `.../(public)/destination/[slug]/activity/[aslug]/page.tsx`, `.../(public)/destination/[slug]/package/[pslug]/page.tsx`
- Rename: `myhive-next/app/[locale]/(public)/blog` → `.../(public)/ideas` (`git mv`), update `path:` and breadcrumb paths inside
- Modify: `components/site/legacy/LegacyDestination.tsx`, `LegacyActivityDetail.tsx`, `LegacyPackageDetail.tsx`, `LegacyBlog.tsx`, `LegacyBlogPost.tsx`
- Modify: `app/sitemap.ts`, `components/landing/data.ts`, `components/landing/PragueLanding.tsx:157`, `components/landing/VoteLanding.tsx:350`, `components/landing/LandingFooter.tsx:51`, `components/landing/LandingLanguageSwitcher.tsx`, `app/[locale]/landing/prague/page.tsx:5` (comment), `scripts/smoke.mjs`
- Test: `components/landing/__tests__/data.test.ts` (expectations for the new paths)

**Interfaces:**
- Consumes from Task 1: `destinationPath`, `activityPath`, `packagePath`, `blogIndexPath`, `blogPostPath`, `routePath`, `delocalizeSegments`, `DESTINATION_HUBS`; from Task 3: the `destinationSlug` prop on the three CRA pages.
- Produces: `DestinationCatalog.tsx` exports `destinationMetadata({ destinationSlug, locale, searchParams })` and `DestinationCatalogPage({ destinationSlug, locale, searchParams })`; `ActivityDetail.tsx` exports `activityMetadata({ destinationSlug, activitySlug, locale })` and `ActivityDetailPage(...)`; `PackageDetail.tsx` the same with `packageSlug`.

- [ ] **Step 1: Failing test for the landing link builders**

In `components/landing/__tests__/data.test.ts` add:
```ts
it('builds Prague links on the hub scheme', () => {
  expect(tripBuilderPath('prague')).toBe('/prague-stag-do?tab=trip-builder');
  expect(activityPath('prague', 'beer-spa')).toBe('/prague-stag-do/activities/beer-spa');
  expect(categoryPath('prague', 'beer')).toBe('/prague-stag-do?tab=activities&category=beer');
});
```
(use the exported names `data.ts` actually has; read the file). Run `npx vitest run components/landing` → FAIL.

- [ ] **Step 2: Shared server modules**

`components/site/catalog/DestinationCatalog.tsx`: move everything from `app/[locale]/destination/[slug]/page.tsx` except the Next-specific exports into two functions. `destinationMetadata` returns what `generateMetadata` returned, with `path: destinationPath(destinationSlug)`. `DestinationCatalogPage` returns the JSX; breadcrumb path `destinationPath(dest.slug)`; it passes `destinationSlug` to `<LegacyDestination initial={...} destinationSlug={dest.slug} />`. Keep the `?tab=`/`?voteSession=` escape hatch and its `noindex` metadata branch exactly as they are.

Thin route files, e.g. `app/[locale]/prague-stag-do/page.tsx`:
```tsx
import type { Metadata } from 'next';
import { DestinationCatalogPage, destinationMetadata } from '@/components/site/catalog/DestinationCatalog';

// Folder name = DESTINATION_HUBS.prague in legacy-src/i18n/routes.js (the DE
// spelling /jga-prag is rewritten here by next-intl pathnames). A second hub
// city gets its own folder + table row.
const DESTINATION_SLUG = 'prague';

interface PageParams {
  params: Promise<{ locale: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}

export async function generateMetadata({ params, searchParams }: PageParams): Promise<Metadata> {
  const { locale } = await params;
  return destinationMetadata({ destinationSlug: DESTINATION_SLUG, locale, searchParams: await searchParams });
}

export default async function Page({ params, searchParams }: PageParams) {
  const { locale } = await params;
  return DestinationCatalogPage({ destinationSlug: DESTINATION_SLUG, locale, searchParams: await searchParams });
}
```
`app/[locale]/destination/[slug]/page.tsx` becomes the same with `destinationSlug: slug` from params. Keep `setRequestLocale(locale)` inside the shared page function (first thing). Repeat for activity (`revalidate = 3600` stays in each route file) and package.

- [ ] **Step 3: Legacy wrappers compute locale-aware patterns**

`LegacyDestination.tsx`:
```tsx
import { useLocale } from '../../../legacy-src/i18n';
import { DESTINATION_HUBS, routePath } from '../../../legacy-src/i18n/routes';
// ...
export default function LegacyDestination({ initial, destinationSlug }: { initial: DestinationInitial; destinationSlug: string }) {
  const locale = useLocale();
  const hub = DESTINATION_HUBS[destinationSlug];
  // The bridge feeds the PUBLIC path (/jga-prag on DE), so the pattern must be
  // spelled in this locale's segments or <Routes> renders nothing.
  const pattern = routePath(hub ?? '/destination/:slug', locale);
  return (
    <RouteMatch pattern={pattern}>
      <DestinationPage initial={initial} destinationSlug={destinationSlug} />
    </RouteMatch>
  );
}
```
`LegacyActivityDetail`: pattern `routePath(hub ? `${hub}/activities/:slug` : '/destination/:destinationSlug/activity/:slug', locale)`, pass `destinationSlug`. `LegacyPackageDetail`: `hub ? `${hub}/packages/:slug` : '/destination/:destSlug/package/:slug'`. `LegacyBlog`: `routePath('/ideas', locale)`; `LegacyBlogPost`: `routePath('/ideas/:slug', locale)`.

- [ ] **Step 4: Blog → ideas, sitemap, landings, switcher, smoke**

- `git mv "myhive-next/app/[locale]/(public)/blog" "myhive-next/app/[locale]/(public)/ideas"`; inside: `path: blogIndexPath()`, `path: blogPostPath(post.slug || slug)`, breadcrumbs `[t('blog'), blogIndexPath()]`, `mainEntityOfPage: canonical(blogPostPath(...), locale)`.
- `sitemap.ts`: `pushLocalized(urls, blogIndexPath(), 0.8)`, `destinationPath(dest.slug)`, `activityPath(dest.slug, a.slug)`, `packagePath(dest.slug, p.slug)`, `blogPostPath(post.slug)`.
- `components/landing/data.ts`: implement its three builders with `destinationPath`/`activityPath` from `../../legacy-src/i18n/routes`.
- `PragueLanding.tsx:157`, `VoteLanding.tsx:350`: ``lp(`${destinationPath(destinationSlug)}?tab=activities`)``; `LandingFooter.tsx:51`: `lp(blogIndexPath())`.
- `LandingLanguageSwitcher.tsx`: `const path = delocalizeSegments(splitLocale(pathname).pathname, locale);` (import from `../../legacy-src/i18n/routes`).
- `scripts/smoke.mjs`: `/destination/prague` → `/prague-stag-do`; the activity-link regex to `/prague-stag-do/activities/<slug>`; `/blog` → `/ideas` (title unchanged); 404 checks: keep `/destination/atlantis`, change to `/prague-stag-do/activities/not-a-real-activity`, `/ideas/not-a-real-post`. Add: `/de/jga-prag` 200 with canonical `/de/jga-prag`, and `/de/jga-prag/aktivitaeten/<slug>` 200 whose HTML contains `hreflang="en"` pointing at `/prague-stag-do/activities/<slug>`.

- [ ] **Step 5: Build and test**

Run: `cd myhive-next && npm run sync-legacy && npx vitest run && npx tsc --noEmit && BACKEND_URL=http://localhost:8080 npx next build`
Expected: all green. If `next build` fails on a page, fix the page; do not skip the build.

- [ ] **Step 6: Commit**

```bash
git add "myhive-next/app/[locale]" myhive-next/components/site myhive-next/components/landing myhive-next/app/sitemap.ts myhive-next/scripts/smoke.mjs
git commit -m "feat(next): Prague hub, activity and package routes on /prague-stag-do, blog on /ideas; sitemap, landings and legacy wrappers follow the table"
```

---

### Task 6: Redirect acceptance script and end-to-end run

**Files:**
- Create: `myhive-next/scripts/check-redirects.mjs`
- Modify (if the run finds bugs): whichever file owns them, each fix in its own commit.
- Output: `docs/changes/2026-10-15-seo-prague-urls-phase1.md` section "Verification" (Task 7 writes the doc; this task saves raw outputs to the scratchpad directory and reports them).

- [ ] **Step 1: The script**

```js
// Acceptance for the 2026-10 URL move (spec: docs/superpowers/specs/2026-10-15-seo-prague-urls-phase1-design.md).
// Usage: node scripts/check-redirects.mjs [base-url]   (default http://localhost:3000)
// Every old address must be ONE 308 to the new one, which answers 200; the
// retired posts answer 410 with no hop; landings and non-hub cities never redirect.
import { DESTINATION_HUBS, SUPPORTED_LOCALES, activityPath, destinationPath, localizePath, packagePath } from '../legacy-src/i18n/routes.js';
import { GONE_BLOG_SLUGS } from '../lib/gone.ts'; // if .ts import fails under node, duplicate the 5 slugs here with a comment

const base = (process.argv[2] ?? 'http://localhost:3000').replace(/\/+$/, '');
let errors = 0;

async function follow(path) {
  const hops = [];
  let url = base + path;
  for (let i = 0; i < 5; i += 1) {
    const res = await fetch(url, { redirect: 'manual' });
    if (res.status >= 300 && res.status < 400) {
      hops.push(res.status);
      url = new URL(res.headers.get('location'), url).toString();
      continue;
    }
    return { hops, status: res.status, url };
  }
  return { hops, status: -1, url };
}

function report(ok, label, r) {
  if (!ok) {
    errors += 1;
    console.log(`FAIL ${label}: hops=${JSON.stringify(r.hops)} status=${r.status} url=${r.url}`);
  }
}

async function expectSingle308(oldPath, newPath) {
  const r = await follow(oldPath);
  report(r.hops.length === 1 && r.hops[0] === 308 && r.status === 200 && r.url === base + newPath, `${oldPath} -> ${newPath}`, r);
}
async function expectDirect(path, status) {
  const r = await follow(path);
  report(r.hops.length === 0 && r.status === status, `${path} is ${status} with no hop`, r);
}

const destinations = await (await fetch(`${base}/api/destinations`)).json();
for (const dest of destinations) {
  for (const locale of SUPPORTED_LOCALES) {
    const old = (p) => localizePath(p, locale);
    if (DESTINATION_HUBS[dest.slug]) {
      await expectSingle308(old(`/destination/${dest.slug}`), localizePath(destinationPath(dest.slug), locale));
      await expectSingle308(old(`/destination/${dest.slug}?utm_source=test&tab=trip-builder`), localizePath(`${destinationPath(dest.slug)}?utm_source=test&tab=trip-builder`, locale));
      const activities = await (await fetch(`${base}/api/activities?destinationId=${dest.id}`)).json(); // check lib/api.ts for the real query
      for (const a of activities) {
        await expectSingle308(old(`/destination/${dest.slug}/activity/${a.slug}`), localizePath(activityPath(dest.slug, a.slug), locale));
      }
      const packages = await (await fetch(`${base}/api/packages?destinationId=${dest.id}`)).json();
      for (const p of packages) {
        await expectSingle308(old(`/destination/${dest.slug}/package/${p.slug}`), localizePath(packagePath(dest.slug, p.slug), locale));
      }
    } else {
      await expectDirect(old(`/destination/${dest.slug}`), 200);
    }
  }
}
for (const locale of SUPPORTED_LOCALES) {
  await expectSingle308(localizePath('/blog', locale), localizePath('/ideas', locale));
  for (const slug of GONE_BLOG_SLUGS) await expectDirect(localizePath(`/blog/${slug}`, locale), 410);
  await expectDirect(localizePath('/blog/not-a-post', locale), 404);
  await expectDirect(localizePath('/landing/prague', locale), 200);
  await expectDirect(localizePath('/destination/not-a-city', locale), 404);
}
console.log(`errors: ${errors}`);
process.exit(errors === 0 ? 0 : 1);
```
Read `lib/api.ts` for the real activities/packages query parameters and adapt. The script needs no dependencies.

- [ ] **Step 2: Start both servers**

Check port 8080 is free first (`netstat -ano | findstr :8080`; Steam's CEF sometimes squats it — if so, note it and run the backend on `--server.port=8081` with `BACKEND_URL=http://localhost:8081` for Next). Backend: `cd myhive-backend && ./gradlew bootRun --args='--spring.profiles.active=dev'` in the background; Next: `cd myhive-next && npm run dev` in the background. Wait until `curl -s localhost:8080/destinations` returns JSON and `curl -sI localhost:3000/` returns 200.

- [ ] **Step 3: Run the acceptance**

Run: `cd myhive-next && node scripts/check-redirects.mjs http://localhost:3000` → must end with `errors: 0`.
Run: `node scripts/smoke.mjs http://localhost:3000` → PASS.
Then record (save stdout to the scratchpad and paste the lines into your report):
```
curl -sI http://localhost:3000/de/prague-stag-do            # expected: 307/308 to /de/jga-prag OR 404 — never 500
curl -sI http://localhost:3000/jga-prag                     # expected: 404 or a redirect — never 500
curl -sI http://localhost:3000/de/ideas                     # same expectation
curl -sI "http://localhost:3000/prague-stag-do?tab=trip-builder"   # 200, and the body carries robots noindex
curl -s  http://localhost:3000/de/jga-prag | grep -o '<link rel="canonical"[^>]*>'       # /de/jga-prag
curl -s  http://localhost:3000/de/jga-prag | grep -o '<link rel="alternate"[^>]*>'       # en -> /prague-stag-do, de -> /de/jga-prag, x-default -> /prague-stag-do
curl -s  http://localhost:3000/sitemap.xml | grep -c 'prague-stag-do'                     # > 0
curl -s  http://localhost:3000/sitemap.xml | grep -c '/destination/prague'                # 0
```
Open `http://localhost:3000/de/jga-prag` in the browser (Playwright MCP or claude-in-chrome) and click the language switcher's English item: the URL must become `/prague-stag-do`. Click an activity card: URL `/de/jga-prag/aktivitaeten/<slug>`, page renders. Open `/prague-stag-do?tab=trip-builder`: the Trip Builder tab renders (SPA shim).

- [ ] **Step 4: Fix and commit anything red, then commit the script**

```bash
git add myhive-next/scripts/check-redirects.mjs
git commit -m "test(next): check-redirects.mjs — single-hop 308 / 410 / untouched-city acceptance for the URL move"
```
Stop both servers when done.

---

### Task 7: Change doc

**Files:**
- Create: `docs/changes/2026-10-15-seo-prague-urls-phase1.md`

- [ ] **Step 1: Write it** — what moved (table of old → new), the design (one table, three consumers), the deviations from the ТЗ (prague-scoped masks, packages moved now, DE slugs deferred, moved post keeps EN slug on DE), the verification outputs from Task 6 verbatim, the deploy-day checklist from the spec, and the open items for phases 2/3. Then `graphify update .`.

- [ ] **Step 2: Commit**

```bash
git add docs/changes/2026-10-15-seo-prague-urls-phase1.md
git commit -m "docs: change log for the Prague URL move, phase 1"
```
