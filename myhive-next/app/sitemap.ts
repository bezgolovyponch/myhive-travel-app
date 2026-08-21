import type { MetadataRoute } from 'next';
import { api } from '../lib/api';
import { SITE_URL, canonical } from '../lib/seo';
import { routing } from '../i18n/routing';

// Single sitemap owner (spec §2): replaces the backend SitemapController at
// cutover. Fetches the same catalog/blog data the pages render, so URLs and
// content can't drift. Service flows (/admin, /vote, /payment) are excluded
// and disallowed in robots.
export const revalidate = 3600;

// One entry per locale variant, each carrying the full hreflang set (Google
// wants every URL it should index listed, not only the default-locale one).
// Legal pages are NOT localized (canonical points at the English document) and
// stay single English entries below.
function pushLocalized(
  urls: MetadataRoute.Sitemap,
  path: string,
  priority: number,
  lastModified?: string
) {
  const languages = Object.fromEntries(
    routing.locales.map((l) => [l, canonical(path, l)])
  );
  for (const locale of routing.locales) {
    urls.push({
      url: canonical(path, locale),
      priority,
      ...(lastModified ? { lastModified } : {}),
      alternates: { languages },
    });
  }
}

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const urls: MetadataRoute.Sitemap = [
    { url: `${SITE_URL}/terms`, priority: 0.2 },
    { url: `${SITE_URL}/privacy-policy`, priority: 0.2 },
    { url: `${SITE_URL}/cookie-policy`, priority: 0.2 },
    { url: `${SITE_URL}/refund-policy`, priority: 0.2 },
  ];
  pushLocalized(urls, '/', 1);
  pushLocalized(urls, '/blog', 0.8);
  pushLocalized(urls, '/about', 0.5);
  pushLocalized(urls, '/contact', 0.5);

  try {
    const destinations = (await api.getDestinations()) ?? [];
    for (const dest of destinations) {
      // Per-record SEO gate: unready records are excluded, and a destination
      // that is not indexable excludes ALL of its children (parent rule).
      if (!dest.seoIndexable) continue;
      pushLocalized(urls, `/destination/${dest.slug}`, 0.9);
      const [activities, packages] = await Promise.all([
        api.getActivities(dest.id).catch(() => null),
        api.getPackages(dest.id).catch(() => null),
      ]);
      for (const a of activities ?? []) {
        if (!a.seoIndexable) continue;
        pushLocalized(urls, `/destination/${dest.slug}/activity/${a.slug}`, 0.7);
      }
      for (const p of packages ?? []) {
        if (!p.seoIndexable) continue;
        pushLocalized(urls, `/destination/${dest.slug}/package/${p.slug}`, 0.6);
      }
    }
    const posts = (await api.getBlogPosts()) ?? [];
    for (const post of posts.filter((p) => p.seoIndexable)) {
      pushLocalized(urls, `/blog/${post.slug}`, 0.7, post.date || post.createdAt || undefined);
    }
  } catch {
    // Backend unreachable: serve the static core rather than erroring —
    // the hourly revalidate will fill in the catalog when it's back.
  }

  return urls;
}
