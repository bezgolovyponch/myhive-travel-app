import { notFound } from 'next/navigation';
import LegacyAppShim from '../../components/LegacyAppShim';

// Required catch-all: Ф1 serves the public URLs as Server Components (they win
// route resolution); everything else — admin, vote, payment, and any SPA-state
// deep link — still mounts the whole legacy SPA, which does its own client-side
// routing (BrowserRouter reads the real URL). Client-side navigation INSIDE the
// SPA can still reach public URLs (react-router owns history once mounted);
// only fresh page loads hit the SSR pages.
// The SPA legitimately owns only the service flows; every public URL has an
// SSR page that wins route resolution. Anything else is a real 404 — mounting
// the SPA unconditionally turned unknown URLs into soft 404s (HTTP 200).
const SPA_PREFIXES = new Set(['admin', 'vote', 'payment']);

export default async function CatchAllPage({
  params,
}: {
  params: Promise<{ slug: string[] }>;
}) {
  const { slug } = await params;
  if (!SPA_PREFIXES.has(slug[0])) {
    notFound();
  }
  return <LegacyAppShim />;
}
