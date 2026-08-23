// Prague landing (mockup fixes/trivlu-landing-2-prague-desktop-sticky-right-v74).
// A thin server shell: metadata and the catalogue fetch; the landing carries
// its own header and footer, so it lives outside the PublicChrome route group.
// The catalogue itself stays at /destination/prague.
import { api, type Activity, type Category } from '../../../lib/api';
import { DEFAULT_DESTINATION_SLUG, pageMetadata } from '../../../lib/seo';
import PragueLanding from '../../../components/landing/PragueLanding';
import { buildRows, hydratePool, toLandingActivity } from '../../../components/landing/data';

export const revalidate = 3600;

// Title and description come from the approved mockup's <head>.
export const metadata = pageMetadata({
  title: 'Trivlu. Why Prague Is the Best Bachelor Party City in Europe.',
  description:
    'Prague lies ninety minutes away. Beer costs €2.20. Trivlu offers 72 local activities. Read five reasons, real prices and a complete sample weekend.',
  path: '/landing/prague',
});

const FALLBACK_TOTAL = 72;

async function loadCatalogue(): Promise<{
  activities: Activity[];
  categories: Category[];
  destinationSlug: string;
}> {
  try {
    const destinations = (await api.getDestinations()) ?? [];
    const prague =
      destinations.find((d) => d.slug === 'prague') ??
      destinations.find((d) => d.slug === DEFAULT_DESTINATION_SLUG) ??
      destinations[0];
    if (!prague) return { activities: [], categories: [], destinationSlug: 'prague' };
    const [activities, categories] = await Promise.all([
      api.getActivities(prague.id).then((a) => a ?? []),
      api.getDestinationCategories(prague.id).then((c) => c ?? []),
    ]);
    return { activities, categories, destinationSlug: prague.slug };
  } catch {
    // Backend unreachable: render the page on curated fallbacks rather than 500.
    return { activities: [], categories: [], destinationSlug: 'prague' };
  }
}

export default async function PragueLandingPage() {
  const { activities, categories, destinationSlug } = await loadCatalogue();
  const landing = activities.map(toLandingActivity);

  return (
    <PragueLanding
      rows={buildRows(landing, categories)}
      pool={hydratePool(landing)}
      totalActivities={landing.length || FALLBACK_TOTAL}
      destinationSlug={destinationSlug}
    />
  );
}
