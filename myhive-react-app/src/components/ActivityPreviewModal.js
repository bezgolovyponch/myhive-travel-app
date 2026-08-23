import AppModal from './AppModal';
import { formatAmount, formatPrice, hasGroupMin } from '../utils/format';
import { useLocalePath, useT } from '../i18n';
import './ActivityPreviewModal.css';

function ActivityPreviewModal({ activity, link, onClose }) {
    const t = useT('cards');
    const lp = useLocalePath();
    if (!activity) {
        return null;
    }

    const meta = [];
    if (activity.price != null) {
        // "/ person" lives in the key (German flips the phrase), so the
        // translatable format string replaces utils/format's English one.
        meta.push(hasGroupMin(activity)
            ? t('fromPerPerson', {price: formatPrice(activity.price)})
            : t('perPersonPrice', {price: formatPrice(activity.price)}));
    }
    if (hasGroupMin(activity)) {
        meta.push(t('groupMinimum', {amount: formatAmount(Number(activity.minPrice))}));
    }
    if (activity.duration != null) {
        meta.push(t('durationHours', {hours: Math.round(activity.duration / 60)}));
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
                    href={lp(link)}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="activity-preview-link"
                >
                    {t('viewFullPage')}
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
                    : <span className="activity-preview-no-desc">{t('noDescription')}</span>}
            </div>
            {includedItems.length > 0 && (
                <div className="activity-preview-includes">
                    <h3 className="activity-preview-includes-title">{t('whatsIncluded')}</h3>
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
