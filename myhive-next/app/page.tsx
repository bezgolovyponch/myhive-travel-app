// SSR homepage. A thin server shell: metadata, JSON-LD and the catalogue
// fetch. The markup is the redesigned voting landing (mockup
// fixes/trivlu-landing-1-voting-v58.html), which carries its own header and
// footer — hence this page lives outside the (public) route group so
// PublicChrome does not wrap a second chrome around it.
import { api, type Activity, type Category } from '../lib/api';
import { SITE_URL, WHATSAPP_URL, DEFAULT_DESTINATION_SLUG, pageMetadata, jsonLd } from '../lib/seo';
import HomeLanding from '../components/landing/HomeLanding';
import {
  buildDeck,
  buildRows,
  hydratePool,
  toLandingActivity,
} from '../components/landing/data';

export const revalidate = 3600;

// Kept identical to the <Helmet> block in legacy-src/pages/HomePage.js. Both
// render: Next's metadata is what crawlers read, Helmet rewrites the same values
// on hydration, so they must not disagree.
const TITLE = 'Trivlu — Prague Stag Do. Planned in 10 Minutes.';
const DESCRIPTION =
  'Your group votes, we do the rest. The perfect Prague stag do weekend — activities, booking and logistics all sorted for you.';

export const metadata = pageMetadata({
  title: TITLE,
  description: DESCRIPTION,
  path: '/',
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

export default async function HomePage() {
  const { activities, categories, destinationSlug } = await loadCatalogue();
  const landing = activities.map(toLandingActivity);

  const organizationJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    name: 'Trivlu',
    url: SITE_URL,
    logo: `${SITE_URL}/logo-trivlu.png`,
    sameAs: [WHATSAPP_URL],
  };

  const prices = landing.map((a) => a.price).filter((p) => p > 0);

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonLd(organizationJsonLd) }}
      />
      <HomeLanding
        rows={buildRows(landing, categories)}
        deck={buildDeck(landing)}
        pool={hydratePool(landing)}
        totalActivities={landing.length || FALLBACK_TOTAL}
        fromPrice={prices.length ? Math.min(...prices) : FALLBACK_FROM_PRICE}
        destinationSlug={destinationSlug}
      />
    </>
  );
}
