// Locale-aware URL helpers, hook-free so both server code (the Next catch-all)
// and client code (LegacyRouter, components) can import them.

export const SUPPORTED_LOCALES = ['en', 'de'];
export const DEFAULT_LOCALE = 'en';

// URLs the legacy SPA owns (client-only flows). They are never locale-prefixed:
// the SPA's BrowserRouter reads the real URL and knows nothing about locales.
// /unsubscribe takes no subpath; /vote and /payment require one; /admin owns
// its whole subtree including the bare form. Mirrored by the Next catch-all,
// which decides between mounting the SPA and a real 404.
export const SPA_EXACT = new Set(['unsubscribe']);
export const SPA_NESTED = new Set(['vote', 'payment']);
export const SPA_PREFIXES = new Set(['admin', 'vote', 'payment', 'unsubscribe']);

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

/** Prefix an absolute-relative path for a locale. '/'+'de' -> '/de'. */
export function localizePath(path, locale) {
  if (!locale || locale === DEFAULT_LOCALE) return path;
  return path === '/' ? `/${locale}` : `/${locale}${path}`;
}

/**
 * Locale-prefix an href unless it is external, already prefixed, or an
 * SPA-owned URL (those flows are English-only and unprefixed by design).
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
  if (SPA_PREFIXES.has(seg)) return href;
  return localizePath(path, locale) + rest;
}
