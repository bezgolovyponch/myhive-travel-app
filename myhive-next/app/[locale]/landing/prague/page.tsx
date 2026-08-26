// Prague landing (mockup fixes/trivlu-landing-2-prague-desktop-sticky-right-v74).
// A thin server shell: locale-aware metadata and the localized catalogue
// fetch; the landing carries its own header and footer, so it lives outside
// the PublicChrome route group. The catalogue itself stays at
// /destination/prague.
import type { Metadata } from 'next';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import { api, type Activity, type Category } from '@/lib/api';
import { DEFAULT_DESTINATION_SLUG, pageMetadata } from '@/lib/seo';
import PragueLanding from '@/components/landing/PragueLanding';
import LegacyProviders, { type LegacyDestination } from '@/components/site/LegacyProviders';
import { buildRows, hydratePool, toLandingActivity } from '@/components/landing/data';

export const revalidate = 3600;

type Props = { params: Promise<{ locale: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'landing.meta.prague' });
  return pageMetadata({
    title: t('title'),
    description: t('description'),
    path: '/landing/prague',
    locale,
  });
}

const FALLBACK_TOTAL = 72;

async function loadCatalogue(locale: string): Promise<{
  activities: Activity[];
  categories: Category[];
  destinationSlug: string;
  destinations: LegacyDestination[];
}> {
  try {
    const destinations = (await api.getDestinations(locale)) ?? [];
    const prague =
      destinations.find((d) => d.slug === 'prague') ??
      destinations.find((d) => d.slug === DEFAULT_DESTINATION_SLUG) ??
      destinations[0];
    if (!prague)
      return {
        activities: [],
        categories: [],
        destinationSlug: 'prague',
        destinations: [],
      };
    const [activities, categories] = await Promise.all([
      api.getActivities(prague.id, locale).then((a) => a ?? []),
      api.getDestinationCategories(prague.id, locale).then((c) => c ?? []),
    ]);
    return {
      activities,
      categories,
      destinationSlug: prague.slug,
      destinations,
    };
  } catch {
    // Backend unreachable: render the page on curated fallbacks rather than 500.
    return {
      activities: [],
      categories: [],
      destinationSlug: 'prague',
      destinations: [],
    };
  }
}

export default async function PragueLandingPage({ params }: Props) {
  const { locale } = await params;
  setRequestLocale(locale);
  const { activities, categories, destinationSlug, destinations } = await loadCatalogue(locale);
  const landing = activities.map(toLandingActivity);

  return (
    <LegacyProviders destinations={destinations}>
      <PragueLanding
        rows={buildRows(landing, categories)}
        pool={hydratePool(landing)}
        totalActivities={landing.length || FALLBACK_TOTAL}
        destinationSlug={destinationSlug}
      />
    </LegacyProviders>
  );
}
