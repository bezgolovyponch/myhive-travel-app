// SSR Refund & Cancellation Policy — a thin server shell: metadata only. The markup is the canonical
// CRA page (legacy-src/pages/RefundPolicyPage.js), so this route cannot drift from what the SPA renders.
// Legal copy is deliberately not localized (translated: false): a translation
// would need legal review, so /de/refund-policy serves the English document
// with a canonical pointing at /refund-policy.
import type { Metadata } from 'next';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import { pageMetadata } from '@/lib/seo';
import LegacyRefund from '@/components/site/legacy/LegacyRefund';

type Props = { params: Promise<{ locale: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'meta.legal' });
  return pageMetadata({
    title: t('refundTitle'),
    description: t('refundDescription'),
    path: '/refund-policy',
    locale,
    translated: false,
  });
}

export default async function Page({ params }: Props) {
  const { locale } = await params;
  setRequestLocale(locale);
  return <LegacyRefund />;
}
