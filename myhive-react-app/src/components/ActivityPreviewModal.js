import AppModal from './AppModal';
import { formatPricePerPerson, groupMinNote, hasGroupMin } from '../utils/format';
import './ActivityPreviewModal.css';

function ActivityPreviewModal({ activity, link, onClose }) {
    if (!activity) {
        return null;
    }

    const meta = [];
    if (activity.price != null) {
        meta.push(hasGroupMin(activity)
            ? `from ${formatPricePerPerson(activity.price)}`
            : formatPricePerPerson(activity.price));
    }
    if (hasGroupMin(activity)) {
        meta.push(groupMinNote(activity));
    }
    if (activity.duration != null) {
        meta.push(`${Math.round(activity.duration / 60)}h`);
    }
    if (activity.categories && activity.categories.length > 0) {
        meta.push(activity.categories.join(' · '));
    }

    // Same parsing as the detail page: the API stores includes as one
    // semicolon/newline-separated string (commas stay inside an item).
    const includedItems = (activity.includes || '')
        .split(/[;\n]+/)
        .map((item) => item.trim())
        .filter(Boolean);

    return (
        <AppModal
            isOpen
            onClose={onClose}
            title={activity.name}
            overlayClassName="activity-preview-modal"
            closeOnBackdrop
            footer={link && (
                <a
                    href={link}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="activity-preview-link"
                >
                    View full page ↗
                </a>
            )}
        >
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
            {includedItems.length > 0 && (
                <div className="activity-preview-includes">
                    <h3 className="activity-preview-includes-title">What's included</h3>
                    <ul className="activity-preview-includes-list">
                        {includedItems.map((item) => (
                            <li key={item}>{item}</li>
                        ))}
                    </ul>
                </div>
            )}
        </AppModal>
    );
}

export default ActivityPreviewModal;
