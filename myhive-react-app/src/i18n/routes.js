// Locale-aware URL helpers, hook-free so both server code (the Next catch-all)
// and client code (LegacyRouter, components) can import them.

export const SUPPORTED_LOCALES = ['en', 'de'];
export const DEFAULT_LOCALE = 'en';

// URLs the legacy SPA owns (client-only flows), used by the Next catch-all to
// decide between mounting the SPA and a real 404. /unsubscribe takes no
// subpath; /vote and /payment require one; /admin owns its whole subtree
// including the bare form. They are locale-prefixed like everything else —
// the SPA's BrowserRouter takes the prefix as its basename (see App.js).
export const SPA_EXACT = new Set(['unsubscribe']);
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

/** Locale of the current page, read from the URL prefix; the default outside a browser (tests, SSR). */
export function currentLocale() {
  // Tests stub window.location with partial objects; no pathname = default.
  const pathname = typeof window === 'undefined' ? null : window.location?.pathname;
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

/** Prefix an absolute-relative path for a locale. '/'+'de' -> '/de'. */
export function localizePath(path, locale) {
  if (!locale || locale === DEFAULT_LOCALE) return path;
  return path === '/' ? `/${locale}` : `/${locale}${path}`;
}

/**
 * Locale-prefix an href unless it is external or already prefixed.
 * Accepts hrefs with search/hash.
 */
export function localizeHref(href, locale) {
  if (!locale || locale === DEFAULT_LOCALE) return href;
  if (!href.startsWith('/')) return href;
  const pathEnd = href.length - href.replace(/^[^?#]*/, '').length;
  const path = href.slice(0, pathEnd) || '/';
  const rest = href.slice(pathEnd);
  const seg = firstSegment(path);
  if (SUPPORTED_LOCALES.includes(seg) && seg !== DEFAULT_LOCALE) return href;
  return localizePath(path, locale) + rest;
}
