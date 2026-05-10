import {useContext, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {AppContext} from '../context/AppContext';
import {DEFAULT_ACTIVITY_IMAGE, formatPrice} from '../utils/format';
import TripSetupModal from './TripSetupModal';

function TripBuilderDropdown() {
    const {state, dispatch} = useContext(AppContext);
    const navigate = useNavigate();
    const [voteSetupOpen, setVoteSetupOpen] = useState(false);

    if (!state.tripBuilderModalOpen) return null;

    const handleVoteClick = () => setVoteSetupOpen(true);

    const destSlug = state.tripItems.find(i => i.destinationSlug)?.destinationSlug;
    const preselectedDestination = state.destinations.find(d => d.slug === destSlug) || null;

    const handleVoteConfirm = ({ travelers, startDate, endDate, email, destination }) => {
        setVoteSetupOpen(false);
        dispatch({ type: 'CLOSE_TRIP_BUILDER_MODAL' });
        navigate('/vote/new/categories', {
            state: {
                destinationId: destination.id,
                destinationSlug: destination.slug,
                destinationName: destination.name,
                voteSetup: { travelers, startDate, endDate, email },
            },
        });
    };

    const travelers = state.tripTravelers || 1;

    const standalone = state.tripItems.filter(i => !i.packageId);
    const packageGroups = state.tripItems.reduce((acc, item) => {
        if (!item.packageId) {
            return acc;
        }
        if (!acc[item.packageId]) {
            acc[item.packageId] = {
                packageId: item.packageId,
                packageName: item.packageName,
                packageDiscountPct: Number(item.packageDiscountPct) || 0,
                destinationSlug: item.destinationSlug,
                items: [],
            };
        }
        acc[item.packageId].items.push(item);
        return acc;
    }, {});
    const groupsArray = Object.values(packageGroups);

    const totalPrice = (() => {
        let total = 0;
        standalone.forEach(it => {
            total += (Number(it.price) || 0) * travelers;
        });
        groupsArray.forEach(g => {
            const sub = g.items.reduce((s, it) => s + (Number(it.price) || 0) * travelers, 0);
            total += sub * (100 - g.packageDiscountPct) / 100;
        });
        return Math.round(total * 100) / 100;
    })();

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
                <button className="app-modal-close-btn"
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
                                            className="trip-modal-item-remove"
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
                                        className="trip-modal-item-remove"
                                        onClick={() => dispatch({type: 'REMOVE_FROM_TRIP', activityId: item.id})}
                                    >×
                                    </button>
                                </div>
                            ))}
                        </div>
                        <div className="trip-modal-total">
                            <span>Total ({travelers} {travelers === 1 ? 'person' : 'people'})</span>
                            <span className="trip-modal-total-price">€{totalPrice}</span>
                        </div>
                        <button className="trip-builder-complete-btn" onClick={handleComplete}>
                            Complete Booking
                        </button>
                        <button className="trip-builder-vote-btn" onClick={handleVoteClick}>
                            Vote together &amp; build a trip
                        </button>

                        <TripSetupModal
                            isVoteMode={true}
                            voteOpen={voteSetupOpen}
                            onVoteConfirm={handleVoteConfirm}
                            onVoteCancel={() => setVoteSetupOpen(false)}
                            preselectedDestination={preselectedDestination}
                        />
                    </>
                ) : (
                    <div className="empty-trip-state">
                        <p>No activities added yet. Browse and add activities to build your trip!</p>
                        <button className="trip-builder-vote-btn" onClick={handleVoteClick}>
                            Vote together &amp; build a trip
                        </button>
                        <TripSetupModal
                            isVoteMode={true}
                            voteOpen={voteSetupOpen}
                            onVoteConfirm={handleVoteConfirm}
                            onVoteCancel={() => setVoteSetupOpen(false)}
                            preselectedDestination={preselectedDestination}
                        />
                    </div>
                )}
            </div>
        </div>
    );
}

export default TripBuilderDropdown;
