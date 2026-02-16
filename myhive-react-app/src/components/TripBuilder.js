import {useContext, useState} from 'react';
import {AppContext} from '../context/AppContext';

function TripBuilder() {
  const { state, dispatch } = useContext(AppContext);
  const [browseFilter, setBrowseFilter] = useState('all');

  const handleRemoveActivity = (activityId) => {
    dispatch({ type: 'REMOVE_FROM_TRIP', activityId });
  };

  const handleAddActivity = (activity) => {
    dispatch({type: 'ADD_TO_TRIP', activity});
  };

  const handleConfirmTrip = () => {
    alert(`🎉 Proceeding to confirmation!\n\nYour trip includes ${state.tripItems.length} activities:\n${state.tripItems.map(item => `• ${item.name || item.title}`).join('\n')}\n\nThis would normally take you to the booking confirmation page.`);
  };

  const filteredBrowseActivities = browseFilter === 'all'
      ? state.activities
      : state.activities.filter(a => a.category === browseFilter);

  return (
    <div className="trip-builder-layout">
      <div className="trip-builder-left">
        <div className="itinerary-header">
          <h2>Your Itinerary</h2>
          <p>{state.tripItems.length} {state.tripItems.length === 1 ? 'activity' : 'activities'} selected</p>
        </div>
        <div className="itinerary-list">
          {state.tripItems.length > 0 ? (
            state.tripItems.map(item => (
              <div key={item.id} className="itinerary-item">
                <img src={item.imageUrl || item.image} alt={item.name || item.title} className="itinerary-item-image"
                     loading="lazy"/>
                <div className="itinerary-item-content">
                  <div className="itinerary-item-title">{item.name || item.title}</div>
                  <div
                      className="itinerary-item-price">{typeof item.price === 'number' ? `€${item.price}` : item.price}</div>
                </div>
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
        {state.tripItems.length > 0 && (
            <button className="btn btn--primary btn--full-width confirm-btn" onClick={handleConfirmTrip}>
              Proceed to Confirmation
            </button>
        )}
      </div>
      <div className="trip-builder-right">
        <div className="browse-header">
          <h3>Browse More Activities</h3>
          <div className="browse-filters">
            {['all', 'nightlife', 'adventure', 'daytime'].map(filter => (
                <button
                    key={filter}
                    className={`filter-btn ${browseFilter === filter ? 'active' : ''}`}
                    onClick={() => setBrowseFilter(filter)}
                >
                  {filter === 'all' ? 'All' : filter.charAt(0).toUpperCase() + filter.slice(1)}
                </button>
            ))}
          </div>
        </div>
        <div className="browse-activities">
          {filteredBrowseActivities.map(activity => {
            const isAdded = state.tripItems.some(item => item.id === activity.id);
            return (
                <div key={activity.id} className="browse-activity-item">
                  <img src={activity.imageUrl || activity.image} alt={activity.name || activity.title}
                       className="browse-activity-image" loading="lazy"/>
                  <div className="browse-activity-content">
                    <div className="browse-activity-title">{activity.name || activity.title}</div>
                    <div
                        className="browse-activity-price">{typeof activity.price === 'number' ? `€${activity.price}` : activity.price}</div>
                  </div>
                  <button
                      className="browse-add-btn"
                      onClick={() => handleAddActivity(activity)}
                      disabled={isAdded}
                  >
                    {isAdded ? 'Added' : 'Add'}
                  </button>
                </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

export default TripBuilder;
