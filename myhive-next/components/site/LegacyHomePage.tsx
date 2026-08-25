'use client';

// Client boundary for the canonical CRA homepage. legacy-src/pages/HomePage.js
// carries no 'use client' directive of its own, so a Server Component cannot
// render it directly — this file is that directive. Everything below still
// server-renders into the initial HTML; only the hooks hydrate.
import HomePage from '../../legacy-src/pages/HomePage';
import { localizePath, useLocale } from '../../legacy-src/i18n';
import { VOTE_FLOW_PATH } from '../../lib/routes';

// The SSR page's CTAs hand the vote funnel to /vote/new rather than opening the
// setup modal in place — see lib/routes.ts and the HomePage prop comment. The
// locale prefix has to be baked in here: it is a hard navigation, and
// LegacyRouter only localizes react-router links.

export interface LegacyActivity {
  id: string;
  slug: string;
  name: string;
}

export default function LegacyHomePage({ activities }: { activities: LegacyActivity[] }) {
  const locale = useLocale();
  return (
    <HomePage featuredActivities={activities} voteHref={localizePath(VOTE_FLOW_PATH, locale)} />
  );
}
