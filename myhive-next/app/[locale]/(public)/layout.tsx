import { setRequestLocale } from 'next-intl/server';
import PublicChrome from '@/components/site/PublicChrome';

export default async function PublicLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale);
  return <PublicChrome locale={locale}>{children}</PublicChrome>;
}
