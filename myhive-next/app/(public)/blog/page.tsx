// SSR blog hub — content parity with legacy-src/pages/BlogPage.js markup/classes,
// rendered as a Server Component so crawlers get the post list as real HTML.
import Link from 'next/link';
import { api } from '../../../lib/api';
import { pageMetadata } from '../../../lib/seo';
import '../../../legacy-src/pages/BlogPage.css';

export const revalidate = 3600;

const TITLE = 'Stag Do Planning Guides & Ideas | Trivlu Blog';
const DESCRIPTION =
  'Guides, ideas and destination tips for planning the perfect stag do — from budgeting to the best cities in Europe.';

export const metadata = pageMetadata({
  title: TITLE,
  description: DESCRIPTION,
  path: '/blog',
});

function displayDate(post: { publishedAt?: string | null; createdAt?: string | null }) {
  const raw = post.publishedAt || post.createdAt;
  if (!raw) return null;
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return null;
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' });
}

export default async function BlogPage() {
  const posts = (await api.getBlogPosts().catch(() => null)) ?? [];

  return (
    <div className="blog-page">
      <section className="blog-section">
        <h1>The Stag Do Playbook</h1>
        {posts.length === 0 ? (
          <p style={{ color: 'var(--text-muted)' }}>No blog posts yet. Check back soon!</p>
        ) : (
          <div className="blog-grid">
            {posts.map((post) => {
              const date = displayDate(post);
              return (
                <Link
                  key={post.id}
                  href={`/blog/${post.slug || post.id}`}
                  className="card blog-card"
                >
                  {post.imageUrl && (
                    <img src={post.imageUrl} alt={post.title} className="blog-card-image" />
                  )}
                  <div className="blog-card-content">
                    {post.category && (
                      <span className="blog-card-category">{post.category}</span>
                    )}
                    <h3 className="blog-card-title">{post.title}</h3>
                    {post.excerpt && <p className="blog-card-excerpt">{post.excerpt}</p>}
                    {date && <span className="blog-card-date">{date}</span>}
                  </div>
                </Link>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}
