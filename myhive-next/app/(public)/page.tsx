// SSR homepage. A thin server shell: metadata, JSON-LD and the featured-activity
// fetch. The markup itself is the canonical CRA homepage — the hand-written copy
// that used to live here had drifted from it (stale headings, inverted section
// order, an inline vote card whose stylesheet was never imported, and no
// cta_click events at all), and would have drifted again after any CRA fix.
import { api, type Activity } from '../../lib/api';
import { SITE_URL, WHATSAPP_URL, DEFAULT_DESTINATION_SLUG, pageMetadata, jsonLd } from '../../lib/seo';
import LegacyHomePage from '../../components/site/LegacyHomePage';

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

const MAX_FEATURED = 12;

// Mirrors FeaturedActivitiesSection's own fallback: curated featured activities,
// else the default destination's, so the grid is never empty and never shows
// another destination's activities.
async function loadFeatured(): Promise<Activity[]> {
  try {
    const destinations = (await api.getDestinations()) ?? [];
    const main =
      destinations.find((d) => d.slug === DEFAULT_DESTINATION_SLUG) ?? destinations[0];
    let activities = (await api.getFeaturedActivities().catch(() => null)) ?? [];
    if (activities.length === 0 && main) {
      activities = (await api.getActivities(main.id)) ?? [];
    }
    return activities.slice(0, MAX_FEATURED);
  } catch {
    // Backend unreachable: render the page without the grid rather than 500.
    return [];
  }
}

export default async function HomePage() {
  const activities = await loadFeatured();

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
