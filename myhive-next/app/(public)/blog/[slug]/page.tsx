// SSR blog post — content parity with legacy-src/pages/BlogPostPage.js. Legacy
// renders post.content as plain text split on newlines into <p> blocks (NOT
// HTML), so we mirror that exactly — no dangerouslySetInnerHTML.
import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { api, type BlogPost } from '../../../../lib/api';
import { SITE_URL, canonical, breadcrumbJsonLd, jsonLd } from '../../../../lib/seo';
import '../../../../legacy-src/pages/BlogPostPage.css';

export const revalidate = 3600;

function displayDate(post: BlogPost) {
  const raw = post.publishedAt || post.createdAt;
  if (!raw) return null;
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return null;
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' });
}

function summarize(post: BlogPost) {
  if (post.excerpt) return post.excerpt;
  const text = (post.content || '').replace(/\s+/g, ' ').trim();
  return text.length > 155 ? `${text.slice(0, 152).trimEnd()}...` : text;
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  const post = await api.getBlogPostBySlug(slug);
  if (!post) return {};

  const title = `${post.title} | Trivlu Blog`;
  const description = summarize(post);
  const url = canonical(`/blog/${post.slug || slug}`);

  return {
    title,
    description,
    alternates: { canonical: url },
    openGraph: {
      title,
      description,
      url,
      type: 'article',
      images: post.imageUrl ? [{ url: post.imageUrl }] : undefined,
    },
  };
}

export default async function BlogPostPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const post = await api.getBlogPostBySlug(slug);
  if (!post) notFound();

  const date = displayDate(post);
  const paragraphs = post.content
    ? post.content.split('\n').filter((p) => p.trim())
    : [];

  const articleJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Article',
    headline: post.title,
    datePublished: post.publishedAt || post.createdAt || undefined,
    image: post.imageUrl || undefined,
    author: { '@type': 'Organization', name: 'Trivlu' },
    publisher: {
      '@type': 'Organization',
      name: 'Trivlu',
      logo: { '@type': 'ImageObject', url: `${SITE_URL}/logo-trivlu.png` },
    },
    mainEntityOfPage: canonical(`/blog/${post.slug || slug}`),
  };

  const breadcrumbs = breadcrumbJsonLd([
    ['Home', '/'],
    ['Blog', '/blog'],
    [post.title, `/blog/${post.slug || slug}`],
  ]);

  return (
    <div className="blog-post-page">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonLd(articleJsonLd) }}
      />
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonLd(breadcrumbs) }}
      />

      {post.imageUrl && (
        <div
          className="blog-post-hero"
          style={{ backgroundImage: `url(${post.imageUrl})` }}
        >
          <div className="blog-post-hero-overlay">
            {post.category && <span className="blog-post-category">{post.category}</span>}
            <h1>{post.title}</h1>
            {date && <span className="blog-post-date">{date}</span>}
          </div>
        </div>
      )}

      <article className="blog-post-container">
        {!post.imageUrl && (
          <>
            {post.category && <span className="blog-post-category">{post.category}</span>}
            <h1>{post.title}</h1>
            {date && <span className="blog-post-date">{date}</span>}
          </>
        )}
        {paragraphs.map((paragraph, index) => (
          <p key={index}>{paragraph}</p>
        ))}

        <div className="blog-post-back">
          <Link href="/blog" className="btn btn--primary">
            Back to Blog
          </Link>
        </div>
      </article>
    </div>
  );
}
