import { useCallback, useState } from 'react';
import './SwipeCard.css';

const SWIPE_THRESHOLD = 80;

function SwipeCard({ cards, currentIndex, onSwipe, title, subtitle, shareUrl }) {
    const [drag, setDrag] = useState({ active: false, startX: 0, offsetX: 0 });
    const [copied, setCopied] = useState(false);

    const handleCopy = useCallback(() => {
        navigator.clipboard.writeText(shareUrl);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    }, [shareUrl]);

    const handlePointerDown = useCallback((e) => {
        e.currentTarget.setPointerCapture(e.pointerId);
        setDrag({ active: true, startX: e.clientX, offsetX: 0 });
    }, []);

    const handlePointerMove = useCallback((e) => {
        setDrag(prev => {
            if (!prev.active) return prev;
            return { ...prev, offsetX: e.clientX - prev.startX };
        });
    }, []);

    const handlePointerUp = useCallback((cardId) => {
        setDrag(prev => {
            if (!prev.active) return prev;
            if (Math.abs(prev.offsetX) > SWIPE_THRESHOLD) {
                onSwipe(prev.offsetX > 0 ? 'right' : 'left', cardId);
            }
            return { active: false, startX: 0, offsetX: 0 };
        });
    }, [onSwipe]);

    const handleButtonSwipe = (direction) => {
        if (currentIndex < cards.length) {
            onSwipe(direction, cards[currentIndex].id);
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
    const nextCard = cards[currentIndex + 1];
    const rotate = drag.offsetX * 0.08;
    const overlayOpacity = Math.min(Math.abs(drag.offsetX) / SWIPE_THRESHOLD, 1);

    return (
        <div className="swipe-card-page">
            {title && <h2 className="swipe-card-title">{title}</h2>}
            {subtitle && <p className="swipe-card-subtitle">{subtitle}</p>}
            <div className="swipe-card-progress">
                {currentIndex + 1} / {cards.length}
            </div>

            <div className="swipe-card-stack">
                {nextCard && (
                    <div className="swipe-tinder-card" style={{ transform: 'scale(0.95)', zIndex: 0 }}>
                        <div className="swipe-card">
                            {nextCard.imageUrl
                                ? <>
                                    <img src={nextCard.imageUrl} alt={nextCard.name} className="swipe-card-image" />
                                    <div className="swipe-card-info">
                                        <div className="swipe-card-name">{nextCard.name}</div>
                                    </div>
                                </>
                                : <div className="swipe-card-text-only">
                                    <div className="swipe-card-name">{nextCard.name}</div>
                                </div>
                            }
                        </div>
                    </div>
                )}

                <div
                    className="swipe-tinder-card"
                    style={{
                        transform: `translateX(${drag.offsetX}px) rotate(${rotate}deg)`,
                        transition: drag.active ? 'none' : 'transform 0.3s ease',
                        zIndex: 1,
                    }}
                    onPointerDown={handlePointerDown}
                    onPointerMove={handlePointerMove}
                    onPointerUp={() => handlePointerUp(card.id)}
                    onPointerCancel={() => handlePointerUp(card.id)}
                >
                    <div className="swipe-card">
                        {card.imageUrl
                            ? <>
                                <img src={card.imageUrl} alt={card.name} className="swipe-card-image" />
                                <div className="swipe-card-info">
                                    <div className="swipe-card-name">{card.name}</div>
                                    <div className="swipe-card-meta">
                                        {card.duration && <span>{Math.round(card.duration / 60)}h</span>}
                                        {card.duration && card.price && <span> · </span>}
                                        {card.price && <span>€{card.price}/person</span>}
                                    </div>
                                </div>
                            </>
                            : <div className="swipe-card-text-only">
                                <div className="swipe-card-name">{card.name}</div>
                            </div>
                        }
                        <div
                            className="swipe-overlay swipe-overlay-like"
                            style={{ opacity: drag.offsetX > 20 ? overlayOpacity : 0 }}
                        >LIKE ♥</div>
                        <div
                            className="swipe-overlay swipe-overlay-dislike"
                            style={{ opacity: drag.offsetX < -20 ? overlayOpacity : 0 }}
                        >NOPE ✕</div>
                    </div>
                </div>
            </div>

            <div className="swipe-buttons">
                <button
                    className="swipe-btn swipe-btn-dislike"
                    onClick={() => handleButtonSwipe('left')}
                    aria-label="Dislike"
                >✕</button>
                <button
                    className="swipe-btn swipe-btn-like"
                    onClick={() => handleButtonSwipe('right')}
                    aria-label="Like"
                >♥</button>
            </div>

            {shareUrl && (
                <div className="swipe-share">
                    <span className="swipe-share-label">Share with friends:</span>
                    <div className="swipe-share-row">
                        <span className="swipe-share-url">{shareUrl}</span>
                        <button className="swipe-share-btn" onClick={handleCopy}>
                            {copied ? 'Copied!' : 'Copy'}
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}

export default SwipeCard;
