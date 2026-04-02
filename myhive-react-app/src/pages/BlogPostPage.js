import {useEffect, useState} from 'react';
import {Link, useParams} from 'react-router-dom';
import api from '../services/api';
import './BlogPostPage.css';

function BlogPostPage() {
    const {id} = useParams();
    const [post, setPost] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);

    useEffect(() => {
        api.getBlogPost(id)
            .then(data => setPost(data))
            .catch(() => setError(true))
            .finally(() => setLoading(false));
    }, [id]);

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
