'use client';

// Client boundary for the canonical CRA page. legacy-src carries no 'use client'
// directive of its own, so a Server Component cannot render it directly. The page
// still server-renders into the initial HTML; only its hooks hydrate.
import CookiePolicyPage from '../../../legacy-src/pages/CookiePolicyPage';

export default function LegacyCookie() {
  return <CookiePolicyPage />;
}
