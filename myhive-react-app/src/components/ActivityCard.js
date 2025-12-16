import { useContext } from 'react';
import { AppContext } from '../context/AppContext';

function ActivityCard({ activity, isAdded = false }) {
  const { dispatch } = useContext(AppContext);

  const handleAddToTrip = () => {
    dispatch({ type: 'ADD_TO_TRIP', activity });
  };

  // Map backend data to frontend format
  const imageUrl = activity.imageUrl || 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=400&h=300&fit=crop';
  const title = activity.name || activity.title;
  const formattedPrice = typeof activity.price === 'number' ? `€${activity.price}` : activity.price;
  const duration = activity.duration ? ` (${activity.duration} min)` : '';

  return (
    <div className="activity-card">
      <img 
        src={imageUrl} 
        alt={title} 
        className="activity-image" 
        loading="lazy" 
      />
      <div className="activity-content">
        <span className="activity-category">
          {activity.category ? activity.category.charAt(0).toUpperCase() + activity.category.slice(1) : 'Activity'}
        </span>
        <h3 className="activity-title">{title}{duration}</h3>
        <p className="activity-description">{activity.description}</p>
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
