// SSR blog hub — a thin server shell: metadata plus the post list. The markup is
// the canonical CRA page (legacy-src/pages/BlogPage.js), which fetches in an
// effect no crawler runs, so the posts are injected instead.
import type { Metadata } from 'next';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import { api } from '@/lib/api';
import { pageMetadata } from '@/lib/seo';
import LegacyBlog from '@/components/site/legacy/LegacyBlog';

export const revalidate = 3600;

type Props = { params: Promise<{ locale: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'meta.blog' });
  return pageMetadata({
    title: t('title'),
    description: t('description'),
    path: '/blog',
    locale,
  });
}

export default async function Page({ params }: Props) {
  const { locale } = await params;
  setRequestLocale(locale);
  const posts = (await api.getBlogPosts().catch(() => null)) ?? [];

  return <LegacyBlog posts={posts} />;
}
