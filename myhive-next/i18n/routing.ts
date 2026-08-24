import { defineRouting } from 'next-intl/routing';
// Single source for the locale list: the legacy i18n module (synced from
// myhive-react-app), so the CRA-side link helpers and Next routing can never
// disagree about which locales exist.
import { SUPPORTED_LOCALES, DEFAULT_LOCALE } from '../legacy-src/i18n/routes';

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
});
