import './ReviewsSection.css';

// Hardcoded until real reviews exist; replace content only, keep the shape.
const REVIEWS = [
    {
        quote: "Easiest stag do I've ever organised. The lads voted, Trivlu sorted the rest — all I did was show up.",
        name: 'James W.',
        country: 'United Kingdom',
    },
    {
        quote: 'Booked shooting, karting and a boat party for 14 guys. Zero chaos, brilliant weekend.',
        name: 'Connor M.',
        country: 'Ireland',
    },
    {
        quote: 'The group vote ended every argument in the group chat. 10/10, would use again.',
        name: 'Mark D.',
        country: 'United Kingdom',
    },
    {
        quote: 'Great communication and the itinerary was spot on. The deposit system made paying painless.',
        name: 'Tom V.',
        country: 'Netherlands',
    },
];

function initials(name) {
    return name.split(' ').map(part => part[0]).join('').toUpperCase();
}

function ReviewsSection({onStartVote}) {
    return (
        <section className="reviews-section">
            <h2 className="section-title reviews-title">What the Lads Say</h2>
            <p className="section-subtitle reviews-subtitle">Real reviews from real stag dos.</p>
            <div className="reviews-grid">
                {REVIEWS.map(review => (
                    <div key={review.name} className="review-card">
                        <div className="review-stars" aria-label="5 out of 5 stars">★★★★★</div>
                        <blockquote className="review-quote">"{review.quote}"</blockquote>
                        <div className="review-author">
                            <span className="review-avatar" aria-hidden="true">{initials(review.name)}</span>
                            <div>
                                <div className="review-name">{review.name}</div>
                                <div className="review-country">{review.country}</div>
                            </div>
                        </div>
                    </div>
                ))}
            </div>
            <button className="btn btn--primary btn--lg" onClick={onStartVote}>
                Build Your Trip
            </button>
        </section>
    );
}

export default ReviewsSection;
