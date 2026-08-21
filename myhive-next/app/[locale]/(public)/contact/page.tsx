// SSR contact page — a thin server shell: metadata only. The markup is the canonical
// CRA page (legacy-src/pages/ContactPage.js), so this route cannot drift from what the SPA renders.
import type { Metadata } from 'next';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import { pageMetadata } from '@/lib/seo';
import LegacyContact from '@/components/site/legacy/LegacyContact';

type Props = { params: Promise<{ locale: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'meta.contact' });
  return pageMetadata({
    title: t('title'),
    description: t('description'),
    path: '/contact',
    locale,
  });
}

export default async function Page({ params }: Props) {
  const { locale } = await params;
  setRequestLocale(locale);
  return <LegacyContact />;
}
