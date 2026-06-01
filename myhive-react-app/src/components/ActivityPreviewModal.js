import { useEffect } from 'react';
import './ActivityPreviewModal.css';

function ActivityPreviewModal({ activity, link, onClose }) {
    useEffect(() => {
        if (!activity) {
            return undefined;
        }
        const handleKey = (e) => {
            if (e.key === 'Escape') {
                onClose();
            }
        };
        document.addEventListener('keydown', handleKey);
        return () => document.removeEventListener('keydown', handleKey);
    }, [activity, onClose]);

    if (!activity) {
        return null;
    }

    const meta = [];
    if (activity.price != null) {
        meta.push(`€${activity.price}/person`);
    }
    if (activity.duration) {
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
            aria-label={activity.name}
            onClick={onClose}
        >
            <div className="app-modal-content" onClick={(e) => e.stopPropagation()}>
                <div className="app-modal-header">
                    <h2>{activity.name}</h2>
                    <button className="app-modal-close-btn" onClick={onClose} aria-label="Close">×</button>
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
