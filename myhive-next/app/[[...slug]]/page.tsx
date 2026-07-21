import LegacyAppShim from '../../components/LegacyAppShim';

// Optional catch-all: in Ф0 the legacy SPA owns every route and does its own
// client-side routing (BrowserRouter reads the real URL). Ф1 adds Server
// Component pages for public URLs and narrows this to (legacy) subtrees.
export default function CatchAllPage() {
  return <LegacyAppShim />;
}
