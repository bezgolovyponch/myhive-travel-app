// SSR destination catalog — content parity with legacy-src/pages/DestinationPage.js.
// The Activities/Packages tabs render as in-page anchors; "Trip Builder" and any
// SPA application state (?tab=, ?voteSession=) full-page-navigate into the legacy
// SPA via the escape hatch below.
import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { api, type Activity, type TripPackage } from '../../../lib/api';
import { breadcrumbJsonLd, pageMetadata, jsonLd } from '../../../lib/seo';
import ActivityCardStatic from '../../../components/site/ActivityCardStatic';
import PublicChrome from '../../../components/site/PublicChrome';
import LegacyAppShim from '../../../components/LegacyAppShim';
import '../../../legacy-src/pages/DestinationPage.css';

interface PageParams {
  params: Promise<{ slug: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}

const PRAGUE_TITLE = 'Prague Stag Do — Activities & Packages | Trivlu';
const PRAGUE_DESCRIPTION =
  'Everything for a Prague stag do: top activities, prices for the group and instant trip building. Start planning in minutes.';

export async function generateMetadata({ params, searchParams }: PageParams): Promise<Metadata> {
  const sp = await searchParams;
  // SPA-owned URL state must render even during a backend outage, and the
  // parameterized variants must never be indexed.
  if (sp.tab != null || sp.voteSession != null) {
    return { title: 'Trip Builder | Trivlu', robots: { index: false, follow: true } };
  }
  const { slug } = await params;
  const dest = await api.getDestinationBySlug(slug);
  if (!dest) {
    return { title: 'Destination not found | Trivlu' };
  }
  const isPrague = slug === 'prague';
  return pageMetadata({
    title: isPrague ? PRAGUE_TITLE : `${dest.name} Stag Do — Activities & Packages | Trivlu`,
    description: isPrague
      ? PRAGUE_DESCRIPTION
      : dest.description ||
        `Everything for a ${dest.name} stag do: top activities, prices for the group and instant trip building.`,
    path: `/destination/${slug}`,
    image: dest.imageUrl || undefined,
    noindex: !dest.seoIndexable,
  });
}

export default async function DestinationPage({ params, searchParams }: PageParams) {
  const { slug } = await params;
  const sp = await searchParams;

  // Escape hatch: ?tab= (trip builder / packages application state) and
  // ?voteSession= (vote results) are SPA-owned URLs. Hand the whole page to the
  // legacy app so those flows keep working; do not render the SSR catalog.
  if (sp.tab != null || sp.voteSession != null) {
    return <LegacyAppShim />;
  }

  const dest = await api.getDestinationBySlug(slug);
  if (!dest) {
    notFound();
  }

  const [activities, packages] = await Promise.all([
    api.getActivities(dest.id).then((a) => a ?? []),
    api.getPackages(dest.id).then((p) => p ?? []),
  ]);

  const breadcrumbLd = breadcrumbJsonLd([
    ['Home', '/'],
    [dest.name, `/destination/${dest.slug}`],
  ]);

  return (
    <PublicChrome>
      <div className="destination-page">
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: jsonLd(breadcrumbLd) }}
        />

        <div
          className="page-hero destination-header"
          style={
            dest.imageUrl
              ? {
                  backgroundImage: `linear-gradient(rgba(0,0,0,0.45), rgba(0,0,0,0.45)), url(${dest.imageUrl})`,
                  backgroundSize: 'cover',
                  backgroundPosition: 'center',
                }
              : undefined
          }
        >
          <h1>{dest.name} Stag Do</h1>
          <p>{dest.description || ''}</p>
        </div>

        <nav className="tab-nav">
          <a className="tab-btn active" href="#activities">
            Activities
          </a>
          {packages.length > 0 && (
            <a className="tab-btn" href="#packages">
              Packages
            </a>
          )}
          <a className="tab-btn" href={`/destination/${slug}?tab=trip-builder`}>
            Trip Builder
          </a>
        </nav>

        <div className="tab-content" id="activities">
          <div className="tab-header">
            <h2>Activities</h2>
          </div>
          <div className="activities-grid">
            {activities.map((activity: Activity) => (
              <ActivityCardStatic key={activity.id} activity={activity} />
            ))}
          </div>
        </div>

        {packages.length > 0 && (
          <div id="packages" className="tab-content">
            <div className="tab-header">
              <h2>Packages</h2>
            </div>
            <div className="packages-grid">
              {packages.map((pkg: TripPackage) => (
                <Link
                  key={pkg.id}
                  href={`/destination/${slug}/package/${pkg.slug}`}
                  className="card"
                  aria-label={`View ${pkg.name}`}
                >
                  <img
                    src={pkg.imageUrl}
                    alt={pkg.name}
                    className="activity-image"
                    loading="lazy"
                  />
                  <div className="activity-content">
                    <h3 className="activity-title">{pkg.name}</h3>
                    <div className="activity-footer">
                      <span className="activity-price">€{Math.round(pkg.discountedPrice)}</span>
                    </div>
                  </div>
                </Link>
              ))}
            </div>
          </div>
        )}
      </div>
    </PublicChrome>
  );
}
