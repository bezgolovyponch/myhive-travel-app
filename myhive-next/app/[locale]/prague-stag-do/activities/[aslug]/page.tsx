import type { Metadata } from 'next';
import { ActivityDetailPage, activityMetadata } from '@/components/site/catalog/ActivityDetail';

export const revalidate = 3600;

// Folder name = DESTINATION_HUBS.prague in legacy-src/i18n/routes.js (the DE
// spelling /jga-prag is rewritten here by next-intl pathnames). A second hub
// city gets its own folder + table row.
const DESTINATION_SLUG = 'prague';

type Props = { params: Promise<{ locale: string; aslug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, aslug } = await params;
  return activityMetadata({ destinationSlug: DESTINATION_SLUG, activitySlug: aslug, locale });
}

export default async function Page({ params }: Props) {
  const { locale, aslug } = await params;
  return ActivityDetailPage({ destinationSlug: DESTINATION_SLUG, activitySlug: aslug, locale });
}
