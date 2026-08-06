import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import {capitalizeFirst, DEFAULT_ACTIVITY_IMAGE, formatPricePerPerson, groupMinNote, hasGroupMin} from '../utils/format';
import ActivityPreviewModal from './ActivityPreviewModal';
import './ActivityCard.css';

function ActivityCard({ activity, isAdded = false, silent = false }) {
    const {dispatch} = useTrip();
    const navigate = useNavigate();
    const [previewOpen, setPreviewOpen] = useState(false);

    const destSlug = activity.destinationSlug || activity.destinationId;
    const activityLink = `/destination/${destSlug}/activity/${activity.slug || activity.id}`;

    const handleAddToTrip = (e) => {
        e.stopPropagation();
        dispatch({ type: 'ADD_TO_TRIP', activity, silent });
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
    const formattedPrice = hasGroupMin(activity)
        ? `from ${formatPricePerPerson(activity.price)}`
        : formatPricePerPerson(activity.price);
    const primaryCategory = activity.categories && activity.categories.length > 0
        ? activity.categories[0].name
        : null;

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
            aria-label={`View ${title}`}
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
          {primaryCategory ? capitalizeFirst(primaryCategory) : 'Activity'}
        </span>
                {/* A real anchor so the card is a crawlable link when this card is
                    server-rendered — the surrounding div is role="button", which
                    emits no href. preventDefault lets the click bubble to
                    handleCardClick instead, keeping SPA client-side navigation. */}
                <h3 className="activity-title">
                    <a
                        href={activityLink}
                        className="activity-title-link"
                        onClick={(e) => e.preventDefault()}
                        tabIndex={-1}
                    >
                        {title}
                    </a>
                </h3>
                <div className="activity-footer">
                    <span className="activity-price">
                        {formattedPrice}
                        {hasGroupMin(activity) && (
                            <span className="activity-min-note">{groupMinNote(activity)}</span>
                        )}
                    </span>
                    <div className="activity-actions">
                        <button
                            className="more-info-btn"
                            onClick={handleMoreInfo}
                        >
                            More info
                        </button>
                        <button
                            className="add-to-trip-btn"
                            onClick={handleAddToTrip}
                            disabled={isAdded}
                        >
                            {isAdded ? 'Added' : 'Add to trip'}
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
            />
        )}
        </>
    );
}

export default ActivityCard;
