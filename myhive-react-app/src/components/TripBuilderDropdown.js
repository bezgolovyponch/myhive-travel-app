import {useContext} from 'react';
import {useNavigate} from 'react-router-dom';
import {AppContext} from '../context/AppContext';

function TripBuilderDropdown() {
    const {state, dispatch} = useContext(AppContext);
    const navigate = useNavigate();

    if (!state.tripBuilderModalOpen) return null;

    const handleComplete = () => {
        const destId = state.tripItems[0]?.destinationId;
        dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'});
        if (destId) {
            navigate(`/destination/${destId}?tab=trip-builder`);
        }
    };

    return (
        <div className="trip-builder-dropdown">
            <div className="trip-builder-dropdown-header">
                <h3>Trip Builder</h3>
                <button className="app-modal-close-btn"
                        onClick={() => dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'})}>×
                </button>
            </div>
            <div className="trip-builder-dropdown-body">
                {state.tripItems.length > 0 ? (
                    <>
                        <div className="trip-modal-items">
                            {state.tripItems.map(item => {
                                const img = item.imageUrl || 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=400&h=300&fit=crop';
                                const price = typeof item.price === 'number' ? `€${item.price}` : item.price;
                                return (
                                    <div key={item.id} className="trip-modal-item">
                                        <img src={img} alt={item.name} className="trip-modal-item-image"/>
                                        <div className="trip-modal-item-info">
                                            <span className="trip-modal-item-name">{item.name || item.title}</span>
                                            <span className="trip-modal-item-price">{price}</span>
                                        </div>
                                        <button
                                            className="trip-modal-item-remove"
                                            onClick={() => dispatch({type: 'REMOVE_FROM_TRIP', activityId: item.id})}
                                        >×
                                        </button>
                                    </div>
                                );
                            })}
                        </div>
                        <div className="trip-modal-total">
                            <span>Total</span>
                            <span className="trip-modal-total-price">
                                €{state.tripItems.reduce((sum, item) => sum + (typeof item.price === 'number' ? item.price : 0), 0)}
                            </span>
                        </div>
                        <button className="trip-builder-complete-btn" onClick={handleComplete}>
                            Complete Booking
                        </button>
                    </>
                ) : (
                    <div className="empty-trip-state">
                        <p>No activities added yet. Browse and add activities to build your trip!</p>
                    </div>
                )}
            </div>
        </div>
    );
}

export default TripBuilderDropdown;
