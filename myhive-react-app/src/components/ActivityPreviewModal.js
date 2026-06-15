import AppModal from './AppModal';
import { formatPricePerPerson } from '../utils/format';
import './ActivityPreviewModal.css';

function ActivityPreviewModal({ activity, link, onClose }) {
    if (!activity) {
        return null;
    }

    const meta = [];
    if (activity.price != null) {
        meta.push(formatPricePerPerson(activity.price));
    }
    if (activity.duration != null) {
        meta.push(`${Math.round(activity.duration / 60)}h`);
    }
    if (activity.categories && activity.categories.length > 0) {
        meta.push(activity.categories.join(' · '));
    }

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
        </AppModal>
    );
}

export default ActivityPreviewModal;
