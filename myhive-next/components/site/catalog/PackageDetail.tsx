// SSR package detail (shared by the hub routes and /destination/[slug]) —
// a thin server shell: metadata, JSON-LD, the record fetch
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
import { packagePath, destinationPath } from '../../../legacy-src/i18n/routes';
import LegacyPackageDetail from '@/components/site/legacy/LegacyPackageDetail';

interface Args {
  destinationSlug: string;
  packageSlug: string;
  locale: string;
}

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

export async function packageMetadata({ destinationSlug, packageSlug, locale }: Args): Promise<Metadata> {
  const t = await getTranslations({ locale, namespace: 'meta.package' });
  const [pkg, dest] = await Promise.all([
    api.getPackageBySlug(packageSlug, locale),
    api.getDestinationBySlug(destinationSlug, locale),
  ]);
  if (!pkg) {
    return { title: t('notFound') };
  }

  const destinationName = dest?.name || destinationNameFromSlug(destinationSlug);
  return pageMetadata({
    title: t('title', { name: pkg.name, destination: destinationName }),
    description: pkg.description
      ? truncate(pkg.description)
      : t('fallbackDescription', { name: pkg.name, destination: destinationName }),
    path: packagePath(destinationSlug, pkg.slug),
    locale,
    image: pkg.imageUrl || undefined,
    noindex: !(pkg.seoIndexable && dest?.seoIndexable),
  });
}

export async function PackageDetailPage({ destinationSlug, packageSlug, locale }: Args) {
  setRequestLocale(locale);
  const pkg = await api.getPackageBySlug(packageSlug, locale);
  if (!pkg) {
    notFound();
  }

  // City-match guard (spec §6): reject a mismatched destination slug so the same
  // package is not reachable under multiple cities.
  if (pkg.destinationSlug && pkg.destinationSlug !== destinationSlug) {
    notFound();
  }

  const t = await getTranslations({ locale, namespace: 'common' });
  // destinationName rides on the record (localized); the slug is the fallback.
  const destinationName = pkg.destinationName || destinationNameFromSlug(destinationSlug);
  const breadcrumbLd = breadcrumbJsonLd(
    [
      [t('home'), '/'],
      [destinationName, destinationPath(destinationSlug)],
      [pkg.name, packagePath(destinationSlug, pkg.slug)],
    ],
    locale
  );

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonLd(breadcrumbLd) }}
      />
      <LegacyPackageDetail pkg={pkg} destinationSlug={destinationSlug} />
    </>
  );
}
