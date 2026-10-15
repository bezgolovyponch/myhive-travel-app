import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import {capitalizeFirst, DEFAULT_ACTIVITY_IMAGE} from '../utils/format';
import ActivityPreviewModal from './ActivityPreviewModal';
import {activityPath, routePath, useLocalePath, useT} from '../i18n';
import './ActivityCard.css';

function ActivityCard({ activity, isAdded = false, silent = false }) {
    const {state, dispatch} = useTrip();
    const navigate = useNavigate();
    const t = useT('cards');
    const tExtras = useT('extras');
    const lp = useLocalePath();
    const [previewOpen, setPreviewOpen] = useState(false);

    const destSlug = activity.destinationSlug || activity.destinationId;
    const activityLink = routePath(activityPath(destSlug, activity.slug || activity.id));

    const handleAddToTrip = (e) => {
        e.stopPropagation();
        dispatch({ type: 'ADD_TO_TRIP', activity, silent });
    };

    // The card's dialog is where extras are chosen: ticked there, then confirmed with its button.
    const cartItem = (state.tripItems || []).find(item => item.id === activity.id);
    const confirmExtras = (codes) => {
        if (cartItem) {
            dispatch({type: 'SET_ITEM_EXTRAS', id: activity.id, selectedExtras: codes});
        } else {
            dispatch({type: 'ADD_TO_TRIP', activity: {...activity, selectedExtras: codes}, silent});
        }
    };

    const handleCardClick = () => {
        navigate(activityLink);
    };

    const handleMoreInfo = (e) => {
        e.stopPropagation();
        setPreviewOpen(true);
    };

    const handleCardKeyDown = (e) => {
        if (e.target !== e.currentTarget) {
            // Keys on nested interactive elements belong to them.
            return;
        }
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            handleCardClick();
        }
    };

    const imageUrl = activity.imageUrl || DEFAULT_ACTIVITY_IMAGE;
    const title = activity.name || activity.title;
    const primaryCategory = activity.categories && activity.categories.length > 0
        ? activity.categories[0].name
        : null;
    const extraNames = (activity.extras || []).map(extra => extra.name);

    // ActivityPreviewModal joins categories as strings; card data carries
    // category objects, so normalize to names for the preview.
    const previewActivity = {
        ...activity,
        categories: (activity.categories || []).map((c) => (typeof c === 'string' ? c : c.name)),
    };

    return (
        <>
        <div
            className="card activity-card"
            role="button"
            tabIndex={0}
            aria-label={t('viewAria', {name: title})}
            onClick={handleCardClick}
            onKeyDown={handleCardKeyDown}
        >
            <img
                src={imageUrl}
                alt={title}
                className="activity-image"
                loading="lazy"
            />
            <div className="activity-content">
        <span className="activity-category">
          {primaryCategory ? capitalizeFirst(primaryCategory) : t('activityCategory')}
        </span>
                {/* A real anchor so the card is a crawlable link when this card is
                    server-rendered — the surrounding div is role="button", which
                    emits no href. preventDefault lets the click bubble to
                    handleCardClick instead, keeping SPA client-side navigation. */}
                <h3 className="activity-title">
                    <a
                        href={lp(activityLink)}
                        className="activity-title-link"
                        onClick={(e) => e.preventDefault()}
                        tabIndex={-1}
                    >
                        {title}
                    </a>
                </h3>
                {/* One line, a count: the card deliberately shows no price, and the names are on the card it opens. */}
                {extraNames.length > 0 && (
                    <span className="activity-extras-chip">
                        {extraNames.length === 1 ? t('extrasOne') : t('extrasCount', {count: extraNames.length})}
                    </span>
                )}
                <div className="activity-footer">
                    <div className="activity-actions">
                        <button
                            className="more-info-btn"
                            onClick={handleMoreInfo}
                        >
                            {t('moreInfo')}
                        </button>
                        <button
                            className="add-to-trip-btn"
                            onClick={handleAddToTrip}
                            disabled={isAdded}
                        >
                            {isAdded ? t('added') : t('addToTrip')}
                        </button>
                    </div>
                </div>
            </div>
        </div>
        {/* Rendered outside the card: the card's hover transform would otherwise
            become the containing block for the fixed-position overlay (flicker),
            and clicks in the modal would bubble to the card's navigate handler. */}
        {previewOpen && (
            <ActivityPreviewModal
                activity={previewActivity}
                link={activityLink}
                onClose={() => setPreviewOpen(false)}
                confirm={{
                    inPlan: Boolean(cartItem),
                    selected: cartItem?.selectedExtras || [],
                    addLabel: t('addToTrip'),
                    updateLabel: tExtras('update'),
                    onConfirm: confirmExtras,
                }}
            />
        )}
        </>
    );
}

export default ActivityCard;
