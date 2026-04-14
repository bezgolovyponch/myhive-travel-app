import {useEffect, useState} from 'react';
import {Helmet} from 'react-helmet-async';
import {Link, useNavigate, useParams} from 'react-router-dom';
import api from '../services/api';
import {SITE_URL} from '../services/config';
import './BlogPostPage.css';

function BlogPostPage() {
    const {slug} = useParams();
    const navigate = useNavigate();
    const [post, setPost] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(slug);

    useEffect(() => {
        const fetchPost = isUuid
            ? api.getBlogPost(slug)
            : api.getBlogPostBySlug(slug);
        fetchPost
            .then(data => {
                if (isUuid && data.slug) {
                    navigate(`/blog/${data.slug}`, {replace: true});
                } else {
                    setPost(data);
                }
            })
            .catch(() => setError(true))
            .finally(() => setLoading(false));
    }, [slug, isUuid, navigate]);

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

    const paragraphs = post.content ? post.content.split('\n').filter(p => p.trim()) : [];

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
                {paragraphs.map((paragraph, index) => (
                    <p key={index}>{paragraph}</p>
                ))}

                <div className="blog-post-back">
                    <Link to="/blog" className="btn btn--primary">Back to Blog</Link>
                </div>
            </article>
        </div>
    );
}

export default BlogPostPage;
