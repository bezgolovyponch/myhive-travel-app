import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import api from '../services/api';
import './BlogPage.css';

function BlogPage() {
    const [posts, setPosts] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        api.getBlogPosts()
            .then(data => setPosts(data))
            .catch(() => setPosts([]))
            .finally(() => setLoading(false));
    }, []);

    return (
        <div className="blog-page">
            <section className="page-hero">
                <h1>Blog</h1>
                <p>Stories, tips, and inspiration for your next group adventure.</p>
            </section>

            <section className="blog-section">
                {loading ? (
                    <p style={{textAlign: 'center', color: 'var(--text-muted)'}}>Loading posts...</p>
                ) : posts.length === 0 ? (
                    <p style={{textAlign: 'center', color: 'var(--text-muted)'}}>No blog posts yet. Check back
                        soon!</p>
                ) : (
                    <div className="blog-grid">
                        {posts.map(post => (
                            <Link key={post.id} to={`/blog/${post.id}`} className="card blog-card">
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
