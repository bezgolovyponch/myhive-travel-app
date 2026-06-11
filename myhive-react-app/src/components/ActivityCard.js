import {useContext} from 'react';
import {useNavigate} from 'react-router-dom';
import {AppContext} from '../context/AppContext';
import {capitalizeFirst, DEFAULT_ACTIVITY_IMAGE, formatPricePerPerson} from '../utils/format';
import './ActivityCard.css';

function ActivityCard({ activity, isAdded = false, silent = false }) {
  const { dispatch } = useContext(AppContext);
    const navigate = useNavigate();

    const handleAddToTrip = (e) => {
        e.stopPropagation();
    dispatch({ type: 'ADD_TO_TRIP', activity, silent });
  };

    const handleCardClick = () => {
        const destSlug = activity.destinationSlug || activity.destinationId;
        navigate(`/destination/${destSlug}/activity/${activity.slug || activity.id}`);
    };

    const handleCardKeyDown = (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            handleCardClick();
        }
    };

    const imageUrl = activity.imageUrl || DEFAULT_ACTIVITY_IMAGE;
  const title = activity.name || activity.title;
    const formattedPrice = formatPricePerPerson(activity.price);
    const primaryCategory = activity.categories && activity.categories.length > 0
        ? activity.categories[0].name
        : null;
  return (
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
          <h3 className="activity-title">{title}</h3>
        <p className="activity-description">{activity.description}</p>
          {activity.includes && (
              <p className="activity-includes">Includes: {activity.includes}</p>
          )}
        <div className="activity-footer">
          <span className="activity-price">{formattedPrice}</span>
            <button
                className="add-to-trip-btn"
            onClick={handleAddToTrip}
            disabled={isAdded}
          >
            {isAdded ? 'Added' : 'Add to Trip'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default ActivityCard;
