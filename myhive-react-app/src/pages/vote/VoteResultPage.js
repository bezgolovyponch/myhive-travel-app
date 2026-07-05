import {useEffect, useRef, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import voteApi from '../../services/voteApi';
import ActivityCard from '../../components/ActivityCard';
import VoteTallyCard from '../../components/vote/VoteTallyCard';
import {useTrip} from '../../context/TripContext';
import { formatPrice, formatPricePerPerson } from '../../utils/format';
import { pushEvent } from '../../utils/analytics';
import { getAttribution, getRef } from '../../utils/attribution';
import { resolveUserRole } from '../../utils/userRole';
import PaymentActions from '../../components/PaymentActions';
import VoteMeta from './VoteMeta';
import './VoteResultPage.css';

function suggestionToActivity(s) {
    return {
        id: s.activityId,
        name: s.name,
        slug: s.slug,
        destinationSlug: s.destinationSlug,
        imageUrl: s.imageUrl,
        description: s.description,
        includes: s.includes,
        price: s.price,
        categories: (s.categories || []).map(name => ({ name })),
    };
}

function VoteResultContent() {
    const { shareToken } = useParams();
    const navigate = useNavigate();
    const {state, dispatch} = useTrip();
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);
    const checkoutFiredRef = useRef(false);

    const managerToken = localStorage.getItem(`myhive-manager-${shareToken}`);
    const isInitiator = localStorage.getItem(`myhive-initiator-${shareToken}`) === 'true' && !!managerToken;

    // The bookable trip = the group's voted result PLUS every activity the initiator added to their trip
    // (state.tripItems), deduped by activityId. The deposit is taken on this whole curated trip, not just
    // the voted picks — so an added activity is payable even if it was never a vote "suggestion".
    const bookedActivities = [
        ...(data?.result || []).map((r) => ({
            activityId: r.activityId,
            activityName: r.name,
            price: Number(r.price),
        })),
        ...(state.tripItems || []).map((i) => ({
            activityId: i.id,
            activityName: i.name,
            price: Number(i.price),
        })),
    ].filter((a, idx, arr) => a.activityId && arr.findIndex((x) => x.activityId === a.activityId) === idx);

    const makeBookingPayload = (contactData) => ({
        tripName: 'Vote booking',
        userEmail: contactData.email,
        customerName: contactData.fullName,
        phone: contactData.phone,
        numberOfTravelers: parseInt(contactData.numberOfTravelers, 10) || data?.numberOfTravelers || 1,
        notes: contactData.specialRequirements || '',
        destinations: [{
            destinationName: data?.destination || '',
            startDate: data?.startDate || null,
            endDate: data?.endDate || null,
            activities: bookedActivities,
        }],
        // Tie the consultation-lead / deposit booking to its originating campaign,
        // same as a direct booking from the Trip Builder.
        ...getAttribution(),
        ref: getRef(),
    });

    const paymentTripData = {
        tripItems: bookedActivities.map((a) => ({id: a.activityId, price: a.price})),
    };

    const handleOpenTripBuilder = () => {
        if (!data) {
            return;
        }
        if (data.numberOfTravelers && data.numberOfTravelers > 0) {
            dispatch({ type: 'UPDATE_TRIP_TRAVELERS', travelers: data.numberOfTravelers });
        }
        if (data.startDate || data.endDate) {
            dispatch({
                type: 'UPDATE_TRIP_DATES',
                startDate: data.startDate ?? '',
                endDate: data.endDate ?? '',
            });
        }
        navigate(`/destination/${data.destinationSlug}?tab=trip-builder&voteSession=${shareToken}`);
    };

    useEffect(() => {
        checkoutFiredRef.current = false; // reset for the new shareToken
        let cancelled = false;
        voteApi.getResult(shareToken)
            .then(response => {
                if (!cancelled) {
                    setData(response);
                    // CART results have their own real checkout-funnel event fired from
                    // Trip Builder (trip_builder_viewed) — this page is just a read-only
                    // tally for them, so don't double-count it as checkout_viewed.
                    if (!checkoutFiredRef.current && response.voteMode !== 'CART') {
                        checkoutFiredRef.current = true;
                        pushEvent('checkout_viewed', {
                            trip_id: shareToken,
                            user_role: resolveUserRole(shareToken),
                            items_count: response.result.length,
                            value: Number(response.totalPrice),
                            currency: 'EUR',
                        });
                    }
                }
            })
            .catch(e => {
                if (!cancelled) {
                    setError(e.message);
                }
            });
        return () => {
            cancelled = true;
        };
    }, [shareToken]);

    if (error) {
        return <div className="result-page-error">{error}</div>;
    }
    if (!data) {
        return <div className="result-page-loading">Loading result...</div>;
    }

    if (data.voteMode === 'CART') {
        return (
            <div className="result-page">
                <div className="result-page-inner">
                    <h1>The votes are in!</h1>
                    <VoteTallyCard
                        participantCount={data.participantCount}
                        rows={data.result.map(row => ({
                            activityId: row.activityId,
                            name: row.name,
                            price: row.price,
                            likeCount: row.likeCount,
                        }))}
                        showPrices
                    />
                    {isInitiator && data.destinationSlug && (
                        <button
                            type="button"
                            className="result-open-trip-btn"
                            onClick={() => navigate(
                                `/destination/${data.destinationSlug}?tab=trip-builder&voteSession=${shareToken}`)}
                        >
                            Back to Trip Builder
                        </button>
                    )}
                </div>
            </div>
        );
    }

    return (
        <div className="result-page">
            <div className="result-page-inner">
                <h1>Trip result</h1>

                <section className="result-block">
                    <h2>The group&apos;s pick</h2>
                    {data.result.length === 0 ? (
                        <p className="result-empty">
                            The group didn&apos;t agree on anything within the budget. See suggestions below.
                        </p>
                    ) : (
                        <ul className="result-list">
                            {data.result.map(row => (
                                <li key={row.activityId} className="result-row">
                                    <div className="result-row-main">
                                        <strong>{row.name}</strong>
                                        <span className="result-row-price">{formatPricePerPerson(row.price)}</span>
                                    </div>
                                    <div className="result-row-counts">
                                        Likes: {row.likeCount} &middot; Skips: {row.skipCount}
                                    </div>
                                </li>
                            ))}
                        </ul>
                    )}
                </section>

                {(() => {
                    const travelers = Number(data.numberOfTravelers) || 1;
                    // Budget reflects the same curated trip the deposit charges (voted result + added activities).
                    const displayedTotal = bookedActivities.reduce((sum, a) => sum + Number(a.price) * travelers, 0);
                    const displayedRemaining = data.budget != null
                        ? Number(data.budget) - displayedTotal
                        : null;
                    return (
                        <section className="result-block budget-block">
                            <h2>Budget</h2>
                            <p>Spent: {formatPrice(displayedTotal)}</p>
                            {data.budget != null && (
                                <>
                                    <p>Budget: {formatPrice(data.budget)}</p>
                                    <p className={displayedRemaining < 0 ? 'budget-over' : ''}>
                                        Remaining: {formatPrice(displayedRemaining)}
                                    </p>
                                </>
                            )}
                        </section>
                    );
                })()}

                {data.destinationSlug && (
                    <button
                        type="button"
                        className="result-open-trip-btn"
                        onClick={handleOpenTripBuilder}
                    >
                        Open in Trip Builder
                    </button>
                )}

                {isInitiator && bookedActivities.length > 0 && (
                    <PaymentActions
                        voteShareToken={shareToken}
                        managerToken={managerToken}
                        tripData={paymentTripData}
                        initialValues={{
                            numberOfTravelers: data.numberOfTravelers,
                            startDate: data.startDate,
                            endDate: data.endDate,
                        }}
                        makeBookingPayload={makeBookingPayload}
                    />
                )}

                {data.suggestions.length > 0 && (
                    <section className="result-block">
                        <h2>Suggestions</h2>
                        <div className="suggestions-grid">
                            {data.suggestions.map(s => {
                                const activity = suggestionToActivity(s);
                                const isAdded = state.tripItems.some(i => i.id === activity.id);
                                return (
                                    <ActivityCard
                                        key={activity.id}
                                        activity={activity}
                                        isAdded={isAdded}
                                        silent
                                    />
                                );
                            })}
                        </div>
                    </section>
                )}
            </div>
        </div>
    );
}

function VoteResultPage() {
    return (
        <>
            <VoteMeta title="Vote results"/>
            <VoteResultContent/>
        </>
    );
}

export default VoteResultPage;
