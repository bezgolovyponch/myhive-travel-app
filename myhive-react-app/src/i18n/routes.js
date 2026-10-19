// Locale-aware URL helpers, hook-free so both server code (the Next catch-all)
// and client code (LegacyRouter, components) can import them.

export const SUPPORTED_LOCALES = ['en', 'de'];
export const DEFAULT_LOCALE = 'en';

// ---- URL scheme --------------------------------------------------------------
// Single owner of the public URL scheme, consumed by: the CRA router and link
// helpers (this file), Next's next-intl `pathnames` (myhive-next/i18n/routing.ts),
// Next's redirects (myhive-next/next.config.ts) and — as a hand-kept Java
// mirror — the backend's email deep links (util/PublicPaths.java).
// Destination slug -> EN-internal hub path. Cities absent here keep the old
// /destination/{slug} routes untouched.
/** @type {Record<string, string>} */
export const DESTINATION_HUBS = { prague: '/prague-stag-do' };

function hubOf(destSlug) {
  return Object.hasOwn(DESTINATION_HUBS, destSlug) ? DESTINATION_HUBS[destSlug] : undefined;
}

// next-intl `pathnames` shape. EN is the internal form (= the Next route
// folder name); other locales are the public form the middleware rewrites.
// Paths not listed here are identical in every locale.
/** @type {Record<string, Record<string, string>>} */
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
  return hubOf(destSlug) ?? `/destination/${destSlug}`;
}

/** EN-internal path of an activity detail page. */
export function activityPath(destSlug, slug) {
  const hub = hubOf(destSlug);
  return hub ? `${hub}/activities/${slug}` : `/destination/${destSlug}/activity/${slug}`;
}

/** EN-internal path of a package detail page. */
export function packagePath(destSlug, slug) {
  const hub = hubOf(destSlug);
  return hub ? `${hub}/packages/${slug}` : `/destination/${destSlug}/package/${slug}`;
}

export function blogIndexPath() {
  return '/ideas';
}

export function blogPostPath(slug) {
  return `/ideas/${slug}`;
}

/**
 * The slug to link an entity (activity, package, blog post, ...) by in a locale:
 * the German slug when the locale is not the default and the entity has one,
 * the English `slug` otherwise.
 * @param {{slug: string, slugDe?: string|null}} entity
 * @param {string} [locale]
 * @returns {string}
 */
export function entitySlug(entity, locale = currentLocale()) {
  return locale !== DEFAULT_LOCALE && entity.slugDe ? entity.slugDe : entity.slug;
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

// Every regex is anchored (^...$), so the order of these entries cannot change a result.
const TRANSLATIONS = Object.entries(LOCALIZED_PATHNAMES)
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
    if (patternToRegex(`${hub}/activities/[slug]`).test(internal)) {
      return { destinationSlug, kind: 'activity' };
    }
    if (patternToRegex(`${hub}/packages/[slug]`).test(internal)) {
      return { destinationSlug, kind: 'package' };
    }
  }
  return null;
}

// URLs the legacy SPA owns (client-only flows), used by the Next catch-all to
// decide between mounting the SPA and a real 404. /unsubscribe takes no
// subpath (nor does /plan, the AI planner chat); /vote and /payment require one; /admin owns its whole subtree
// including the bare form. They are locale-prefixed like everything else —
// the SPA's BrowserRouter takes the prefix as its basename (see App.js).
export const SPA_EXACT = new Set(['unsubscribe', 'plan']);
export const SPA_NESTED = new Set(['vote', 'payment']);

function firstSegment(pathname) {
  return pathname.split('/').filter(Boolean)[0] ?? '';
}

/** '/de/about' -> { locale: 'de', pathname: '/about' }; '/about' -> en. */
export function splitLocale(pathname) {
  const seg = firstSegment(pathname);
  if (seg !== DEFAULT_LOCALE && SUPPORTED_LOCALES.includes(seg)) {
    const rest = pathname.slice(seg.length + 1) || '/';
    return { locale: seg, pathname: rest };
  }
  return { locale: DEFAULT_LOCALE, pathname };
}

// The locale of the render in progress on the SERVER, bound by LocaleProvider
// (i18n/index.js) as it renders. Without it, server-side currentLocale() fell
// back to English while the client resolved 'de' from the URL — every
// formatAmount/formatDate in the initial HTML disagreed with hydration
// (€109 vs 109 €), React threw #418 and client-rendered the whole tree from
// scratch, wiping DOM that non-React scripts (the consent banner) had already
// injected. Module state is safe here: the legacy tree renders synchronously
// under its provider, and the browser never reads it (URL wins there).
let ssrLocale = DEFAULT_LOCALE;

/** Called by LocaleProvider during render; a no-op signal for everything else. */
export function bindSsrLocale(locale) {
  ssrLocale = locale || DEFAULT_LOCALE;
}

/** Locale of the current page: URL prefix in the browser, the provider-bound locale during SSR. */
export function currentLocale() {
  if (typeof window === 'undefined') {
    return ssrLocale;
  }
  // Tests stub window.location with partial objects; no pathname = default.
  const pathname = window.location?.pathname;
  return pathname ? splitLocale(pathname).locale : DEFAULT_LOCALE;
}

/**
 * Request-body fragment carrying the current locale — `{locale: 'de'}`, or {}
 * for the default: the backend stores null for English, so the field is only
 * sent when it says something.
 */
export function localeField() {
  const locale = currentLocale();
  return locale === DEFAULT_LOCALE ? {} : { locale };
}

/**
 * Append the current locale to an API URL (`?locale=de` / `&locale=de`): the
 * backend resolves translatable content in place for it. Every public read
 * sends it, English included, so the response never carries the raw
 * translations map meant for the admin.
 */
export function withLocaleParam(url) {
  return `${url}${url.includes('?') ? '&' : '?'}locale=${currentLocale()}`;
}

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
