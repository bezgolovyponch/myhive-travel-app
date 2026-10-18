import { DESTINATION_HUBS, SUPPORTED_LOCALES, activityPath, localizePath } from '../legacy-src/i18n/routes';
import { loadRedirectMap, slugOf, type MapRow } from './redirect-map';

export interface RedirectRule {
  source: string;
  destination: string;
  permanent: true;
}

const ACTIVITY_PATH = /^\/destination\/([^/]+)\/activity\/[^/]+$/;

// 308s from the pre-2026-10 URL scheme (spec: docs/superpowers/specs/2026-10-15-seo-prague-urls-phase1-design.md, section 3).
// Three groups, in this order: (1) one exact rule per 301 row of seo/redirect-map.csv
// (EN, DE and the explicit /en/ variant); (2) pattern fallbacks for activities and
// packages that are not in the map, generated from the same hub table the links use,
// scoped to hub destinations only so cities on /destination/{slug} are never touched;
// (3) guards for activities whose slug was renamed: a stale cart or an
// old link built from the new hub with the old slug lands on the new page.
// No generic /blog/:slug rule on purpose: redirects run before middleware and
// would swallow the 410s (lib/gone.ts). 410 and landing rows produce no rule.
export function legacyUrlRedirects(rows: MapRow[] = loadRedirectMap()): RedirectRule[] {
  const rules: RedirectRule[] = [];
  const rule = (source: string, destination: string) => rules.push({ source, destination, permanent: true });

  const moved = rows.filter((row) => row.note === '301');
  for (const row of moved) {
    rule(row.oldEn, row.newEn);
    rule(row.oldDe, row.newDe);
    // next-intl otherwise answers /en/... with a 307 to the bare URL first,
    // which would make two hops.
    rule(`/en${row.oldEn}`, row.newEn);
  }

  for (const [slug, hub] of Object.entries(DESTINATION_HUBS)) {
    for (const [kind, target] of [['activity', 'activities'], ['package', 'packages']]) {
      rule(`/destination/${slug}/${kind}/:slug`, `${hub}/${target}/:slug`);
      rule(localizePath(`/destination/${slug}/${kind}/:slug`, 'de'), localizePath(`${hub}/${target}/:slug`, 'de'));
      rule(`/en/destination/${slug}/${kind}/:slug`, `${hub}/${target}/:slug`);
    }
  }

  for (const row of moved) {
    const match = ACTIVITY_PATH.exec(row.oldEn);
    if (row.type !== 'activity' || !match || slugOf(row.oldEn) === slugOf(row.newEn)) {
      continue;
    }
    for (const locale of SUPPORTED_LOCALES) {
      const destination = locale === 'de' ? row.newDe : row.newEn;
      rule(localizePath(activityPath(match[1], slugOf(row.oldEn)), locale), destination);
    }
  }
  return rules;
}
