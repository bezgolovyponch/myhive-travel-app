import type { MetadataRoute } from 'next';

// Indexing is opt-in via ALLOW_INDEXING=true (read at BUILD time): previews and
// misconfigured deploys serve Disallow-all by default, and promoting this app
// to the canonical domain can't accidentally deindex the site — the production
// service just sets the env var. Replaces the static public/robots.txt, which
// would have shipped Disallow-all to production unless someone remembered to
// delete it (SEO plan v3: SSR migration exists to GET indexed).
export default function robots(): MetadataRoute.Robots {
  if (process.env.ALLOW_INDEXING !== 'true') {
    return { rules: { userAgent: '*', disallow: '/' } };
  }
  // Production rules, per SEO план v3 + the CRA robots.txt it replaces:
  // public pages open; service flows (admin, voting steps, payment) closed.
  // Next owns the sitemap (app/sitemap.ts) — single implementation, spec §2.
  const site = process.env.NEXT_PUBLIC_SITE_URL || 'https://www.trivlu.com';
  return {
    rules: { userAgent: '*', disallow: ['/admin', '/vote', '/payment'] },
    sitemap: `${site}/sitemap.xml`,
  };
}
