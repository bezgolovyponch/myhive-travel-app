import { useRef } from 'react';
import TinderCard from 'react-tinder-card';
import './SwipeCard.css';

function SwipeCard({ cards, currentIndex, onSwipe, title, subtitle }) {
    const refs = useRef(cards.map(() => null));

    const handleButtonSwipe = async (direction, index) => {
        if (refs.current[index]) {
            await refs.current[index].swipe(direction);
        }
    };

    if (currentIndex >= cards.length) {
        return (
            <div className="swipe-card-page">
                <div className="swipe-done">
                    <p>Processing your choices...</p>
                </div>
            </div>
        );
    }

    const card = cards[currentIndex];

    return (
        <div className="swipe-card-page">
            {title && <h2 className="swipe-card-title">{title}</h2>}
            {subtitle && <p className="swipe-card-subtitle">{subtitle}</p>}
            <div className="swipe-card-progress">
                {currentIndex + 1} / {cards.length}
            </div>

            <div className="swipe-card-stack">
                {cards.slice(currentIndex, currentIndex + 3).reverse().map((c, stackIdx) => {
                    const absoluteIndex = currentIndex + (2 - stackIdx);
                    return (
                        <TinderCard
                            key={c.id}
                            ref={el => { refs.current[absoluteIndex] = el; }}
                            onSwipe={dir => absoluteIndex === currentIndex && onSwipe(dir, c.id)}
                            preventSwipe={['up', 'down']}
                            className="swipe-tinder-card"
                        >
                            <div className="swipe-card">
                                {c.imageUrl
                                    ? <img src={c.imageUrl} alt={c.name} className="swipe-card-image" />
                                    : <div className="swipe-card-image-placeholder">🌍</div>
                                }
                                <div className="swipe-card-info">
                                    <div className="swipe-card-name">{c.name}</div>
                                    <div className="swipe-card-meta">
                                        {c.duration && <span>{Math.round(c.duration / 60)}h</span>}
                                        {c.duration && c.price && <span> · </span>}
                                        {c.price && <span>€{c.price}/person</span>}
                                    </div>
                                </div>
                                <div className="swipe-overlay swipe-overlay-like">LIKE ♥</div>
                                <div className="swipe-overlay swipe-overlay-dislike">NOPE ✕</div>
                            </div>
                        </TinderCard>
                    );
                })}
            </div>

            <div className="swipe-buttons">
                <button
                    className="swipe-btn swipe-btn-dislike"
                    onClick={() => handleButtonSwipe('left', currentIndex)}
                    aria-label="Dislike"
                >✕</button>
                <button
                    className="swipe-btn swipe-btn-like"
                    onClick={() => handleButtonSwipe('right', currentIndex)}
                    aria-label="Like"
                >♥</button>
            </div>
        </div>
    );
}

export default SwipeCard;
