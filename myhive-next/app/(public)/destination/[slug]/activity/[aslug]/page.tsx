// SSR activity detail — content parity with legacy-src/pages/ActivityDetailPage.js.
// Interactive Add-to-trip / group vote live in the SPA; the CTAs here
// full-page-navigate into SPA-owned URLs.
import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { api, type Activity } from '../../../../../../lib/api';
import { canonical, breadcrumbJsonLd, formatPricePerPerson, WHATSAPP_URL } from '../../../../../../lib/seo';
import '../../../../../../legacy-src/pages/ActivityDetailPage.css';

export const revalidate = 3600;

interface PageParams {
  params: Promise<{ slug: string; aslug: string }>;
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

function formatDuration(minutes: number) {
  if (minutes < 60) {
    return `${minutes}m`;
  }
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest ? `${hours}h ${rest}m` : `${hours}h`;
}

function primaryCategoryName(activity: Activity): string | null {
  const first = activity.categories?.[0];
  if (!first) return null;
  return typeof first === 'string' ? first : first.name;
}

function truncate(text: string, limit = 155) {
  if (text.length <= limit) return text;
  return `${text.slice(0, limit - 1).trimEnd()}…`;
}

export async function generateMetadata({ params }: PageParams): Promise<Metadata> {
  const { slug, aslug } = await params;
  const activity = await api.getActivityBySlug(aslug);
  if (!activity) {
    return { title: 'Activity not found | Trivlu' };
  }

  const destinationName = destinationNameFromSlug(slug);
  const title = `${activity.name} in ${destinationName} | Trivlu`;
  const description = activity.description
    ? truncate(activity.description)
    : `${activity.name} in ${destinationName}.`;
  const url = canonical(`/destination/${slug}/activity/${activity.slug}`);

  return {
    title,
    description,
    alternates: { canonical: url },
    openGraph: {
      title,
      description,
      url,
      ...(activity.imageUrl ? { images: [{ url: activity.imageUrl }] } : {}),
    },
  };
}

export default async function ActivityDetailPage({ params }: PageParams) {
  const { slug, aslug } = await params;
  const activity = await api.getActivityBySlug(aslug);
  if (!activity) {
    notFound();
  }

  // City-match guard (spec §6): the same card must not be reachable under
  // multiple cities, so reject a mismatched destination slug.
  if (activity.destinationSlug && activity.destinationSlug !== slug) {
    notFound();
  }

  const destinationName = destinationNameFromSlug(slug);
  const category = capitalizeFirst(primaryCategoryName(activity) || 'Activity');
  const durationText =
    activity.duration != null ? formatDuration(activity.duration) : null;
  const hasGroupMin = activity.minPrice != null && activity.minPrice > 0;
  const formattedPrice = `${hasGroupMin ? 'from ' : ''}${formatPricePerPerson(activity.price)}`;
  const includesItems = (activity.includes || '')
    .split(/[,;\n]+/)
    .map((item) => item.trim())
    .filter(Boolean);

  const breadcrumbLd = breadcrumbJsonLd([
    ['Home', '/'],
    [destinationName, `/destination/${slug}`],
    [activity.name, `/destination/${slug}/activity/${activity.slug}`],
  ]);

  return (
    <div className="activity-detail-page">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(breadcrumbLd) }}
      />

      <nav className="activity-detail-breadcrumbs">
        <a href="/">Home</a>
        <span className="sep">&rsaquo;</span>
        <a href={`/destination/${slug}`}>{destinationName}</a>
        <span className="sep">&rsaquo;</span>
        <span>{activity.name}</span>
      </nav>

      <div className="activity-detail-title-block">
        <h1>{activity.name}</h1>
        <div className="activity-detail-meta-line">
          <span className="activity-detail-chip">
            <i className="ph ph-tag" aria-hidden="true" /> {category}
          </span>
          {durationText && (
            <span className="activity-detail-chip">
              <i className="ph ph-clock" aria-hidden="true" /> {durationText}
            </span>
          )}
        </div>
      </div>

      {activity.imageUrl && (
        <img
          src={activity.imageUrl}
          alt={activity.name}
          style={{
            width: '100%',
            maxHeight: '30rem',
            objectFit: 'cover',
            borderRadius: 'var(--radius-lg, 1rem)',
          }}
        />
      )}

      <div className="activity-detail-layout">
        <div className="activity-detail-content">
          {includesItems.length > 0 && (
            <section className="activity-detail-blk">
              <h2 className="activity-detail-blk-title">
                <i className="ph ph-check-circle" aria-hidden="true" /> What&apos;s included
              </h2>
              <ul className="activity-detail-inc-list">
                {includesItems.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </section>
          )}
          {activity.description && (
            <section className="activity-detail-blk">
              <h2 className="activity-detail-blk-title">
                <i className="ph ph-note" aria-hidden="true" /> About this activity
              </h2>
              <p className="activity-detail-desc">{activity.description}</p>
            </section>
          )}
        </div>

        <aside className="activity-detail-add-col">
          <div className="activity-detail-add-panel">
            <div className="activity-detail-price-line">
              <span className="amt">{formattedPrice}</span>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--gap-sm, 0.5rem)' }}>
              <a className="activity-detail-add-btn" href="/vote/new">
                <i className="ph ph-check-square" aria-hidden="true" /> Start Group Vote
              </a>
              <a
                className="activity-detail-add-btn"
                href={`/destination/${slug}?tab=trip-builder`}
                style={{ background: 'transparent', border: '1px solid var(--border)' }}
              >
                <i className="ph ph-plus-circle" aria-hidden="true" /> Add to trip
              </a>
            </div>
            <p className="activity-detail-panel-help">
              Not sure yet?{' '}
              <a href={WHATSAPP_URL} target="_blank" rel="noopener noreferrer">
                Chat with our team
              </a>{' '}
              on WhatsApp.
            </p>
          </div>
        </aside>
      </div>
    </div>
  );
}
