// SSR blog post — a thin server shell: metadata, JSON-LD and the record fetch.
// The markup is the canonical CRA page (legacy-src/pages/BlogPostPage.js), which
// renders post.content as GFM markdown through the shared MarkdownContent
// renderer (react-markdown + remark-gfm), NOT dangerouslySetInnerHTML — raw HTML
// in content is not rendered and URLs go through react-markdown's sanitizer.
// Post fields arrive already localized for `locale` (backend translations
// column, English fallback per field).
import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import { api, type BlogPost } from '@/lib/api';
import { SITE_URL, canonical, breadcrumbJsonLd, jsonLd, pageMetadata } from '@/lib/seo';
import LegacyBlogPost from '@/components/site/legacy/LegacyBlogPost';

export const revalidate = 3600;

type Props = { params: Promise<{ locale: string; slug: string }> };

function summarize(post: BlogPost) {
  if (post.excerpt) return post.excerpt;
  const text = (post.content || '')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1') // keep link text, drop URL
    .replace(/[#*_>|`]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  return text.length > 155 ? `${text.slice(0, 152).trimEnd()}...` : text;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, slug } = await params;
  const post = await api.getBlogPostBySlug(slug, locale);
  if (!post) return {};

  const t = await getTranslations({ locale, namespace: 'meta.blogPost' });
  return pageMetadata({
    title: t('title', { title: post.title }),
    description: summarize(post),
    path: `/blog/${post.slug || slug}`,
    locale,
    image: post.imageUrl || undefined,
    ogType: 'article',
    noindex: !post.seoIndexable,
  });
}

export default async function Page({ params }: Props) {
  const { locale, slug } = await params;
  setRequestLocale(locale);
  const post = await api.getBlogPostBySlug(slug, locale);
  if (!post) notFound();

  const t = await getTranslations({ locale, namespace: 'common' });

  const articleJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Article',
    headline: post.title,
    datePublished: post.date || post.createdAt || undefined,
    image: post.imageUrl || undefined,
    author: { '@type': 'Organization', name: 'Trivlu' },
    publisher: {
      '@type': 'Organization',
      name: 'Trivlu',
      logo: { '@type': 'ImageObject', url: `${SITE_URL}/logo-trivlu.png` },
    },
    mainEntityOfPage: canonical(`/blog/${post.slug || slug}`, locale),
  };

  const breadcrumbs = breadcrumbJsonLd(
    [
      [t('home'), '/'],
      [t('blog'), '/blog'],
      [post.title, `/blog/${post.slug || slug}`],
    ],
    locale
  );

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonLd(articleJsonLd) }}
      />
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonLd(breadcrumbs) }}
      />
      <LegacyBlogPost post={post} />
    </>
  );
}
