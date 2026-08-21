// SSR activity detail — a thin server shell: metadata, JSON-LD, the record fetch
// and the city-match guard. The markup is the canonical CRA page
// (legacy-src/pages/ActivityDetailPage.js), so this route cannot drift from what
// the SPA renders, and its Add-to-trip CTA is the real one rather than a link.
// Record fields (name, description) are backend data — localized in the content
// phase (Ф3); until then every locale serves the English record.
import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import { api } from '@/lib/api';
import { breadcrumbJsonLd, pageMetadata, jsonLd } from '@/lib/seo';
import LegacyActivityDetail from '@/components/site/legacy/LegacyActivityDetail';

export const revalidate = 3600;

type Props = { params: Promise<{ locale: string; slug: string; aslug: string }> };

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

function truncate(text: string, limit = 155) {
  if (text.length <= limit) return text;
  return `${text.slice(0, limit - 1).trimEnd()}…`;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, slug, aslug } = await params;
  const t = await getTranslations({ locale, namespace: 'meta.activity' });
  const [activity, dest] = await Promise.all([
    api.getActivityBySlug(aslug),
    api.getDestinationBySlug(slug),
  ]);
  if (!activity) {
    return { title: t('notFound') };
  }
  const destinationName = destinationNameFromSlug(slug);
  return pageMetadata({
    title: t('title', { name: activity.name, destination: destinationName }),
    description: activity.description
      ? truncate(activity.description)
      : t('fallbackDescription', { name: activity.name, destination: destinationName }),
    path: `/destination/${slug}/activity/${activity.slug}`,
    locale,
    image: activity.imageUrl || undefined,
    noindex: !(activity.seoIndexable && dest?.seoIndexable),
  });
}

export default async function Page({ params }: Props) {
  const { locale, slug, aslug } = await params;
  setRequestLocale(locale);
  const activity = await api.getActivityBySlug(aslug);
  if (!activity) {
    notFound();
  }

  // City-match guard (spec §6): the same card must not be reachable under
  // multiple cities, so reject a mismatched destination slug.
  if (activity.destinationSlug && activity.destinationSlug !== slug) {
    notFound();
  }

  const t = await getTranslations({ locale, namespace: 'common' });
  const destinationName = destinationNameFromSlug(slug);
  const breadcrumbLd = breadcrumbJsonLd(
    [
      [t('home'), '/'],
      [destinationName, `/destination/${slug}`],
      [activity.name, `/destination/${slug}/activity/${activity.slug}`],
    ],
    locale
  );

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonLd(breadcrumbLd) }}
      />
      <LegacyActivityDetail activity={activity} />
    </>
  );
}
