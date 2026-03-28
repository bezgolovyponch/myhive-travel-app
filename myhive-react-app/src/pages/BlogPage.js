import {Link} from 'react-router-dom';

function BlogPage() {
    const posts = [
        {
            id: 1,
            title: 'Top 5 Group Travel Destinations for 2026',
            excerpt: 'From the cobblestone streets of Prague to the volcanic landscapes of Tenerife, discover the hottest destinations for your next group adventure.',
            date: 'March 15, 2026',
            category: 'Destinations',
            image: 'https://images.unsplash.com/photo-1519677100203-a0e668c92439?w=600&h=400&fit=crop',
        },
        {
            id: 2,
            title: 'How to Plan a Stress-Free Group Trip',
            excerpt: 'Coordinating schedules, budgets, and preferences for a group can be overwhelming. Here are our top tips to keep everyone happy.',
            date: 'March 8, 2026',
            category: 'Tips',
            image: 'https://images.unsplash.com/photo-1539635278303-d4002c07eae3?w=600&h=400&fit=crop',
        },
        {
            id: 3,
            title: 'Why AI is Changing the Way We Travel Together',
            excerpt: 'Artificial intelligence is transforming group travel planning from a logistical nightmare into a seamless experience. Here\'s how.',
            date: 'February 28, 2026',
            category: 'Technology',
            image: 'https://images.unsplash.com/photo-1488646953014-85cb44e25828?w=600&h=400&fit=crop',
        },
        {
            id: 4,
            title: 'Bali on a Budget: A Group Travel Guide',
            excerpt: 'Think Bali is too expensive for a group getaway? Think again. We break down how to experience the Island of the Gods without breaking the bank.',
            date: 'February 20, 2026',
            category: 'Guides',
            image: 'https://images.unsplash.com/photo-1537996194471-e657df975ab4?w=600&h=400&fit=crop',
        },
        {
            id: 5,
            title: 'The Rise of Multi-Traveler Experiences',
            excerpt: 'Solo travel had its moment. Now, shared experiences are taking center stage. Explore why traveling with others is the new trend.',
            date: 'February 12, 2026',
            category: 'Trends',
            image: 'https://images.unsplash.com/photo-1530789253388-582c481c54b0?w=600&h=400&fit=crop',
        },
        {
            id: 6,
            title: 'Weekend Getaway Ideas for Large Groups',
            excerpt: 'Planning a weekend escape for 10+ people? These destinations and activities are perfect for big groups looking for adventure.',
            date: 'February 5, 2026',
            category: 'Destinations',
            image: 'https://images.unsplash.com/photo-1506197603052-3cc9c3a201bd?w=600&h=400&fit=crop',
        },
    ];

    return (
        <div className="blog-page">
            <section className="blog-hero">
                <h1>Blog</h1>
                <p>Stories, tips, and inspiration for your next group adventure.</p>
            </section>

            <section className="blog-section">
                <div className="blog-grid">
                    {posts.map(post => (
                        <Link key={post.id} to={`/blog/${post.id}`} className="blog-card">
                            <img src={post.image} alt={post.title} className="blog-card-image"/>
                            <div className="blog-card-content">
                                <span className="blog-card-category">{post.category}</span>
                                <h3 className="blog-card-title">{post.title}</h3>
                                <p className="blog-card-excerpt">{post.excerpt}</p>
                                <span className="blog-card-date">{post.date}</span>
                            </div>
                        </Link>
                    ))}
                </div>
            </section>
        </div>
    );
}

export default BlogPage;
