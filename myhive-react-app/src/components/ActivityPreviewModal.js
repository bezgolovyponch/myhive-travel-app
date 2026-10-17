import { useEffect, useState } from 'react';
import AppModal from './AppModal';
import api from '../services/api';
import { extraPriceLabel, formatDuration } from '../utils/format';
import ItemExtrasPicker from './ItemExtrasPicker';
import { activityPath, useLocalePath, useT } from '../i18n';
import './ActivityPreviewModal.css';

/**
 * The activity's card in a dialog, so a list can show what a row is without
 * leaving the page: Escape, the backdrop, × and the footer button all close it.
 *
 * @param activity    what the caller already has; a row with no `description` key is completed by id
 * @param activityId  the id to load the rest by, for rows that carry only a name and a picture
 * @param link        `link`: the activity page path, either EN-internal (`activityPath(...)`) or already localized (`routePath(...)`); the modal runs it through `lp()` (localizePath), which is idempotent on localized input. Defaults to the loaded activity's own page.
 * @param closeLabel  shows a footer button that closes, labelled with where it goes back to
 * @param action      {label, onClick, disabled}: one thing to do with the activity; doing it closes the dialog
 * @param confirm     {inPlan, selected, addLabel, updateLabel, disabled, onConfirm}: the card can put the
 *                    activity into the plan, or change the extras of one that is there. The extras are then
 *                    a checklist, nothing ticked that the plan does not hold, and ticking changes nothing
 *                    until the button is pressed: `onConfirm(codes, activity)` gets every ticked code.
 */
function ActivityPreviewModal({ activity: given, activityId, link, onClose, closeLabel, action, confirm }) {
    const t = useT('cards');
    const tDuration = useT('activityDetail.duration');
    const tExtras = useT('extras');
    const lp = useLocalePath();
    const [loaded, setLoaded] = useState(null);
    const needsLoad = Boolean(given && activityId && given.description === undefined);
    // What is ticked on the card right now; the plan only hears of it through the button.
    const [ticked, setTicked] = useState(() => confirm?.selected || []);
    const openFor = given ? (given.id || activityId || given.name) : null;
    const selectedKey = (confirm?.selected || []).join('|');
    useEffect(() => {
        setTicked(selectedKey ? selectedKey.split('|') : []);
    }, [openFor, selectedKey]);

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
            ? activityPath(activity.destinationSlug, activity.slug) : null);

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
    const extras = activity.extras || [];
    // Codes the activity still offers, in its own order: a stale code never reaches the plan.
    const tickedCodes = extras.filter((extra) => ticked.includes(extra.code)).map((extra) => extra.code);
    const unchanged = tickedCodes.join('|') === extras
        .filter((extra) => (confirm?.selected || []).includes(extra.code)).map((extra) => extra.code).join('|');
    // The card is the same wherever it opens: the button is always there - "Add" for an activity that is
    // not in the plan, "Update" for one that is (off until a tick differs from what the plan holds).
    const confirmable = Boolean(confirm) && !loading;

    return (
        <AppModal
            isOpen
            onClose={onClose}
            title={activity.name}
            overlayClassName="activity-preview-modal"
            closeOnBackdrop
            footer={(fullPage || closeLabel || action || confirmable) && (
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
                        <button type="button" className={`activity-preview-btn${action || confirmable ? ' is-quiet' : ''}`}
                                onClick={onClose}>
                            {closeLabel}
                        </button>
                    )}
                    {confirmable && (
                        <button
                            type="button"
                            className="activity-preview-btn activity-preview-confirm"
                            disabled={confirm.disabled || (confirm.inPlan && unchanged)}
                            onClick={() => {
                                confirm.onConfirm(tickedCodes, activity);
                                onClose();
                            }}
                        >
                            {confirm.inPlan ? confirm.updateLabel : confirm.addLabel}
                        </button>
                    )}
                    {action && !confirmable && (
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
            {/* With a plan to put it in, the extras are chosen here: ticked, then confirmed with the button. */}
            {extras.length > 0 && confirm && (
                <div className="activity-preview-extras activity-preview-extras--pick">
                    <ItemExtrasPicker extras={extras} selected={tickedCodes} onChange={setTicked} />
                </div>
            )}
            {/* Without one (a friend looking at a ballot card) they are only listed, priced. */}
            {extras.length > 0 && !confirm && (
                <div className="activity-preview-extras">
                    <h3 className="activity-preview-includes-title">{tExtras('title')}</h3>
                    <ul className="activity-preview-extras-list">
                        {extras.map((extra) => (
                            <li key={extra.code}>
                                <span>{extra.name}</span>
                                <span className="activity-preview-extras-price">{extraPriceLabel(extra, tExtras)}</span>
                            </li>
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
