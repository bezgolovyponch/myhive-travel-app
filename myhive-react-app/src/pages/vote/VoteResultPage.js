import { useContext, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AppContext } from '../../context/AppContext';
import voteApi from '../../services/voteApi';
import { formatPricePerPerson } from '../../utils/format';

function VoteResultPage() {
    const { shareToken } = useParams();
    const navigate = useNavigate();
    const { dispatch } = useContext(AppContext);
    const [result, setResult] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        voteApi.getResult(shareToken)
            .then(setResult)
            .catch(e => setError(e.message))
            .finally(() => setLoading(false));
    }, [shareToken]);

    const handleOpenTripBuilder = () => {
        if (result.numberOfTravelers > 1) {
            dispatch({ type: 'UPDATE_TRIP_TRAVELERS', travelers: result.numberOfTravelers });
        }
        if (result.startDate || result.endDate) {
            dispatch({ type: 'UPDATE_TRIP_DATES', startDate: result.startDate ?? '', endDate: result.endDate ?? '' });
        }
        navigate(`/destination/${result.destinationSlug}?tab=trip-builder&voteSession=${shareToken}`);
    };

    const pageStyle = { maxWidth: 560, margin: '0 auto', padding: 'calc(var(--header-height) + 24px) 16px 40px', color: 'var(--text, #f5f5f5)' };

    if (loading) return <div style={{ ...pageStyle, textAlign: 'center' }}>Loading results...</div>;

    if (error) {
        return (
            <div style={{ ...pageStyle, textAlign: 'center' }}>
                <p style={{ color: '#dc3545' }}>{error}</p>
                <p style={{ color: 'var(--text-muted, rgba(167,169,169,0.7))' }}>Results are sent by email once the 24-hour window closes.</p>
            </div>
        );
    }

    return (
        <div style={pageStyle}>
            <h2 style={{ marginBottom: 4 }}>Your Group Trip to {result.destinationName}</h2>
            <p style={{ color: 'var(--text-muted, rgba(167,169,169,0.7))', marginBottom: 24 }}>
                {result.activities.length} activities &middot; {result.numberOfTravelers} travellers
            </p>

            {result.activities.length === 0 ? (
                <p style={{ color: 'var(--text-muted, rgba(167,169,169,0.7))' }}>No activities matched the group&apos;s votes. Try adjusting the categories.</p>
            ) : (
                <>
                    <div style={{ marginBottom: 24 }}>
                        {result.activities.map(activity => (
                            <div key={activity.id} style={{ display: 'flex', gap: 12, padding: '12px 0', borderBottom: '1px solid var(--card-border, rgba(119,124,124,0.15))' }}>
                                {activity.imageUrl && (
                                    <img src={activity.imageUrl} alt={activity.name}
                                         style={{ width: 72, height: 72, objectFit: 'cover', borderRadius: 8, flexShrink: 0 }} />
                                )}
                                <div>
                                    <div style={{ fontWeight: 600 }}>{activity.name}</div>
                                    <div style={{ color: 'var(--text-muted, rgba(167,169,169,0.7))', fontSize: 13 }}>
                                        {activity.duration && <span>{Math.round(activity.duration / 60)}h &middot; </span>}
                                        <span>{formatPricePerPerson(activity.price)}</span>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                    <div style={{ fontWeight: 700, fontSize: '1.1rem', marginBottom: 24 }}>
                        Total: €{result.totalPrice}
                    </div>
                </>
            )}

            <button
                onClick={handleOpenTripBuilder}
                style={{ width: '100%', padding: '14px', background: 'var(--brand, #6A1B9A)', color: 'white', border: 'none', borderRadius: 8, fontWeight: 700, fontSize: '1rem', cursor: 'pointer' }}
            >
                Open in Trip Builder
            </button>
        </div>
    );
}

export default VoteResultPage;
