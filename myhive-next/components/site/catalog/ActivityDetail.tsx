// SSR activity detail: a thin server shell (metadata, JSON-LD, the record fetch
// and the city-match guard) around the canonical CRA page
// (legacy-src/pages/ActivityDetailPage.js). Shared server module rendered by both
// the hub route (app/[locale]/(public)/prague-stag-do/...) and the legacy
// destination/[slug]/... route.
// Record fields arrive already localized for `locale` (backend translations
// column, English fallback per field).
import type { Metadata } from 'next';
import { notFound, permanentRedirect } from 'next/navigation';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import { api, type Activity } from '@/lib/api';
import { wrongSlugTarget } from '@/lib/slug-redirect';
import { breadcrumbJsonLd, pageMetadata, jsonLd } from '@/lib/seo';
import { activityPath, destinationPath, entitySlug, localizePath } from '../../../legacy-src/i18n/routes';
import PublicChrome from '@/components/site/PublicChrome';
import LegacyActivityDetail from '@/components/site/legacy/LegacyActivityDetail';
import { destinationNameFromSlug, truncate } from './text';

interface Args {
  destinationSlug: string;
  activitySlug: string;
  locale: string;
}

/** Internal (EN-shaped) path of an activity per locale; DE uses its own slug. */
export function activityPaths(destinationSlug: string, a: Activity): Record<string, string> {
  return {
    en: activityPath(destinationSlug, a.slug),
    de: activityPath(destinationSlug, a.slugDe ?? a.slug),
  };
}

/** Public (locale-prefixed) URL of the same activity in each locale. */
export function activityAlternates(destinationSlug: string, a: Activity): Record<string, string> {
  return Object.fromEntries(
    Object.entries(activityPaths(destinationSlug, a)).map(([l, p]) => [l, localizePath(p, l)])
  );
}

export async function activityMetadata({ destinationSlug, activitySlug, locale }: Args): Promise<Metadata> {
  const t = await getTranslations({ locale, namespace: 'meta.activity' });
  const [activity, dest] = await Promise.all([
    api.getActivityBySlug(activitySlug, locale),
    api.getDestinationBySlug(destinationSlug, locale),
  ]);
  if (!activity) {
    return { title: t('notFound') };
  }
  const destinationName = dest?.name || destinationNameFromSlug(destinationSlug);
  return pageMetadata({
    title: t('title', { name: activity.name, destination: destinationName }),
    description: activity.description
      ? truncate(activity.description)
      : t('fallbackDescription', { name: activity.name, destination: destinationName }),
    path: activityPaths(destinationSlug, activity),
    locale,
    image: activity.imageUrl || undefined,
    noindex: !(activity.seoIndexable && dest?.seoIndexable),
  });
}

export async function ActivityDetailPage({ destinationSlug, activitySlug, locale }: Args) {
  setRequestLocale(locale);
  const activity = await api.getActivityBySlug(activitySlug, locale);
  if (!activity) {
    notFound();
  }

  // City-match guard (spec §6): the same card must not be reachable under
  // multiple cities, so reject a mismatched destination slug.
  if (activity.destinationSlug && activity.destinationSlug !== destinationSlug) {
    notFound();
  }

  const target = wrongSlugTarget(activitySlug, activity, (s) => activityPath(destinationSlug, s), locale);
  if (target) {
    permanentRedirect(target);
  }

  const t = await getTranslations({ locale, namespace: 'common' });
  // destinationName rides on the record (localized); the slug is the fallback.
  const destinationName = activity.destinationName || destinationNameFromSlug(destinationSlug);
  const breadcrumbLd = breadcrumbJsonLd(
    [
      [t('home'), '/'],
      [destinationName, destinationPath(destinationSlug)],
      [activity.name, activityPath(destinationSlug, entitySlug(activity, locale))],
    ],
    locale
  );

  return (
    <PublicChrome locale={locale} alternates={activityAlternates(destinationSlug, activity)}>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonLd(breadcrumbLd) }}
      />
      <LegacyActivityDetail activity={activity} destinationSlug={destinationSlug} />
    </PublicChrome>
  );
}
