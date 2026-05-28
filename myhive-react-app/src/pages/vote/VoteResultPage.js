import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import { formatPrice, formatPricePerPerson } from '../../utils/format';
import './VoteResultPage.css';

function VoteResultPage() {
    const { shareToken } = useParams();
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);
    const [added, setAdded] = useState(() => new Set());

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

    // TODO: wire Add-to-trip to the booking flow (out of Plan 4 scope)
    const toggleSuggestion = (activityId) => {
        setAdded(prev => {
            const next = new Set(prev);
            if (next.has(activityId)) {
                next.delete(activityId);
            } else {
                next.add(activityId);
            }
            return next;
        });
    };

    if (error) {
        return <div className="result-page-error">{error}</div>;
    }
    if (!data) {
        return <div className="result-page-loading">Loading result...</div>;
    }

    return (
        <div className="result-page">
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

            <section className="result-block budget-block">
                <h2>Budget</h2>
                <p>Spent: {formatPrice(data.totalPrice)}</p>
                {data.budget != null && (
                    <>
                        <p>Budget: {formatPrice(data.budget)}</p>
                        <p className={data.remaining < 0 ? 'budget-over' : ''}>
                            Remaining: {formatPrice(data.remaining)}
                        </p>
                    </>
                )}
            </section>

            {data.suggestions.length > 0 && (
                <section className="result-block">
                    <h2>Suggestions</h2>
                    <div className="suggestions-grid">
                        {data.suggestions.map(s => (
                            <div
                                key={s.activityId}
                                className={`suggestion-card ${added.has(s.activityId) ? 'added' : ''}`}
                            >
                                {s.imageUrl && (
                                    <img src={s.imageUrl} alt={s.name} className="suggestion-image" />
                                )}
                                <div className="suggestion-body">
                                    <strong>{s.name}</strong>
                                    <p>{formatPricePerPerson(s.price)}</p>
                                    {s.categories && s.categories.length > 0 && (
                                        <p className="suggestion-cats">{s.categories.join(' · ')}</p>
                                    )}
                                    <button type="button" onClick={() => toggleSuggestion(s.activityId)}>
                                        {added.has(s.activityId) ? 'Added' : 'Add to trip'}
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                </section>
            )}
        </div>
    );
}

export default VoteResultPage;
