import type { MetadataRoute } from 'next';

// Indexing is opt-in via ALLOW_INDEXING=true (read at BUILD time): previews and
// misconfigured deploys serve Disallow-all by default, and promoting this app
// to the canonical domain can't accidentally deindex the site — the production
// service just sets the env var. Replaces the static public/robots.txt, which
// would have shipped Disallow-all to production unless someone remembered to
// delete it (SEO plan v3: SSR migration exists to GET indexed).

// AI model-TRAINING crawlers, blocked outright. This replaces the Cloudflare
// "managed robots.txt" block disabled on 2026-08-18 (cutover checklist B5) —
// same opt-out, but the rules live in our code instead of being injected by a
// third party. Deliberately NOT listed: AI search/assistant fetchers
// (OAI-SearchBot, ChatGPT-User, PerplexityBot, Meta-ExternalFetcher…) — those
// cite the site and refer real visitors. Google-Extended and Applebot-Extended
// are control tokens rather than crawlers: listing them in robots.txt IS the
// documented way to opt out of Gemini/Apple AI training without touching
// Googlebot/Applebot search crawling.
const AI_TRAINING_BOTS = [
  'GPTBot', // OpenAI training
  'CCBot', // Common Crawl — feeds many training corpora
  'Google-Extended', // Gemini training opt-out token
  'Applebot-Extended', // Apple AI training opt-out token
  'ClaudeBot', // Anthropic crawler
  'anthropic-ai', // legacy Anthropic token
  'Bytespider', // ByteDance training crawler
  'Meta-ExternalAgent', // Meta AI training crawler
];

export default function robots(): MetadataRoute.Robots {
  if (process.env.ALLOW_INDEXING !== 'true') {
    return { rules: { userAgent: '*', disallow: '/' } };
  }
  // Production rules, per SEO план v3 + the CRA robots.txt it replaces:
  // public pages open; service flows (admin, voting steps, payment) closed.
  // Next owns the sitemap (app/sitemap.ts) — single implementation, spec §2.
  const site = process.env.NEXT_PUBLIC_SITE_URL || 'https://www.trivlu.com';
  return {
    rules: [
      { userAgent: '*', disallow: ['/admin', '/vote', '/payment'] },
      { userAgent: AI_TRAINING_BOTS, disallow: '/' },
    ],
    sitemap: `${site}/sitemap.xml`,
  };
}
