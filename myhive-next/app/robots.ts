import type { MetadataRoute } from 'next';
import { routing } from '../i18n/routing';

// Indexing is opt-in via ALLOW_INDEXING=true (read at BUILD time): previews and
// misconfigured deploys serve Disallow-all by default, and promoting this app
// to the canonical domain can't accidentally deindex the site — the production
// service just sets the env var. Replaces the static public/robots.txt, which
// would have shipped Disallow-all to production unless someone remembered to
// delete it (SEO plan v3: SSR migration exists to GET indexed).

// AI crawlers blocked outright. Decision 2026-10-18 (SEO manager): assistants
// must be able to read and cite the site, so every AI bot is open except
// Bytespider. The training opt-outs (GPTBot, CCBot, Google-Extended,
// Applebot-Extended, ClaudeBot, anthropic-ai, Meta-ExternalAgent) were dropped
// on purpose. The Cloudflare "Block AI bots" switch stays off (deploy checklist).
const BLOCKED_BOTS = ['Bytespider'];

export default function robots(): MetadataRoute.Robots {
  if (process.env.ALLOW_INDEXING !== 'true') {
    return { rules: { userAgent: '*', disallow: '/' } };
  }
  // Production rules, per SEO план v3 + the CRA robots.txt it replaces:
  // public pages open; service flows (admin, voting steps, payment) closed.
  // Next owns the sitemap (app/sitemap.ts) — single implementation, spec §2.
  const site = process.env.NEXT_PUBLIC_SITE_URL || 'https://www.trivlu.com';
  // Service flows exist under every locale prefix too (/de/vote/...).
  const serviceFlows = ['/admin', '/vote', '/payment'];
  const prefixes = routing.locales
    .filter((l) => l !== routing.defaultLocale)
    .map((l) => `/${l}`);
  const disallow = serviceFlows.flatMap((p) => [p, ...prefixes.map((pre) => pre + p)]);
  return {
    rules: [
      { userAgent: '*', disallow },
      { userAgent: BLOCKED_BOTS, disallow: '/' },
    ],
    sitemap: `${site}/sitemap.xml`,
  };
}
