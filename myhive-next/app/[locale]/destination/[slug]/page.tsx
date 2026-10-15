import type { Metadata } from 'next';
import { DestinationCatalogPage, destinationMetadata } from '@/components/site/catalog/DestinationCatalog';

interface PageParams {
  params: Promise<{ locale: string; slug: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}

export async function generateMetadata({ params, searchParams }: PageParams): Promise<Metadata> {
  const { locale, slug } = await params;
  return destinationMetadata({ destinationSlug: slug, locale, searchParams: await searchParams });
}

export default async function Page({ params, searchParams }: PageParams) {
  const { locale, slug } = await params;
  return DestinationCatalogPage({ destinationSlug: slug, locale, searchParams: await searchParams });
}
