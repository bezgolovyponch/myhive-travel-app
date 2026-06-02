import { useEffect, useRef } from 'react';
import './ActivityPreviewModal.css';

function ActivityPreviewModal({ activity, link, onClose }) {
    const contentRef = useRef(null);
    const previouslyFocusedRef = useRef(null);
    const onCloseRef = useRef(onClose);

    useEffect(() => {
        onCloseRef.current = onClose;
    });

    useEffect(() => {
        if (!activity) {
            return;
        }
        previouslyFocusedRef.current = document.activeElement;
        const node = contentRef.current;

        const getFocusable = () => {
            if (!node) {
                return [];
            }
            return Array.from(
                node.querySelectorAll(
                    'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
                )
            ).filter((el) => !el.hasAttribute('disabled'));
        };

        const focusables = getFocusable();
        if (focusables.length > 0) {
            focusables[0].focus();
        }

        const handleKey = (e) => {
            if (e.key === 'Escape') {
                onCloseRef.current();
                return;
            }
            if (e.key === 'Tab') {
                const items = getFocusable();
                if (items.length === 0) {
                    e.preventDefault();
                    return;
                }
                const first = items[0];
                const last = items[items.length - 1];
                if (e.shiftKey && document.activeElement === first) {
                    e.preventDefault();
                    last.focus();
                } else if (!e.shiftKey && document.activeElement === last) {
                    e.preventDefault();
                    first.focus();
                }
            }
        };

        document.addEventListener('keydown', handleKey);
        return () => {
            document.removeEventListener('keydown', handleKey);
            const prev = previouslyFocusedRef.current;
            if (prev && typeof prev.focus === 'function' && document.contains(prev)) {
                prev.focus();
            }
        };
    }, [activity]);

    if (!activity) {
        return null;
    }

    const meta = [];
    if (activity.price != null) {
        meta.push(`€${activity.price}/person`);
    }
    if (activity.duration != null) {
        meta.push(`${Math.round(activity.duration / 60)}h`);
    }
    if (activity.categories && activity.categories.length > 0) {
        meta.push(activity.categories.join(' · '));
    }

    return (
        <div
            className="app-modal activity-preview-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="activity-preview-title"
            onClick={onClose}
        >
            <div className="app-modal-content" ref={contentRef} onClick={(e) => e.stopPropagation()}>
                <div className="app-modal-header">
                    <h2 id="activity-preview-title">{activity.name}</h2>
                    <button type="button" className="app-modal-close-btn" onClick={onClose} aria-label="Close">×</button>
                </div>
                <div className="app-modal-body">
                    {activity.imageUrl && (
                        <img src={activity.imageUrl} alt={activity.name} className="activity-preview-image" />
                    )}
                    {meta.length > 0 && (
                        <div className="activity-preview-meta">{meta.join(' · ')}</div>
                    )}
                    <div className="activity-preview-description">
                        {activity.description
                            ? activity.description
                            : <span className="activity-preview-no-desc">No description yet.</span>}
                    </div>
                </div>
                {link && (
                    <div className="app-modal-footer">
                        <a
                            href={link}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="activity-preview-link"
                        >
                            View full page ↗
                        </a>
                    </div>
                )}
            </div>
        </div>
    );
}

export default ActivityPreviewModal;
