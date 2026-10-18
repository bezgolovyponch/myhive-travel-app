import type { Metadata } from 'next';
import { PackageDetailPage, packageMetadata } from '@/components/site/catalog/PackageDetail';

export const revalidate = 3600;

// Folder name = DESTINATION_HUBS.prague in legacy-src/i18n/routes.js (the DE
// spelling /jga-prag is rewritten here by next-intl pathnames). A second hub
// city gets its own folder + table row.
const DESTINATION_SLUG = 'prague';

type Props = { params: Promise<{ locale: string; pslug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, pslug } = await params;
  return packageMetadata({ destinationSlug: DESTINATION_SLUG, packageSlug: pslug, locale });
}

export default async function Page({ params }: Props) {
  const { locale, pslug } = await params;
  return PackageDetailPage({ destinationSlug: DESTINATION_SLUG, packageSlug: pslug, locale });
}
