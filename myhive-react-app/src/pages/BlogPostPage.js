import {Helmet} from 'react-helmet-async';
import {Link, useParams} from 'react-router-dom';
import api from '../services/api';
import {useFetchBySlug} from '../hooks/useFetchBySlug';
import {SITE_URL} from '../services/config';
import MarkdownContent from '../components/MarkdownContent';
import './BlogPostPage.css';

function BlogPostPage() {
    const {slug} = useParams();
    const {data: post, loading, error} = useFetchBySlug(api.getBlogPostBySlug, slug);

    if (loading) {
        return (
            <div className="blog-post-page">
                <div className="blog-post-container">
                    <p className="text-center">Loading...</p>
                </div>
            </div>
        );
    }

    if (error || !post) {
        return (
            <div className="blog-post-page">
                <div className="blog-post-container">
                    <h1>Post not found</h1>
                    <Link to="/blog" className="btn btn--primary">Back to Blog</Link>
                </div>
            </div>
        );
    }

    return (
        <div className="blog-post-page">
            <Helmet>
                <title>{post.title} — Trivlu Blog</title>
                <meta name="description" content={post.excerpt || post.title}/>
                <link rel="canonical" href={`${SITE_URL}/blog/${post.slug}`}/>
            </Helmet>
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
                    <Link to="/blog" className="btn btn--primary">Back to Blog</Link>
                </div>
            </article>
        </div>
    );
}

export default BlogPostPage;
