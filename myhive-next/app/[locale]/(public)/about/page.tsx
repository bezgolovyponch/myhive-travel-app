// SSR About page — a thin server shell: metadata only. The markup is the canonical
// CRA page (legacy-src/pages/AboutPage.js), so this route cannot drift from what the SPA renders.
import type { Metadata } from 'next';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import { pageMetadata } from '@/lib/seo';
import LegacyAbout from '@/components/site/legacy/LegacyAbout';

type Props = { params: Promise<{ locale: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'meta.about' });
  return pageMetadata({
    title: t('title'),
    description: t('description'),
    path: '/about',
    locale,
  });
}

export default async function Page({ params }: Props) {
  const { locale } = await params;
  setRequestLocale(locale);
  return <LegacyAbout />;
}
