// Shared SEO constants/helpers for the public (SSR) pages.
import type { Metadata } from 'next';

export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL || 'https://www.trivlu.com';
export const WHATSAPP_URL = 'https://wa.me/420795518597';

// Interim single-city default (Ф3 makes destination context path-driven).
export const DEFAULT_DESTINATION_SLUG = 'prague';

export function canonical(path: string) {
  return `${SITE_URL}${path}`;
}

/** BreadcrumbList JSON-LD from [label, path] pairs (path absolute-relative). */
export function breadcrumbJsonLd(items: [string, string][]) {
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: items.map(([name, path], i) => ({
      '@type': 'ListItem',
      position: i + 1,
      name,
      item: canonical(path),
    })),
  };
}

export function formatPricePerPerson(price: number) {
  return `€${Math.round(price)} / person`;
}

interface PageMeta {
  title: string;
  description: string;
  /** Absolute-relative path, e.g. '/about'. */
  path: string;
  /** Absolute image URL; falls back to the brand og-image. */
  image?: string;
  ogType?: 'website' | 'article';
  /** Emits robots noindex,follow (per-record SEO gate). */
  noindex?: boolean;
}

/** Uniform metadata: Next merges `openGraph` shallowly (a page-level object
 *  REPLACES the root one), so every page must emit a complete OG set or lose
 *  og:image/og:type. This is the single place that guarantees completeness. */
export function pageMetadata({ title, description, path, image, ogType = 'website', noindex = false }: PageMeta): Metadata {
  const url = canonical(path);
  return {
    title,
    description,
    alternates: { canonical: url },
    ...(noindex ? { robots: { index: false, follow: true } } : {}),
    openGraph: {
      title,
      description,
      url,
      type: ogType,
      images: image
        ? [{ url: image }]
        : [{ url: `${SITE_URL}/og-image.png`, width: 1000, height: 1000, type: 'image/png' }],
    },
  };
}

/** JSON-LD for <script dangerouslySetInnerHTML>: '<' must be escaped so
 *  backend-controlled strings can't close the script tag (XSS). */
export function jsonLd(data: unknown): string {
  return JSON.stringify(data).replace(/</g, '\\u003c');
}
