import { useContext } from 'react';
import { AppContext } from '../context/AppContext';

function ActivityCard({ activity, isAdded = false }) {
  const { dispatch } = useContext(AppContext);

  const handleAddToTrip = () => {
    dispatch({ type: 'ADD_TO_TRIP', activity });
  };

  return (
    <div className="activity-card">
      <img 
        src={activity.image} 
        alt={activity.title} 
        className="activity-image" 
        loading="lazy" 
      />
      <div className="activity-content">
        <span className="activity-category">
          {activity.category.charAt(0).toUpperCase() + activity.category.slice(1)}
        </span>
        <h3 className="activity-title">{activity.title}</h3>
        <p className="activity-description">{activity.description}</p>
        <div className="activity-footer">
          <span className="activity-price">{activity.price}</span>
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
