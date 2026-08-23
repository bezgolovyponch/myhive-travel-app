// SSR destination catalog — a thin server shell: metadata, JSON-LD, the SPA
// escape hatch and the first page of catalog data. The markup is the canonical
// CRA page (legacy-src/pages/DestinationPage.js), so tabs, category filters and
// the real Add-to-trip buttons behave exactly as they do in the SPA, and this
// route cannot drift from it.
import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { api } from '../../../lib/api';
import { breadcrumbJsonLd, pageMetadata, jsonLd } from '../../../lib/seo';
import PublicChrome from '../../../components/site/PublicChrome';
import LegacyAppShim from '../../../components/LegacyAppShim';
import LegacyDestination from '../../../components/site/legacy/LegacyDestination';

interface PageParams {
  params: Promise<{ slug: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}

// Matches PAGE_SIZE in legacy-src/pages/DestinationPage.js — the seeded first
// page must be the same size the client's Show More continues from.
const PAGE_SIZE = 12;

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

export default async function Page({ params, searchParams }: PageParams) {
  const { slug } = await params;
  const sp = await searchParams;

  // Escape hatch: ?tab= (trip builder / packages application state) and
  // ?voteSession= (vote results) are SPA-owned URLs. Hand the whole page to the
  // legacy app so those flows keep working; do not render the SSR catalog.
  // It also means the seeded catalog below only ever serves the bare URL, where
  // there is no search string for the CRA page to interpret.
  if (sp.tab != null || sp.voteSession != null) {
    return <LegacyAppShim />;
  }

  const dest = await api.getDestinationBySlug(slug);
  if (!dest) {
    notFound();
  }

  const [categories, activityPage, packages] = await Promise.all([
    api.getDestinationCategories(dest.id).then((c) => c ?? []),
    api
      .getActivitiesPaged(dest.id, 0, PAGE_SIZE)
      .catch(() => ({ content: [], totalElements: 0, last: true })),
    api.getPackages(dest.id).then((p) => p ?? []),
  ]);

  const breadcrumbLd = breadcrumbJsonLd([
    ['Home', '/'],
    [dest.name, `/destination/${dest.slug}`],
  ]);

  return (
    <PublicChrome>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonLd(breadcrumbLd) }}
      />
      <LegacyDestination
        initial={{
          destination: dest,
          categories,
          activities: activityPage?.content ?? [],
          packages,
          totalElements: activityPage?.totalElements ?? 0,
          last: activityPage?.last ?? true,
        }}
      />
    </PublicChrome>
  );
}
