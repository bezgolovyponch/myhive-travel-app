import {useNavigate} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import {DEFAULT_ACTIVITY_IMAGE, formatPrice} from '../utils/format';
import {computeTripTotal, groupTripItems} from '../utils/tripPricing';
import TripSetupModal from './TripSetupModal';
import {useStartGroupVote} from '../hooks/useStartGroupVote';
import {useT} from '../i18n';
import './TripBuilderDropdown.css';

// voteHref: where the empty state's vote CTA goes instead of opening the setup
// modal in place. Pages outside the SPA (the marketing landings) must pass it —
// the modal's confirm hands its setup to /vote/new/quiz through react-router
// location state, and the full page load out of such a page destroys it. Same
// prop, same reason as HomePage's.
function TripBuilderDropdown({voteHref}) {
    const {state, dispatch} = useTrip();
    const navigate = useNavigate();
    const {voteSetupOpen, openVoteSetup, closeVoteSetup, handleVoteConfirm, preselectedDestination} = useStartGroupVote();
    const t = useT('tripDropdown');

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
                <h3>{t('title')}</h3>
                <button type="button" className="app-modal-close-btn" aria-label={t('closeAria')}
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
                                            <span className="trip-modal-package-discount">{t('percentOff', {pct: group.packageDiscountPct})}</span>
                                        )}
                                        <button
                                            type="button"
                                            className="trip-modal-item-remove"
                                            aria-label={t('removeAria', {name: group.packageName})}
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
                                        aria-label={t('removeAria', {name: item.name || item.title})}
                                        onClick={() => dispatch({type: 'REMOVE_FROM_TRIP', activityId: item.id})}
                                    >×
                                    </button>
                                </div>
                            ))}
                        </div>
                    </>
                ) : (
                    <div className="empty-trip-state">
                        <p>{t('emptyState')}</p>
                        {voteHref ? (
                            <a className="trip-builder-vote-btn" href={voteHref}>
                                {t('voteTogether')}
                            </a>
                        ) : (
                            <>
                                <button className="trip-builder-vote-btn" onClick={openVoteSetup}>
                                    {t('voteTogether')}
                                </button>
                                <TripSetupModal
                                    isVoteMode={true}
                                    voteOpen={voteSetupOpen}
                                    onVoteConfirm={handleVoteConfirm}
                                    onVoteCancel={closeVoteSetup}
                                    preselectedDestination={preselectedDestination}
                                />
                            </>
                        )}
                    </div>
                )}
            </div>
            {state.tripItems.length > 0 && (
                <div className="trip-builder-dropdown-footer">
                    <div className="trip-modal-total">
                        <span>{travelers === 1 ? t('totalOne', {count: travelers}) : t('totalOther', {count: travelers})}</span>
                        <span className="trip-modal-total-price">{formatPrice(totalPrice)}</span>
                    </div>
                    <button className="trip-builder-complete-btn" onClick={handleComplete}>
                        {t('continue')}
                    </button>
                </div>
            )}
        </div>
    );
}

export default TripBuilderDropdown;
