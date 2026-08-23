// Shared SEO constants/helpers for the public (SSR) pages.
import type { Metadata } from 'next';
import { routing } from '@/i18n/routing';
import { localizePath } from '../legacy-src/i18n/routes';

export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL || 'https://www.trivlu.com';
export const WHATSAPP_URL = 'https://wa.me/420795518597';

// Interim single-city default (Ф3 makes destination context path-driven).
export const DEFAULT_DESTINATION_SLUG = 'prague';

export function canonical(path: string, locale: string = routing.defaultLocale) {
  return `${SITE_URL}${localizePath(path, locale)}`;
}

/** BreadcrumbList JSON-LD from [label, path] pairs (path absolute-relative). */
export function breadcrumbJsonLd(items: [string, string][], locale: string = routing.defaultLocale) {
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: items.map(([name, path], i) => ({
      '@type': 'ListItem',
      position: i + 1,
      name,
      item: canonical(path, locale),
    })),
  };
}

export function formatPricePerPerson(price: number) {
  return `€${Math.round(price)} / person`;
}

interface PageMeta {
  title: string;
  description: string;
  /** Absolute-relative path WITHOUT locale prefix, e.g. '/about'. */
  path: string;
  /** Locale of this render; localizes the canonical URL. */
  locale?: string;
  /** Absolute image URL; falls back to the brand og-image. */
  image?: string;
  ogType?: 'website' | 'article';
  /** Emits robots noindex,follow (per-record SEO gate). */
  noindex?: boolean;
  /**
   * false for pages whose CONTENT is not localized yet (legal documents): the
   * canonical then points at the English URL so /de/terms never competes with
   * /terms in the index, and no hreflang set is emitted.
   */
  translated?: boolean;
}

/** Uniform metadata: Next merges `openGraph` shallowly (a page-level object
 *  REPLACES the root one), so every page must emit a complete OG set or lose
 *  og:image/og:type. This is the single place that guarantees completeness. */
export function pageMetadata({
  title,
  description,
  path,
  locale = routing.defaultLocale,
  image,
  ogType = 'website',
  noindex = false,
  translated = true,
}: PageMeta): Metadata {
  const url = canonical(path, translated ? locale : routing.defaultLocale);
  return {
    title,
    description,
    alternates: {
      canonical: url,
      // hreflang: every locale variant plus x-default on the English URL.
      // Only for pages that actually exist in every locale.
      ...(translated
        ? {
            languages: {
              ...Object.fromEntries(
                routing.locales.map((l) => [l, canonical(path, l)])
              ),
              'x-default': canonical(path),
            },
          }
        : {}),
    },
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
