import { getRequestConfig } from 'next-intl/server';
import { hasLocale } from 'next-intl';
import { routing } from './routing';

// English is the fallback for untranslated keys: translation lands
// progressively (legal pages, new features), and a missing key must render the
// English string, not a bare key path. Mirrors the en-fallback inside the
// legacy useT hook, so server metadata and client markup degrade identically.
function withEnglishFallback(
  en: Record<string, unknown>,
  overrides: Record<string, unknown>
): Record<string, unknown> {
  const merged: Record<string, unknown> = { ...en };
  for (const [key, value] of Object.entries(overrides)) {
    const base = merged[key];
    merged[key] =
      value && typeof value === 'object' && !Array.isArray(value) &&
      base && typeof base === 'object' && !Array.isArray(base)
        ? withEnglishFallback(
            base as Record<string, unknown>,
            value as Record<string, unknown>
          )
        : value;
  }
  return merged;
}

export default getRequestConfig(async ({ requestLocale }) => {
  const requested = await requestLocale;
  const locale = hasLocale(routing.locales, requested)
    ? requested
    : routing.defaultLocale;

  const en = (await import('../legacy-src/i18n/messages/en.json')).default;
  const messages =
    locale === routing.defaultLocale
      ? en
      : withEnglishFallback(
          en,
          (await import(`../legacy-src/i18n/messages/${locale}.json`)).default
        );

  return { locale, messages };
});
