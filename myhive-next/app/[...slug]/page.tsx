import LegacyAppShim from '../../components/LegacyAppShim';

// Required catch-all: Ф1 serves the public URLs as Server Components (they win
// route resolution); everything else — admin, vote, payment, and any SPA-state
// deep link — still mounts the whole legacy SPA, which does its own client-side
// routing (BrowserRouter reads the real URL). Client-side navigation INSIDE the
// SPA can still reach public URLs (react-router owns history once mounted);
// only fresh page loads hit the SSR pages.
export default function CatchAllPage() {
  return <LegacyAppShim />;
}
