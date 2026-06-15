import {useEffect, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import voteApi from '../../services/voteApi';
import ActivityCard from '../../components/ActivityCard';
import {useTrip} from '../../context/TripContext';
import { formatPrice, formatPricePerPerson } from '../../utils/format';
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
        let cancelled = false;
        voteApi.getResult(shareToken)
            .then(response => {
                if (!cancelled) {
                    setData(response);
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
                    const addedSuggestionsTotal = (data.suggestions || [])
                        .filter(s => state.tripItems.some(i => i.id === s.activityId))
                        .reduce((sum, s) => sum + Number(s.price) * travelers, 0);
                    const displayedTotal = Number(data.totalPrice) + addedSuggestionsTotal;
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
