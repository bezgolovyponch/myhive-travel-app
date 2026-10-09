import { useEffect, useState } from 'react';
import AppModal from './AppModal';
import api from '../services/api';
import { formatDuration } from '../utils/format';
import { useLocalePath, useT } from '../i18n';
import './ActivityPreviewModal.css';

/**
 * The activity's card in a dialog, so a list can show what a row is without
 * leaving the page: Escape, the backdrop, × and the footer button all close it.
 *
 * @param activity    what the caller already has; a row with no `description` key is completed by id
 * @param activityId  the id to load the rest by, for rows that carry only a name and a picture
 * @param link        the full page; defaults to the loaded activity's own page
 * @param closeLabel  shows a footer button that closes, labelled with where it goes back to
 * @param action      {label, onClick, disabled}: one thing to do with the activity; doing it closes the dialog
 */
function ActivityPreviewModal({ activity: given, activityId, link, onClose, closeLabel, action }) {
    const t = useT('cards');
    const tDuration = useT('activityDetail.duration');
    const lp = useLocalePath();
    const [loaded, setLoaded] = useState(null);
    const needsLoad = Boolean(given && activityId && given.description === undefined);

    useEffect(() => {
        setLoaded(null);
        if (!needsLoad) {
            return undefined;
        }
        let live = true;
        Promise.resolve()
            .then(() => api.getActivity(activityId))
            .then((full) => {
                if (live) setLoaded(full);
            })
            // The row's own name and picture stay up; the text just never arrives.
            .catch(() => {
                if (live) setLoaded({});
            });
        return () => {
            live = false;
        };
    }, [activityId, needsLoad]);

    if (!given) {
        return null;
    }
    const loading = needsLoad && !loaded;
    // Category objects from the API become the names the meta line joins.
    const activity = {
        ...given,
        ...(loaded || {}),
        name: given.name || loaded?.name,
        categories: (loaded?.categories || given.categories || []).map((c) => (typeof c === 'string' ? c : c.name)),
    };
    const fullPage = link
        || (activity.slug && activity.destinationSlug
            ? `/destination/${activity.destinationSlug}/activity/${activity.slug}` : null);

    // No per-person price: the plan shows one "from" price for the group.
    const meta = [];
    const durationText = formatDuration(activity.duration, tDuration);
    if (durationText) {
        meta.push(durationText);
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
            footer={(fullPage || closeLabel || action) && (
                <>
                    {fullPage && (
                        <a
                            href={lp(fullPage)}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="activity-preview-link"
                        >
                            {t('viewFullPage')}
                        </a>
                    )}
                    {closeLabel && (
                        <button type="button" className={`activity-preview-btn${action ? ' is-quiet' : ''}`}
                                onClick={onClose}>
                            {closeLabel}
                        </button>
                    )}
                    {action && (
                        <button
                            type="button"
                            className="activity-preview-btn"
                            disabled={action.disabled}
                            onClick={() => {
                                action.onClick();
                                onClose();
                            }}
                        >
                            {action.label}
                        </button>
                    )}
                </>
            )}
        >
            {/* What you get is the first thing under the title, before the picture and the story. */}
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
            {activity.imageUrl && (
                <img src={activity.imageUrl} alt={activity.name} className="activity-preview-image" />
            )}
            {meta.length > 0 && (
                <div className="activity-preview-meta">{meta.join(' · ')}</div>
            )}
            <div className="activity-preview-description" aria-busy={loading || undefined}>
                {activity.description
                    ? activity.description
                    : <span className="activity-preview-no-desc">{loading ? t('loadingDetails') : t('noDescription')}</span>}
            </div>
        </AppModal>
    );
}

export default ActivityPreviewModal;
