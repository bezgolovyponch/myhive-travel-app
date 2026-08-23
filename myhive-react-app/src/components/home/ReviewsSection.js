import {pushEvent} from '../../utils/analytics';
import {useT} from '../../i18n';
import './ReviewsSection.css';

// Hardcoded until real reviews exist; replace content only, keep the shape.
// Reviewer names are proper nouns and stay untranslated.
const REVIEWS = [
    {
        quoteKey: 'reviews.r1Quote',
        name: 'James W.',
        countryKey: 'reviews.r1Country',
    },
    {
        quoteKey: 'reviews.r2Quote',
        name: 'Connor M.',
        countryKey: 'reviews.r2Country',
    },
    {
        quoteKey: 'reviews.r3Quote',
        name: 'Mark D.',
        countryKey: 'reviews.r3Country',
    },
    {
        quoteKey: 'reviews.r4Quote',
        name: 'Tom V.',
        countryKey: 'reviews.r4Country',
    },
];

function initials(name) {
    return name.split(' ').map(part => part[0]).join('').toUpperCase();
}

function ReviewsSection({onStartVote}) {
    const t = useT('home');
    return (
        <section className="reviews-section">
            <h2 className="section-title reviews-title">{t('reviews.heading')}</h2>
            <p className="section-subtitle reviews-subtitle">{t('reviews.subtitle')}</p>
            <div className="reviews-grid">
                {REVIEWS.map(review => (
                    <div key={review.name} className="review-card">
                        <div className="review-stars" role="img" aria-label={t('reviews.starsLabel')}>★★★★★</div>
                        <blockquote className="review-quote">"{t(review.quoteKey)}"</blockquote>
                        <div className="review-author">
                            <span className="review-avatar" aria-hidden="true">{initials(review.name)}</span>
                            <div>
                                <div className="review-name">{review.name}</div>
                                <div className="review-country">{t(review.countryKey)}</div>
                            </div>
                        </div>
                    </div>
                ))}
            </div>
            <button
                className="btn btn--primary btn--lg"
                onClick={() => {
                    pushEvent('cta_click', {cta_label: 'Start Group Vote', block: 'reviews'});
                    onStartVote();
                }}
            >
                {t('reviews.startVoteCta')}
            </button>
        </section>
    );
}

export default ReviewsSection;
