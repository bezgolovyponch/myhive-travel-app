import type { Metadata } from 'next';
import { DestinationCatalogPage, destinationMetadata } from '@/components/site/catalog/DestinationCatalog';

// Folder name = DESTINATION_HUBS.prague in legacy-src/i18n/routes.js (the DE
// spelling /jga-prag is rewritten here by next-intl pathnames). A second hub
// city gets its own folder + table row.
const DESTINATION_SLUG = 'prague';

interface PageParams {
  params: Promise<{ locale: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}

export async function generateMetadata({ params, searchParams }: PageParams): Promise<Metadata> {
  const { locale } = await params;
  return destinationMetadata({ destinationSlug: DESTINATION_SLUG, locale, searchParams: await searchParams });
}

export default async function Page({ params, searchParams }: PageParams) {
  const { locale } = await params;
  return DestinationCatalogPage({ destinationSlug: DESTINATION_SLUG, locale, searchParams: await searchParams });
}
