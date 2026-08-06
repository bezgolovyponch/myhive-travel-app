// SSR package detail — a thin server shell: metadata, JSON-LD, the record fetch
// and the city-match guard. The markup is the canonical CRA page
// (legacy-src/pages/PackageDetailPage.js), so this route cannot drift from what
// the SPA renders, and its Add-to-trip CTA is the real one rather than a link.
import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { api } from '../../../../../../lib/api';
import { breadcrumbJsonLd, pageMetadata, jsonLd } from '../../../../../../lib/seo';
import LegacyPackageDetail from '../../../../../../components/site/legacy/LegacyPackageDetail';

export const revalidate = 3600;

interface PageParams {
  params: Promise<{ slug: string; pslug: string }>;
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

export async function generateMetadata({ params }: PageParams): Promise<Metadata> {
  const { slug, pslug } = await params;
  const [pkg, dest] = await Promise.all([
    api.getPackageBySlug(pslug),
    api.getDestinationBySlug(slug),
  ]);
  if (!pkg) {
    return { title: 'Package not found | Trivlu' };
  }

  const destinationName = destinationNameFromSlug(slug);
  return pageMetadata({
    title: `${pkg.name} — ${destinationName} Package | Trivlu`,
    description: pkg.description
      ? truncate(pkg.description)
      : `${pkg.name} package in ${destinationName}.`,
    path: `/destination/${slug}/package/${pkg.slug}`,
    image: pkg.imageUrl || undefined,
    noindex: !(pkg.seoIndexable && dest?.seoIndexable),
  });
}

export default async function Page({ params }: PageParams) {
  const { slug, pslug } = await params;
  const pkg = await api.getPackageBySlug(pslug);
  if (!pkg) {
    notFound();
  }

  // City-match guard (spec §6): reject a mismatched destination slug so the same
  // package is not reachable under multiple cities.
  if (pkg.destinationSlug && pkg.destinationSlug !== slug) {
    notFound();
  }

  const destinationName = destinationNameFromSlug(slug);
  const breadcrumbLd = breadcrumbJsonLd([
    ['Home', '/'],
    [destinationName, `/destination/${slug}`],
    [pkg.name, `/destination/${slug}/package/${pkg.slug}`],
  ]);

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
