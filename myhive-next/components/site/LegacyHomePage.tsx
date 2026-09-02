'use client';

// Client boundary for the canonical CRA homepage. legacy-src/pages/HomePage.js
// carries no 'use client' directive of its own, so a Server Component cannot
// render it directly — this file is that directive. Everything below still
// server-renders into the initial HTML; only the hooks hydrate.
//
// No voteHref anymore: "Start Group Vote" opens the setup modal in place here
// too. The confirm survives leaving this server-rendered page because
// useStartGroupVote carries the setup through /vote/new's query string, which
// LegacyRouter turns into a real (locale-prefixed) page load.
import HomePage from '../../legacy-src/pages/HomePage';

export interface LegacyActivity {
  id: string;
  slug: string;
  name: string;
}

export default function LegacyHomePage({ activities }: { activities: LegacyActivity[] }) {
  return <HomePage featuredActivities={activities} />;
}
