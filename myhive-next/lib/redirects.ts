import { DESTINATION_HUBS, SUPPORTED_LOCALES, localizePath } from '../legacy-src/i18n/routes';

export interface RedirectRule {
  source: string;
  destination: string;
  permanent: true;
}

// 308s from the pre-2026-10 URL scheme (spec: docs/superpowers/specs/2026-10-15-seo-prague-urls-phase1-design.md).
// Rules are generated from the same table the links use, scoped to hub
// destinations only, so cities on /destination/{slug} are never touched.
export function legacyUrlRedirects(): RedirectRule[] {
  const rules: RedirectRule[] = [];
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
  // Old URLs that carry the explicit /en/ prefix go straight to the bare English
  // URL: next-intl otherwise answers /en/... with a 307 to the bare URL first,
  // which would make two hops.
  for (const [slug, hub] of Object.entries(DESTINATION_HUBS)) {
    rule(`/en/destination/${slug}`, hub);
    rule(`/en/destination/${slug}/activity/:slug`, `${hub}/activities/:slug`);
    rule(`/en/destination/${slug}/package/:slug`, `${hub}/packages/:slug`);
  }
  rule('/en/blog', '/ideas');
  rule('/en/blog/how-to-plan-a-stress-free-group-trip', '/ideas/how-to-plan-a-stag-do');
  return rules;
}
