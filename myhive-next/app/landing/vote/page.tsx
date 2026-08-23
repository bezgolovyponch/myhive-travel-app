// Voting landing (mockup fixes/trivlu-landing-1-voting-v58.html). A thin
// server shell: metadata and the catalogue fetch; the landing carries its own
// header and footer, so it lives outside the PublicChrome route group.
import { api, type Activity, type Category } from '../../../lib/api';
import { DEFAULT_DESTINATION_SLUG, pageMetadata } from '../../../lib/seo';
import VoteLanding from '../../../components/landing/VoteLanding';
import {
  buildDeck,
  buildRows,
  hydratePool,
  toLandingActivity,
} from '../../../components/landing/data';

export const revalidate = 3600;

// Title and description come from the approved mockup's <head>.
export const metadata = pageMetadata({
  title: 'Trivlu. Bachelor Party Planner for Prague. Skip Chatting. Start Voting.',
  description:
    'Trivlu plans bachelor parties in Prague. Swipe the activities. Send one link. Your friends vote. Choose from 72 activities. Free cancellation. Reply in 10 minutes.',
  path: '/landing/vote',
});

// The landing's copy quotes these when the backend is unreachable, so the
// headline never reads "0 activities".
const FALLBACK_TOTAL = 72;
const FALLBACK_FROM_PRICE = 10;

async function loadCatalogue(): Promise<{
  activities: Activity[];
  categories: Category[];
  destinationSlug: string;
}> {
  try {
    const destinations = (await api.getDestinations()) ?? [];
    const main =
      destinations.find((d) => d.slug === DEFAULT_DESTINATION_SLUG) ?? destinations[0];
    if (!main) return { activities: [], categories: [], destinationSlug: DEFAULT_DESTINATION_SLUG };
    const [activities, categories] = await Promise.all([
      api.getActivities(main.id).then((a) => a ?? []),
      api.getDestinationCategories(main.id).then((c) => c ?? []),
    ]);
    return { activities, categories, destinationSlug: main.slug };
  } catch {
    // Backend unreachable: render the page on curated fallbacks rather than 500.
    return { activities: [], categories: [], destinationSlug: DEFAULT_DESTINATION_SLUG };
  }
}

export default async function VoteLandingPage() {
  const { activities, categories, destinationSlug } = await loadCatalogue();
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
