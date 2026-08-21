// SSR destination catalog — a thin server shell: metadata, JSON-LD, the SPA
// escape hatch and the first page of catalog data. The markup is the canonical
// CRA page (legacy-src/pages/DestinationPage.js), so tabs, category filters and
// the real Add-to-trip buttons behave exactly as they do in the SPA, and this
// route cannot drift from it.
import type { Metadata } from 'next';
import { notFound, redirect } from 'next/navigation';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import { api } from '@/lib/api';
import { breadcrumbJsonLd, pageMetadata, jsonLd } from '@/lib/seo';
import PublicChrome from '@/components/site/PublicChrome';
import LegacyAppShim from '@/components/LegacyAppShim';
import LegacyDestination from '@/components/site/legacy/LegacyDestination';
import { DEFAULT_LOCALE } from '../../../../legacy-src/i18n/routes';

interface PageParams {
  params: Promise<{ locale: string; slug: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}

// Matches PAGE_SIZE in legacy-src/pages/DestinationPage.js — the seeded first
// page must be the same size the client's Show More continues from.
const PAGE_SIZE = 12;

function spaQueryString(sp: Record<string, string | string[] | undefined>) {
  const qs = new URLSearchParams();
  for (const [key, value] of Object.entries(sp)) {
    if (value == null) continue;
    for (const v of Array.isArray(value) ? value : [value]) {
      qs.append(key, v);
    }
  }
  const s = qs.toString();
  return s ? `?${s}` : '';
}

export async function generateMetadata({ params, searchParams }: PageParams): Promise<Metadata> {
  const sp = await searchParams;
  const { locale, slug } = await params;
  const t = await getTranslations({ locale, namespace: 'meta.destination' });
  // SPA-owned URL state must render even during a backend outage, and the
  // parameterized variants must never be indexed.
  if (sp.tab != null || sp.voteSession != null) {
    return { title: t('tripBuilderTitle'), robots: { index: false, follow: true } };
  }
  const dest = await api.getDestinationBySlug(slug);
  if (!dest) {
    return { title: t('notFound') };
  }
  const isPrague = slug === 'prague';
  return pageMetadata({
    title: isPrague ? t('pragueTitle') : t('title', { name: dest.name }),
    description: isPrague
      ? t('pragueDescription')
      : dest.description || t('description', { name: dest.name }),
    path: `/destination/${slug}`,
    locale,
    image: dest.imageUrl || undefined,
    noindex: !dest.seoIndexable,
  });
}

export default async function Page({ params, searchParams }: PageParams) {
  const { locale, slug } = await params;
  setRequestLocale(locale);
  const sp = await searchParams;

  // Escape hatch: ?tab= (trip builder / packages application state) and
  // ?voteSession= (vote results) are SPA-owned URLs. Hand the whole page to the
  // legacy app so those flows keep working; do not render the SSR catalog.
  // It also means the seeded catalog below only ever serves the bare URL, where
  // there is no search string for the CRA page to interpret.
  if (sp.tab != null || sp.voteSession != null) {
    // The SPA's BrowserRouter has no locale-prefixed routes: a fresh load of
    // /de/destination/x?tab=... would render blank chrome. The flow itself is
    // English-only for now, so hand it the real URL. (Client-side tab switches
    // on /de stay in the CRA page and never hit this branch.)
    if (locale !== DEFAULT_LOCALE) {
      redirect(`/destination/${slug}${spaQueryString(sp)}`);
    }
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

  const t = await getTranslations({ locale, namespace: 'common' });
  const breadcrumbLd = breadcrumbJsonLd(
    [
      [t('home'), '/'],
      [dest.name, `/destination/${dest.slug}`],
    ],
    locale
  );

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
