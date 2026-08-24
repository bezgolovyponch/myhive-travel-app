// SSR homepage. A thin server shell: metadata, JSON-LD and the featured-activity
// fetch. The markup itself is the canonical CRA homepage — the hand-written copy
// that used to live here had drifted from it (stale headings, inverted section
// order, an inline vote card whose stylesheet was never imported, and no
// cta_click events at all), and would have drifted again after any CRA fix.
import type { Metadata } from 'next';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import { api, type Activity } from '@/lib/api';
import { SITE_URL, WHATSAPP_URL, DEFAULT_DESTINATION_SLUG, pageMetadata, jsonLd } from '@/lib/seo';
import LegacyHomePage from '@/components/site/LegacyHomePage';

export const revalidate = 3600;

type Props = { params: Promise<{ locale: string }> };

// The CRA homepage's <Helmet> block renders the same EN values on hydration but
// is silenced here by PageHeadEnabledContext, so localized metadata is safe.
export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'meta.home' });
  return pageMetadata({
    title: t('title'),
    description: t('description'),
    path: '/',
    locale,
  });
}

const MAX_FEATURED = 12;

// Mirrors FeaturedActivitiesSection's own fallback: curated featured activities,
// else the default destination's, so the grid is never empty and never shows
// another destination's activities.
async function loadFeatured(locale: string): Promise<Activity[]> {
  try {
    const destinations = (await api.getDestinations(locale)) ?? [];
    const main =
      destinations.find((d) => d.slug === DEFAULT_DESTINATION_SLUG) ?? destinations[0];
    let activities = (await api.getFeaturedActivities(locale).catch(() => null)) ?? [];
    if (activities.length === 0 && main) {
      activities = (await api.getActivities(main.id, locale)) ?? [];
    }
    return activities.slice(0, MAX_FEATURED);
  } catch {
    // Backend unreachable: render the page without the grid rather than 500.
    return [];
  }
}

export default async function HomePage({ params }: Props) {
  const { locale } = await params;
  setRequestLocale(locale);
  const activities = await loadFeatured(locale);

  const organizationJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    name: 'Trivlu',
    url: SITE_URL,
    logo: `${SITE_URL}/logo-trivlu.png`,
    sameAs: [WHATSAPP_URL],
  };

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonLd(organizationJsonLd) }}
      />
      <LegacyHomePage activities={activities} />
    </>
  );
}
