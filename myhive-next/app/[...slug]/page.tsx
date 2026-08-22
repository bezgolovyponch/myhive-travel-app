import { notFound } from 'next/navigation';
import LegacyAppShim from '../../components/LegacyAppShim';

// Required catch-all: Ф1 serves the public URLs as Server Components (they win
// route resolution); everything else — admin, payment, and any SPA-state deep
// link — still mounts the whole legacy SPA, which does its own client-side
// routing (BrowserRouter reads the real URL). Client-side navigation INSIDE the
// SPA can still reach public URLs (react-router owns history once mounted);
// only fresh page loads hit the SSR pages. /vote now has its own SSR shell
// (app/vote/[shareToken]) for real OG tags on share links, so it's no longer
// routed through this catch-all at all.
// The SPA legitimately owns only the service flows; every public URL has an
// SSR page that wins route resolution. Anything else is a real 404 — mounting
// the SPA unconditionally turned unknown URLs into soft 404s (HTTP 200).
// Bare react-router routes: the SPA renders real UI at the prefix itself.
// /unsubscribe is reached from reminder emails — EmailService builds
// `frontendUrl + "/unsubscribe?token=..."` — so a 404 here silently breaks every
// unsubscribe link, which is a compliance problem and not merely a dead page.
// It takes no subpath, so anything deeper is still a real 404.
const SPA_EXACT = new Set(['unsubscribe']);
// Prefixes whose bare form is NOT a react-router route: the SPA would render
// empty chrome at HTTP 200 (a soft 404), so a subpath is required. /admin is the
// exception — it owns /admin/* including the bare form.
const SPA_NESTED = new Set(['payment']);

export default async function CatchAllPage({
  params,
}: {
  params: Promise<{ slug: string[] }>;
}) {
  const { slug } = await params;
  const ownsUrl =
    slug[0] === 'admin' ||
    (SPA_EXACT.has(slug[0]) && slug.length === 1) ||
    (SPA_NESTED.has(slug[0]) && slug.length > 1);
  if (!ownsUrl) {
    notFound();
  }
  return <LegacyAppShim />;
}
