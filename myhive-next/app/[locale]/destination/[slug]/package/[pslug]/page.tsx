import type { Metadata } from 'next';
import { PackageDetailPage, packageMetadata } from '@/components/site/catalog/PackageDetail';

export const revalidate = 3600;

type Props = { params: Promise<{ locale: string; slug: string; pslug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, slug, pslug } = await params;
  return packageMetadata({ destinationSlug: slug, packageSlug: pslug, locale });
}

export default async function Page({ params }: Props) {
  const { locale, slug, pslug } = await params;
  return PackageDetailPage({ destinationSlug: slug, packageSlug: pslug, locale });
}
