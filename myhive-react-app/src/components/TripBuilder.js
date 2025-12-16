import { useContext } from 'react';
import { AppContext } from '../context/AppContext';
import ActivityCard from './ActivityCard';

function TripBuilder() {
  const { state, dispatch } = useContext(AppContext);

  const handleRemoveActivity = (activityId) => {
    dispatch({ type: 'REMOVE_FROM_TRIP', activityId });
  };

  return (
    <div className="trip-builder-layout">
      <div className="trip-builder-left">
        <div className="itinerary-header">
          <h2>Your Itinerary</h2>
          <p>{state.tripItems.length} activities selected</p>
        </div>
        <div className="itinerary-list">
          {state.tripItems.length > 0 ? (
            state.tripItems.map(item => (
              <div key={item.id} className="itinerary-item">
                <ActivityCard activity={item} isAdded={true} />
                <button 
                  className="remove-item-btn"
                  onClick={() => handleRemoveActivity(item.id)}
                >
                  ×
                </button>
              </div>
            ))
          ) : (
            <div className="empty-state">
              <p>Start building your trip by adding activities!</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default TripBuilder;
