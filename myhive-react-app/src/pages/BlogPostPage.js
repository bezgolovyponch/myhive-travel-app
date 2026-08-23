import PageHead from '../components/PageHead';
import {Link, useParams} from 'react-router-dom';
import api from '../services/api';
import {useFetchBySlug} from '../hooks/useFetchBySlug';
import {SITE_URL} from '../services/config';
import MarkdownContent from '../components/MarkdownContent';
import {useT} from '../i18n';
import './BlogPostPage.css';

// `post` is supplied by the server renderer (Next.js SSR) so the article is in
// the initial HTML; omitted in the SPA, which fetches by slug as before.
function BlogPostPage({post: injectedPost}) {
    const t = useT('blog');
    const tMeta = useT('meta');
    const {slug} = useParams();
    const {data: post, loading, error} = useFetchBySlug(api.getBlogPostBySlug, slug, injectedPost);

    if (loading) {
        return (
            <div className="blog-post-page">
                <div className="blog-post-container">
                    <p className="text-center">{t('loading')}</p>
                </div>
            </div>
        );
    }

    if (error || !post) {
        return (
            <div className="blog-post-page">
                <div className="blog-post-container">
                    <h1>{t('postNotFound')}</h1>
                    <Link to="/blog" className="btn btn--primary">{t('backToBlog')}</Link>
                </div>
            </div>
        );
    }

    return (
        <div className="blog-post-page">
            <PageHead>
                <title>{tMeta('blogPost.title', {title: post.title})}</title>
                <meta name="description" content={post.excerpt || post.title}/>
                <link rel="canonical" href={`${SITE_URL}/blog/${post.slug}`}/>
            </PageHead>
            {post.imageUrl && (
                <div className="blog-post-hero" style={{backgroundImage: `url(${post.imageUrl})`}}>
                    <div className="blog-post-hero-overlay">
                        {post.category && <span className="blog-post-category">{post.category}</span>}
                        <h1>{post.title}</h1>
                        {post.date && <span className="blog-post-date">{post.date}</span>}
                    </div>
                </div>
            )}

            <article className="blog-post-container">
                {!post.imageUrl && (
                    <>
                        {post.category && <span className="blog-post-category">{post.category}</span>}
                        <h1>{post.title}</h1>
                        {post.date && <span className="blog-post-date">{post.date}</span>}
                    </>
                )}
                <MarkdownContent>{post.content}</MarkdownContent>

                <div className="blog-post-back">
                    <Link to="/blog" className="btn btn--primary">{t('backToBlog')}</Link>
                </div>
            </article>
        </div>
    );
}

export default BlogPostPage;
