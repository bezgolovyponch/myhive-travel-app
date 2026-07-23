// SSR package detail — content parity with legacy-src/pages/PackageDetailPage.js.
// Interactive Add-to-trip lives in the SPA; the CTA here full-page-navigates
// into an SPA-owned URL.
import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { api, type TripPackage } from '../../../../../../lib/api';
import { breadcrumbJsonLd, formatPricePerPerson, pageMetadata, jsonLd } from '../../../../../../lib/seo';
import '../../../../../../legacy-src/pages/PackageDetailPage.css';

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
  const title = `${pkg.name} — ${destinationName} Package | Trivlu`;
  return pageMetadata({
    title,
    description: pkg.description
      ? truncate(pkg.description)
      : `${pkg.name} package in ${destinationName}.`,
    path: `/destination/${slug}/package/${pkg.slug}`,
    image: pkg.imageUrl || undefined,
    noindex: !(pkg.seoIndexable && dest?.seoIndexable),
  });
}

export default async function PackageDetailPage({ params }: PageParams) {
  const { slug, pslug } = await params;
  const pkg = await api.getPackageBySlug(pslug);
  if (!pkg) {
    notFound();
  }

  // City-match guard (spec §6): reject a mismatched destination slug so the
  // same package is not reachable under multiple cities.
  if (pkg.destinationSlug && pkg.destinationSlug !== slug) {
    notFound();
  }

  const destinationName = destinationNameFromSlug(slug);
  const activities: TripPackage['activities'] = pkg.activities ?? [];

  const breadcrumbLd = breadcrumbJsonLd([
    ['Home', '/'],
    [destinationName, `/destination/${slug}`],
    [pkg.name, `/destination/${slug}/package/${pkg.slug}`],
  ]);

  return (
    <div className="package-detail-page">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonLd(breadcrumbLd) }}
      />

      <nav className="package-detail-breadcrumbs">
        <a href="/">Home</a>
        <span>&rsaquo;</span>
        <a href={`/destination/${slug}`}>{destinationName}</a>
        <span>&rsaquo;</span>
        <span>{pkg.name}</span>
      </nav>

      <div className="package-detail-hero">
        {pkg.imageUrl && (
          <img src={pkg.imageUrl} alt={pkg.name} className="package-detail-hero-image" />
        )}
        <div className="package-detail-hero-overlay">
          <h1 className="package-detail-hero-title">{pkg.name}</h1>
        </div>
      </div>

      <div className="package-detail-grid">
        <div className="package-detail-main">
          {pkg.description && (
            <p className="package-detail-description">{pkg.description}</p>
          )}

          {activities && activities.length > 0 && (
            <section className="package-detail-activities-section">
              <h2 className="package-detail-section-title">What&apos;s Included</h2>
              <div className="package-detail-activities">
                {activities.map((activity) => (
                  <Link
                    key={activity.id}
                    href={`/destination/${slug}/activity/${activity.slug}`}
                    className="package-detail-activity"
                  >
                    {activity.imageUrl && (
                      <img
                        src={activity.imageUrl}
                        alt={activity.name}
                        className="package-detail-activity-image"
                      />
                    )}
                    <div className="package-detail-activity-info">
                      <span className="package-detail-activity-name">{activity.name}</span>
                      <span className="package-detail-activity-price">
                        {formatPricePerPerson(activity.price)}
                      </span>
                    </div>
                  </Link>
                ))}
              </div>
            </section>
          )}
        </div>

        <aside className="package-detail-price-card">
          <div className="package-detail-price-card-inner">
            <div className="package-detail-original">€{Math.round(pkg.originalPrice)}</div>
            <div className="package-detail-discounted">€{Math.round(pkg.discountedPrice)}</div>
            <div className="package-detail-savings">
              You save €{Math.round(pkg.savings)}
              {pkg.discountPct ? ` (${Math.round(pkg.discountPct)}% off)` : ''}
            </div>
            <a
              className="add-to-trip-btn package-detail-add-btn"
              href={`/destination/${slug}?tab=trip-builder`}
            >
              Add to Trip
            </a>
          </div>
        </aside>
      </div>
    </div>
  );
}
