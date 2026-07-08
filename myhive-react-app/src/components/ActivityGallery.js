import {useEffect, useState} from 'react';
import {useModalA11y} from '../hooks/useModalA11y';
import {DEFAULT_ACTIVITY_IMAGE} from '../utils/format';
import './ActivityGallery.css';

const MAX_THUMBS = 4;

// Hero photo gallery per the Activity_Trivlu mockup: one large photo plus up
// to four thumbnails (layout adapts via data-count), with a fullscreen
// lightbox (prev/next, thumbnail strip, arrow-key navigation).
function ActivityGallery({images, title}) {
    const [lightboxIndex, setLightboxIndex] = useState(null);
    const isOpen = lightboxIndex !== null;
    const contentRef = useModalA11y(isOpen, () => setLightboxIndex(null));

    const photos = [...new Set((images || []).filter(Boolean))];
    const thumbs = photos.slice(1, 1 + MAX_THUMBS);
    // The badge appears only when not every photo fits the hero (1 main + 4 thumbs).
    const showSeeAll = photos.length > 1 + MAX_THUMBS;

    useEffect(() => {
        if (!isOpen) {
            return;
        }
        const handleKey = (e) => {
            if (e.key === 'ArrowLeft') {
                setLightboxIndex(i => (i - 1 + photos.length) % photos.length);
            } else if (e.key === 'ArrowRight') {
                setLightboxIndex(i => (i + 1) % photos.length);
            }
        };
        document.addEventListener('keydown', handleKey);
        document.body.style.overflow = 'hidden';
        return () => {
            document.removeEventListener('keydown', handleKey);
            document.body.style.overflow = '';
        };
    }, [isOpen, photos.length]);

    if (photos.length === 0) {
        // Mockup data-count="0": single full-width image, no thumbnail grid, no lightbox.
        return (
            <div className="activity-gallery" data-count="0">
                <div className="ag-main ag-main--placeholder">
                    <img src={DEFAULT_ACTIVITY_IMAGE} alt={title}/>
                </div>
            </div>
        );
    }

    const step = (delta) => {
        setLightboxIndex(i => (i + delta + photos.length) % photos.length);
    };

    return (
        <div className="activity-gallery" data-count={thumbs.length}>
            <button
                type="button"
                className="ag-main"
                onClick={() => setLightboxIndex(0)}
                aria-label={`Open photo 1 of ${photos.length}`}
            >
                <img src={photos[0]} alt={title}/>
            </button>
            {thumbs.length > 0 && (
                <div className="ag-grid">
                    {thumbs.map((src, i) => (
                        <button
                            type="button"
                            className="ag-cell"
                            key={src}
                            onClick={() => setLightboxIndex(i + 1)}
                            aria-label={`Open photo ${i + 2} of ${photos.length}`}
                        >
                            <img src={src} alt={`${title} ${i + 2}`}/>
                            {showSeeAll && i === thumbs.length - 1 && (
                                <span className="ag-see-all">
                                    <i className="ph ph-images" aria-hidden="true"/> See all photos ({photos.length})
                                </span>
                            )}
                        </button>
                    ))}
                </div>
            )}

            {isOpen && (
                <div
                    className="ag-lightbox"
                    role="dialog"
                    aria-modal="true"
                    aria-label={`${title} photos`}
                    ref={contentRef}
                    onClick={(e) => {
                        if (e.target === e.currentTarget) {
                            setLightboxIndex(null);
                        }
                    }}
                >
                    <div className="ag-lb-top">
                        <span className="ag-lb-count">{lightboxIndex + 1} / {photos.length}</span>
                        <button
                            type="button"
                            className="ag-lb-close"
                            onClick={() => setLightboxIndex(null)}
                            aria-label="Close"
                        >
                            <i className="ph ph-x" aria-hidden="true"/>
                        </button>
                    </div>
                    <div className="ag-lb-stage">
                        {photos.length > 1 && (
                            <button type="button" className="ag-lb-nav" onClick={() => step(-1)} aria-label="Previous photo">
                                <i className="ph ph-caret-left" aria-hidden="true"/>
                            </button>
                        )}
                        <img src={photos[lightboxIndex]} alt={`${title} ${lightboxIndex + 1} of ${photos.length}`}/>
                        {photos.length > 1 && (
                            <button type="button" className="ag-lb-nav" onClick={() => step(1)} aria-label="Next photo">
                                <i className="ph ph-caret-right" aria-hidden="true"/>
                            </button>
                        )}
                    </div>
                    {photos.length > 1 && (
                        <div className="ag-lb-strip">
                            {photos.map((src, i) => (
                                <button
                                    type="button"
                                    key={src}
                                    className={i === lightboxIndex ? 'active' : ''}
                                    onClick={() => setLightboxIndex(i)}
                                    aria-label={`Go to photo ${i + 1}`}
                                >
                                    <img src={src} alt=""/>
                                </button>
                            ))}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}

export default ActivityGallery;
