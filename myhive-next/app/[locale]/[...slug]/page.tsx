import { notFound, redirect } from 'next/navigation';
import LegacyAppShim from '@/components/LegacyAppShim';
// The SPA-owned URL sets live with the locale helpers so link localization
// (which must NOT prefix these URLs) and route resolution can never disagree.
import { SPA_EXACT, SPA_NESTED, DEFAULT_LOCALE } from '../../../legacy-src/i18n/routes';

// Required catch-all: Ф1 serves the public URLs as Server Components (they win
// route resolution); everything else — admin, vote, payment, and any SPA-state
// deep link — still mounts the whole legacy SPA, which does its own client-side
// routing (BrowserRouter reads the real URL). Client-side navigation INSIDE the
// SPA can still reach public URLs (react-router owns history once mounted);
// only fresh page loads hit the SSR pages.
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

function queryString(sp: Record<string, string | string[] | undefined>) {
  const qs = new URLSearchParams();
  for (const [key, value] of Object.entries(sp)) {
    if (value == null) continue;
    for (const v of Array.isArray(value) ? value : [value]) {
      qs.append(key, v);
    }
  }
  const s = qs.toString();
  return s ? `?${s}` : '';
}

export default async function CatchAllPage({
  params,
  searchParams,
}: {
  params: Promise<{ locale: string; slug: string[] }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { locale, slug } = await params;
  const ownsUrl =
    slug[0] === 'admin' ||
    (SPA_EXACT.has(slug[0]) && slug.length === 1) ||
    (SPA_NESTED.has(slug[0]) && slug.length > 1);
  if (!ownsUrl) {
    notFound();
  }
  // The SPA's BrowserRouter reads the real URL and has no locale-prefixed
  // routes — a /de/admin deep link would render blank chrome. These flows are
  // English-only by design, so send them to their real URL, query intact
  // (/unsubscribe?token=... must survive).
  if (locale !== DEFAULT_LOCALE) {
    redirect(`/${slug.join('/')}${queryString(await searchParams)}`);
  }
  return <LegacyAppShim />;
}
