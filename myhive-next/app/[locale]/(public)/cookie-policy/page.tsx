// SSR Cookie Policy — a thin server shell: metadata only. The markup is the canonical
// CRA page (legacy-src/pages/CookiePolicyPage.js), so this route cannot drift from what the SPA renders.
// Legal copy is deliberately not localized (translated: false): a translation
// would need legal review, so /de/cookie-policy serves the English document
// with a canonical pointing at /cookie-policy.
import type { Metadata } from 'next';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import { pageMetadata } from '@/lib/seo';
import LegacyCookie from '@/components/site/legacy/LegacyCookie';

type Props = { params: Promise<{ locale: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'meta.legal' });
  return pageMetadata({
    title: t('cookieTitle'),
    description: t('cookieDescription'),
    path: '/cookie-policy',
    locale,
    translated: false,
  });
}

export default async function Page({ params }: Props) {
  const { locale } = await params;
  setRequestLocale(locale);
  return <LegacyCookie />;
}
