import {useNavigate} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import {DEFAULT_ACTIVITY_IMAGE, formatPrice} from '../utils/format';
import {computeTripTotal, groupTripItems} from '../utils/tripPricing';
import TripSetupModal from './TripSetupModal';
import {useStartGroupVote} from '../hooks/useStartGroupVote';

function TripBuilderDropdown() {
    const {state, dispatch} = useTrip();
    const navigate = useNavigate();
    const {voteSetupOpen, openVoteSetup, closeVoteSetup, handleVoteConfirm, preselectedDestination} = useStartGroupVote();

    if (!state.tripBuilderModalOpen) return null;

    const travelers = state.tripTravelers || 1;

    const {standalone, groups: groupsArray} = groupTripItems(state.tripItems);
    const totalPrice = computeTripTotal(state.tripItems, travelers);

    const handleComplete = () => {
        const destSlug = state.tripItems.find(i => i.destinationSlug)?.destinationSlug;
        dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'});
        if (destSlug) {
            navigate(`/destination/${destSlug}?tab=trip-builder`);
        }
    };

    return (
        <div className="trip-builder-dropdown">
            <div className="trip-builder-dropdown-header">
                <h3>Trip Builder</h3>
                <button type="button" className="app-modal-close-btn" aria-label="Close"
                        onClick={() => dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'})}>×
                </button>
            </div>
            <div className="trip-builder-dropdown-body">
                {state.tripItems.length > 0 ? (
                    <>
                        <div className="trip-modal-items">
                            {groupsArray.map(group => (
                                <div key={group.packageId} className="trip-modal-package-group">
                                    <div className="trip-modal-package-header">
                                        <span className="trip-modal-package-name">{group.packageName}</span>
                                        {group.packageDiscountPct > 0 && (
                                            <span className="trip-modal-package-discount">{group.packageDiscountPct}% off</span>
                                        )}
                                        <button
                                            type="button"
                                            className="trip-modal-item-remove"
                                            aria-label={`Remove ${group.packageName}`}
                                            onClick={() => dispatch({type: 'REMOVE_PACKAGE_FROM_TRIP', packageId: group.packageId})}
                                        >×
                                        </button>
                                    </div>
                                    {group.items.map(item => (
                                        <div key={item.id} className="trip-modal-item trip-modal-item--indented">
                                            <img src={item.imageUrl || DEFAULT_ACTIVITY_IMAGE} alt={item.name}
                                                 className="trip-modal-item-image"/>
                                            <div className="trip-modal-item-info">
                                                <span className="trip-modal-item-name">{item.name}</span>
                                                <span className="trip-modal-item-price">{formatPrice(item.price)}</span>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            ))}
                            {standalone.map(item => (
                                <div key={item.id} className="trip-modal-item">
                                    <img src={item.imageUrl || DEFAULT_ACTIVITY_IMAGE} alt={item.name}
                                         className="trip-modal-item-image"/>
                                    <div className="trip-modal-item-info">
                                        <span className="trip-modal-item-name">{item.name || item.title}</span>
                                        <span className="trip-modal-item-price">{formatPrice(item.price)}</span>
                                    </div>
                                    <button
                                        type="button"
                                        className="trip-modal-item-remove"
                                        aria-label={`Remove ${item.name || item.title}`}
                                        onClick={() => dispatch({type: 'REMOVE_FROM_TRIP', activityId: item.id})}
                                    >×
                                    </button>
                                </div>
                            ))}
                        </div>
                        <div className="trip-modal-total">
                            <span>Total ({travelers} {travelers === 1 ? 'person' : 'people'})</span>
                            <span className="trip-modal-total-price">{formatPrice(totalPrice)}</span>
                        </div>
                        <button className="trip-builder-complete-btn" onClick={handleComplete}>
                            Complete Booking
                        </button>
                        <button className="trip-builder-vote-btn" onClick={openVoteSetup}>
                            Vote together &amp; build a trip
                        </button>

                        <TripSetupModal
                            isVoteMode={true}
                            voteOpen={voteSetupOpen}
                            onVoteConfirm={handleVoteConfirm}
                            onVoteCancel={closeVoteSetup}
                            preselectedDestination={preselectedDestination}
                        />
                    </>
                ) : (
                    <div className="empty-trip-state">
                        <p>No activities added yet. Browse and add activities to build your trip!</p>
                        <button className="trip-builder-vote-btn" onClick={openVoteSetup}>
                            Vote together &amp; build a trip
                        </button>
                        <TripSetupModal
                            isVoteMode={true}
                            voteOpen={voteSetupOpen}
                            onVoteConfirm={handleVoteConfirm}
                            onVoteCancel={closeVoteSetup}
                            preselectedDestination={preselectedDestination}
                        />
                    </div>
                )}
            </div>
        </div>
    );
}

export default TripBuilderDropdown;
