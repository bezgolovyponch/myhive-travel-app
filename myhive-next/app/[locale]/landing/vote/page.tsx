// Voting landing (mockup fixes/trivlu-landing-1-voting-v58.html). A thin
// server shell: locale-aware metadata and the localized catalogue fetch; the
// landing carries its own header and footer, so it lives outside the
// PublicChrome route group.
import type { Metadata } from 'next';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import { api, type Activity, type Category } from '@/lib/api';
import { DEFAULT_DESTINATION_SLUG, pageMetadata } from '@/lib/seo';
import VoteLanding from '@/components/landing/VoteLanding';
import {
  buildDeck,
  buildRows,
  hydratePool,
  toLandingActivity,
} from '@/components/landing/data';

export const revalidate = 3600;

type Props = { params: Promise<{ locale: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'landing.meta.vote' });
  return pageMetadata({
    title: t('title'),
    description: t('description'),
    path: '/landing/vote',
    locale,
  });
}

// The landing's copy quotes these when the backend is unreachable, so the
// headline never reads "0 activities".
const FALLBACK_TOTAL = 72;
const FALLBACK_FROM_PRICE = 10;

async function loadCatalogue(locale: string): Promise<{
  activities: Activity[];
  categories: Category[];
  destinationSlug: string;
}> {
  try {
    const destinations = (await api.getDestinations(locale)) ?? [];
    const main =
      destinations.find((d) => d.slug === DEFAULT_DESTINATION_SLUG) ?? destinations[0];
    if (!main) return { activities: [], categories: [], destinationSlug: DEFAULT_DESTINATION_SLUG };
    const [activities, categories] = await Promise.all([
      api.getActivities(main.id, locale).then((a) => a ?? []),
      api.getDestinationCategories(main.id, locale).then((c) => c ?? []),
    ]);
    return { activities, categories, destinationSlug: main.slug };
  } catch {
    // Backend unreachable: render the page on curated fallbacks rather than 500.
    return { activities: [], categories: [], destinationSlug: DEFAULT_DESTINATION_SLUG };
  }
}

export default async function VoteLandingPage({ params }: Props) {
  const { locale } = await params;
  setRequestLocale(locale);
  const { activities, categories, destinationSlug } = await loadCatalogue(locale);
  const landing = activities.map(toLandingActivity);
  const prices = landing.map((a) => a.price).filter((p) => p > 0);

  return (
    <VoteLanding
      rows={buildRows(landing, categories)}
      deck={buildDeck(landing)}
      pool={hydratePool(landing)}
      totalActivities={landing.length || FALLBACK_TOTAL}
      fromPrice={prices.length ? Math.min(...prices) : FALLBACK_FROM_PRICE}
      destinationSlug={destinationSlug}
    />
  );
}
