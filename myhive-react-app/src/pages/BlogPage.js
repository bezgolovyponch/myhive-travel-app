import PageHead from '../components/PageHead';
import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import api from '../services/api';
import {SITE_URL} from '../services/config';
import {useT} from '../i18n';
import './BlogPage.css';

// `posts` is supplied by the server renderer (Next.js SSR) so the listing is in
// the initial HTML; omitted in the SPA, which fetches on mount as before.
function BlogPage({posts: injectedPosts}) {
    const t = useT('blog');
    const seeded = Array.isArray(injectedPosts);
    const [posts, setPosts] = useState(seeded ? injectedPosts : []);
    const [loading, setLoading] = useState(!seeded);

    useEffect(() => {
        if (seeded) {
            return;
        }
        api.getBlogPosts()
            .then(data => setPosts(data))
            .catch(() => setPosts([]))
            .finally(() => setLoading(false));
    }, [seeded]);

    return (
        <div className="blog-page">
            <PageHead>
                <title>Stag Do Planning Guides &amp; Ideas | Trivlu Blog</title>
                <meta name="description" content="Guides, ideas and destination tips for planning the perfect stag do — from budgeting to the best cities in Europe."/>
                <link rel="canonical" href={`${SITE_URL}/blog`}/>
            </PageHead>
            <section className="blog-section">
                {/* The listing had no h1 at all, which the SSR smoke checks flag
                    (exactly one per page). Kept inside .blog-section so the
                    existing layout is unchanged. */}
                <h1>{t('listTitle')}</h1>
                {loading ? (
                    <p style={{color: 'var(--text-muted)'}}>{t('loadingPosts')}</p>
                ) : posts.length === 0 ? (
                    <p style={{color: 'var(--text-muted)'}}>{t('emptyState')}</p>
                ) : (
                    <div className="blog-grid">
                        {posts.map(post => (
                            <Link key={post.id} to={`/blog/${post.slug || post.id}`} className="card blog-card">
                                {post.imageUrl && (
                                    <img src={post.imageUrl} alt={post.title} className="blog-card-image"/>
                                )}
                                <div className="blog-card-content">
                                    {post.category && <span className="blog-card-category">{post.category}</span>}
                                    <h3 className="blog-card-title">{post.title}</h3>
                                    {post.excerpt && <p className="blog-card-excerpt">{post.excerpt}</p>}
                                    {post.date && <span className="blog-card-date">{post.date}</span>}
                                </div>
                            </Link>
                        ))}
                    </div>
                )}
            </section>
        </div>
    );
}

export default BlogPage;
