import { useCallback, useEffect, useState } from 'react';
import './SwipeCard.css';
import ActivityPreviewModal from './ActivityPreviewModal';
import { copyToClipboard } from '../utils/clipboard';
import { formatPricePerPerson, groupMinNote, hasGroupMin } from '../utils/format';

const SWIPE_THRESHOLD = 80;

function SwipeCard({ cards, currentIndex, onSwipe, onUndo, canUndo, title, subtitle, shareUrl, getCardLink }) {
    const [drag, setDrag] = useState({ active: false, startX: 0, offsetX: 0 });
    const [copied, setCopied] = useState(false);
    const [infoCard, setInfoCard] = useState(null);

    // Warm the browser cache a few cards ahead so a swipe reveals an already-loaded
    // photo instead of the previous card's image lingering while the next downloads.
    useEffect(() => {
        cards.slice(currentIndex + 1, currentIndex + 4).forEach(upcoming => {
            if (upcoming.imageUrl) {
                new Image().src = upcoming.imageUrl;
            }
        });
    }, [cards, currentIndex]);

    const handleCopy = useCallback(() => {
        copyToClipboard(shareUrl).then(ok => {
            if (ok) {
                setCopied(true);
                setTimeout(() => setCopied(false), 2000);
            }
        });
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
        // Read the current drag state synchronously (no updater) so the side
        // effect onSwipe(...) fires exactly once. Calling onSwipe inside a
        // setDrag updater triggers double-invocation under React.StrictMode
        // in dev and causes counters/picked-lists to jump by 2.
        if (!drag.active) {
            return;
        }
        if (Math.abs(drag.offsetX) > SWIPE_THRESHOLD) {
            onSwipe(drag.offsetX > 0 ? 'right' : 'left', cardId);
        }
        setDrag({ active: false, startX: 0, offsetX: 0 });
    }, [drag.active, drag.offsetX, onSwipe]);

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
    const renderName = (name) => (
        <button
            type="button"
            className="swipe-card-link"
            aria-haspopup="dialog"
            onPointerDown={(e) => e.stopPropagation()}
            onPointerUp={(e) => e.stopPropagation()}
            onClick={(e) => {
                e.stopPropagation();
                setInfoCard(card);
            }}
        >{name}</button>
    );

    return (
        <div className="swipe-card-page">
            {title && <h2 className="swipe-card-title">{title}</h2>}
            {subtitle && <p className="swipe-card-subtitle">{subtitle}</p>}
            <div className="swipe-card-progress">
                {currentIndex + 1} / {cards.length}
            </div>

            <div className="swipe-card-stack">
                {/* Both stack layers are keyed by activity id: after a swipe React promotes the
                    already-rendered (and already-loaded) underlay to the top instead of reusing
                    the top <img> with a swapped src — which kept showing the previous photo
                    until the new one finished downloading. */}
                {nextCard && (
                    <div key={nextCard.id} className="swipe-tinder-card" style={{ transform: 'scale(0.95)', zIndex: 0 }}>
                        <div className="swipe-card">
                            {nextCard.imageUrl
                                ? <>
                                    <img src={nextCard.imageUrl} alt={nextCard.name} className="swipe-card-image"
                                         draggable={false} decoding="async" />
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
                    key={card.id}
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
                                <img src={card.imageUrl} alt={card.name} className="swipe-card-image"
                                     draggable={false} decoding="async" />
                                <div className="swipe-card-info">
                                    <div className="swipe-card-name">{renderName(card.name)}</div>
                                    <div className="swipe-card-meta">
                                        {card.duration && <span>{Math.round(card.duration / 60)}h</span>}
                                        {card.duration && card.price && <span> · </span>}
                                        {card.price && (
                                            <span>
                                                {hasGroupMin(card)
                                                    ? `from ${formatPricePerPerson(card.price)}`
                                                    : formatPricePerPerson(card.price)}
                                            </span>
                                        )}
                                        {card.price && hasGroupMin(card) && (
                                            <span> · {groupMinNote(card)}</span>
                                        )}
                                    </div>
                                </div>
                            </>
                            : <div className="swipe-card-text-only">
                                <div className="swipe-card-name">{renderName(card.name)}</div>
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
                {onUndo && (
                    <button
                        className="swipe-btn swipe-btn-undo"
                        onClick={onUndo}
                        disabled={!canUndo}
                        aria-label="Undo last swipe"
                    >
                        {/* Phosphor icon, not unicode ↩ — iOS renders that codepoint as the emoji arrow */}
                        <i className="ph ph-arrow-counter-clockwise" aria-hidden="true"/>
                    </button>
                )}
                <button
                    className="swipe-btn swipe-btn-like"
                    onClick={() => handleButtonSwipe('right')}
                    aria-label="Like"
                >♥</button>
            </div>

            {shareUrl && (
                <div className="swipe-share">
                    <button className="swipe-share-btn" onClick={handleCopy}>
                        {copied ? '✓ Link Copied!' : 'Copy Invite Link'}
                    </button>
                </div>
            )}

            <ActivityPreviewModal
                activity={infoCard}
                link={infoCard && getCardLink ? getCardLink(infoCard) : null}
                onClose={() => setInfoCard(null)}
            />
        </div>
    );
}

export default SwipeCard;
