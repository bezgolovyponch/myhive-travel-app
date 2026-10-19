import type { Metadata } from 'next';
import { ActivityDetailPage, activityMetadata } from '@/components/site/catalog/ActivityDetail';

export const revalidate = 3600;

type Props = { params: Promise<{ locale: string; slug: string; aslug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, slug, aslug } = await params;
  return activityMetadata({ destinationSlug: slug, activitySlug: aslug, locale });
}

export default async function Page({ params }: Props) {
  const { locale, slug, aslug } = await params;
  return ActivityDetailPage({ destinationSlug: slug, activitySlug: aslug, locale });
}
