// SSR package detail — a thin server shell: metadata, JSON-LD, the record fetch
// and the city-match guard. The markup is the canonical CRA page
// (legacy-src/pages/PackageDetailPage.js), so this route cannot drift from what
// the SPA renders, and its Add-to-trip CTA is the real one rather than a link.
// Record fields arrive already localized for `locale` (backend translations
// column, English fallback per field).
import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import { api } from '@/lib/api';
import { breadcrumbJsonLd, pageMetadata, jsonLd } from '@/lib/seo';
import LegacyPackageDetail from '@/components/site/legacy/LegacyPackageDetail';

export const revalidate = 3600;

type Props = { params: Promise<{ locale: string; slug: string; pslug: string }> };

function capitalizeFirst(s: string) {
  if (!s) return '';
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function destinationNameFromSlug(slug: string) {
  return slug
    .split('-')
    .map((part) => capitalizeFirst(part))
    .join(' ');
}

function truncate(text: string, limit = 160) {
  if (text.length <= limit) return text;
  return `${text.slice(0, limit - 1).trimEnd()}…`;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, slug, pslug } = await params;
  const t = await getTranslations({ locale, namespace: 'meta.package' });
  const [pkg, dest] = await Promise.all([
    api.getPackageBySlug(pslug, locale),
    api.getDestinationBySlug(slug, locale),
  ]);
  if (!pkg) {
    return { title: t('notFound') };
  }

  const destinationName = dest?.name || destinationNameFromSlug(slug);
  return pageMetadata({
    title: t('title', { name: pkg.name, destination: destinationName }),
    description: pkg.description
      ? truncate(pkg.description)
      : t('fallbackDescription', { name: pkg.name, destination: destinationName }),
    path: `/destination/${slug}/package/${pkg.slug}`,
    locale,
    image: pkg.imageUrl || undefined,
    noindex: !(pkg.seoIndexable && dest?.seoIndexable),
  });
}

export default async function Page({ params }: Props) {
  const { locale, slug, pslug } = await params;
  setRequestLocale(locale);
  const pkg = await api.getPackageBySlug(pslug, locale);
  if (!pkg) {
    notFound();
  }

  // City-match guard (spec §6): reject a mismatched destination slug so the same
  // package is not reachable under multiple cities.
  if (pkg.destinationSlug && pkg.destinationSlug !== slug) {
    notFound();
  }

  const t = await getTranslations({ locale, namespace: 'common' });
  // destinationName rides on the record (localized); the slug is the fallback.
  const destinationName = pkg.destinationName || destinationNameFromSlug(slug);
  const breadcrumbLd = breadcrumbJsonLd(
    [
      [t('home'), '/'],
      [destinationName, `/destination/${slug}`],
      [pkg.name, `/destination/${slug}/package/${pkg.slug}`],
    ],
    locale
  );

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonLd(breadcrumbLd) }}
      />
      <LegacyPackageDetail pkg={pkg} />
    </>
  );
}
