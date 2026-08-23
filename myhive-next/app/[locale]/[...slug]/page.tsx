import { notFound } from 'next/navigation';
import LegacyAppShim from '@/components/LegacyAppShim';
// The SPA-owned URL sets live with the locale helpers next to the link
// localization so route resolution and links can never disagree.
import { SPA_EXACT, SPA_NESTED } from '../../../legacy-src/i18n/routes';

// Required catch-all: Ф1 serves the public URLs as Server Components (they win
// route resolution); everything else — admin, vote, payment, and any SPA-state
// deep link — still mounts the whole legacy SPA, which does its own client-side
// routing (BrowserRouter reads the real URL, taking the locale prefix as its
// basename). Client-side navigation INSIDE the SPA can still reach public URLs
// (react-router owns history once mounted); only fresh page loads hit the SSR
// pages.
// The SPA legitimately owns only the service flows; every public URL has an
// SSR page that wins route resolution. Anything else is a real 404 — mounting
// the SPA unconditionally turned unknown URLs into soft 404s (HTTP 200).
// Bare react-router routes: the SPA renders real UI at the prefix itself.
// /unsubscribe is reached from reminder emails — EmailService builds
// `frontendUrl + "/unsubscribe?token=..."` — so a 404 here silently breaks every
// unsubscribe link, which is a compliance problem and not merely a dead page.
// It takes no subpath, so anything deeper is still a real 404.
// /admin is the exception among the nested prefixes — it owns /admin/*
// including the bare form.
export default async function CatchAllPage({
  params,
}: {
  params: Promise<{ locale: string; slug: string[] }>;
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
