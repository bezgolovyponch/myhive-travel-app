import {useNavigate} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import {DEFAULT_ACTIVITY_IMAGE, formatPrice} from '../utils/format';
import {computeTripTotal, groupTripItems} from '../utils/tripPricing';
import TripSetupModal from './TripSetupModal';
import {useStartGroupVote} from '../hooks/useStartGroupVote';
import {useT} from '../i18n';
// Imported here rather than inherited from global.css, so this component paints
// the same on the landings, which cannot load global.css. AppModal.css is for
// the close button below, which reuses .app-modal-close-btn.
import '../styles/tokens.css';
import './AppModal.css';
import './TripBuilderDropdown.css';

// `voteHref` is for the landings, where this dropdown lives inside a header
// that carries a backdrop-filter — the containing block for fixed descendants,
// which would trap the setup modal inside the header bar. Given an href, the
// empty-state CTA navigates to /vote/new (which opens the same modal) instead
// of mounting it here.
function TripBuilderDropdown({voteHref = null}) {
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
