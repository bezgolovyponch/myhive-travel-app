// Shared SEO constants/helpers for the public (SSR) pages.
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
