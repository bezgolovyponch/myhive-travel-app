import { useCallback, useEffect, useState } from 'react';
import './SwipeCard.css';

function DebugOverlay() {
    const [info, setInfo] = useState({});
    useEffect(() => {
        const update = () => {
            const header = document.querySelector('.header');
            const headerRect = header ? header.getBoundingClientRect() : {};
            const vv = window.visualViewport;
            setInfo({
                vw: window.innerWidth,
                docW: document.documentElement.clientWidth,
                bodyW: document.body.scrollWidth,
                vvW: vv ? Math.round(vv.width) : 'none',
                vvScale: vv ? vv.scale.toFixed(3) : 'none',
                vvOffsetL: vv ? Math.round(vv.offsetLeft) : 'none',
                vvPageL: vv ? Math.round(vv.pageLeft) : 'none',
                hdrW: Math.round(headerRect.width || 0),
                hdrL: Math.round(headerRect.left || 0),
                hdrR: Math.round(headerRect.right || 0),
                dpr: window.devicePixelRatio,
            });
        };
        update();
        window.addEventListener('resize', update);
        if (window.visualViewport) {
            window.visualViewport.addEventListener('resize', update);
            window.visualViewport.addEventListener('scroll', update);
        }
        const t = setInterval(update, 500);
        return () => {
            window.removeEventListener('resize', update);
            if (window.visualViewport) {
                window.visualViewport.removeEventListener('resize', update);
                window.visualViewport.removeEventListener('scroll', update);
            }
            clearInterval(t);
        };
    }, []);
    return (
        <div style={{
            position: 'fixed', top: 'calc(var(--header-height) + 4px)', left: 4, zIndex: 9999,
            background: 'rgba(0,0,0,0.85)', color: '#0f0', font: '11px monospace',
            padding: '6px 8px', borderRadius: 4, lineHeight: 1.3,
        }}>
            vw:{info.vw} docW:{info.docW} bodyW:{info.bodyW}<br/>
            vvW:{info.vvW} scale:{info.vvScale}<br/>
            vvOffL:{info.vvOffsetL} vvPageL:{info.vvPageL}<br/>
            hdrW:{info.hdrW} hdrL:{info.hdrL} hdrR:{info.hdrR}<br/>
            dpr:{info.dpr}
        </div>
    );
}

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
            <DebugOverlay />
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
                    <button className="swipe-share-btn" onClick={handleCopy}>
                        {copied ? '✓ Link Copied!' : 'Copy Invite Link'}
                    </button>
                </div>
            )}
        </div>
    );
}

export default SwipeCard;
