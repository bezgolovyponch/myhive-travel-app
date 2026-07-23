import type { MetadataRoute } from 'next';
import { api } from '../lib/api';
import { SITE_URL } from '../lib/seo';

// Single sitemap owner (spec §2): replaces the backend SitemapController at
// cutover. Fetches the same catalog/blog data the pages render, so URLs and
// content can't drift. Service flows (/admin, /vote, /payment) are excluded
// and disallowed in robots.
export const revalidate = 3600;

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const urls: MetadataRoute.Sitemap = [
    { url: `${SITE_URL}/`, priority: 1 },
    { url: `${SITE_URL}/blog`, priority: 0.8 },
    { url: `${SITE_URL}/about`, priority: 0.5 },
    { url: `${SITE_URL}/contact`, priority: 0.5 },
    { url: `${SITE_URL}/terms`, priority: 0.2 },
    { url: `${SITE_URL}/privacy-policy`, priority: 0.2 },
    { url: `${SITE_URL}/cookie-policy`, priority: 0.2 },
    { url: `${SITE_URL}/refund-policy`, priority: 0.2 },
  ];

  try {
    const destinations = (await api.getDestinations()) ?? [];
    for (const dest of destinations) {
      // Per-record SEO gate: unready records are excluded, and a destination
      // that is not indexable excludes ALL of its children (parent rule).
      if (!dest.seoIndexable) continue;
      urls.push({ url: `${SITE_URL}/destination/${dest.slug}`, priority: 0.9 });
      const [activities, packages] = await Promise.all([
        api.getActivities(dest.id).catch(() => null),
        api.getPackages(dest.id).catch(() => null),
      ]);
      for (const a of activities ?? []) {
        if (!a.seoIndexable) continue;
        urls.push({ url: `${SITE_URL}/destination/${dest.slug}/activity/${a.slug}`, priority: 0.7 });
      }
      for (const p of packages ?? []) {
        if (!p.seoIndexable) continue;
        urls.push({ url: `${SITE_URL}/destination/${dest.slug}/package/${p.slug}`, priority: 0.6 });
      }
    }
    const posts = (await api.getBlogPosts()) ?? [];
    for (const post of posts.filter((p) => p.seoIndexable)) {
      urls.push({
        url: `${SITE_URL}/blog/${post.slug}`,
        priority: 0.7,
        lastModified: post.publishedAt || post.createdAt || undefined,
      });
    }
  } catch {
    // Backend unreachable: serve the static core rather than erroring —
    // the hourly revalidate will fill in the catalog when it's back.
  }

  return urls;
}
