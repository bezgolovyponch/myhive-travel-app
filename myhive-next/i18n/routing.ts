import { defineRouting } from 'next-intl/routing';
// Single source for the locale list: the legacy i18n module (synced from
// myhive-react-app), so the CRA-side link helpers and Next routing can never
// disagree about which locales exist.
import { SUPPORTED_LOCALES, DEFAULT_LOCALE, LOCALIZED_PATHNAMES } from '../legacy-src/i18n/routes';

export const routing = defineRouting({
  locales: SUPPORTED_LOCALES,
  defaultLocale: DEFAULT_LOCALE,
  // English stays on the bare URLs it has always had; other locales get a
  // prefix (/de/...). Nothing about the existing EN indexation changes.
  localePrefix: 'as-needed',
  // No Accept-Language redirects and no NEXT_LOCALE cookie: locale is chosen
  // by the URL alone. Redirect-by-header would fragment CDN caching and
  // surprise crawlers; users reach /de via links, hreflang and the switcher.
  localeDetection: false,
  localeCookie: false,
  // German public segments (/de/jga-prag/aktivitaeten/[slug]) are rewritten by
  // the middleware onto the EN-named route folders. The table is owned by the
  // legacy i18n module so CRA links and Next routing cannot disagree.
  // Verified in the next-intl docs: (a) pathnames may be partial - routes not
  // listed pass through as-is and only get the locale prefix; (b) a request
  // for /de/<internal pathname> gets a 307 redirect to the localized pathname
  // (/de/jga-prag), and with localeDetection: false an unprefixed German path
  // like /jga-prag is not rewritten; observed live, next-intl answers it with a
  // 307 to /prague-stag-do (acceptable).
  pathnames: LOCALIZED_PATHNAMES,
});
