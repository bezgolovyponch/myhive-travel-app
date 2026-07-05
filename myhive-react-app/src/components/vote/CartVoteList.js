import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import voteApi from '../../services/voteApi';
import ActivityPreviewModal from '../ActivityPreviewModal';
import {formatPricePerPerson} from '../../utils/format';
import {pushEvent} from '../../utils/analytics';
import './CartVoteList.css';

const VOTED_KEY = (shareToken) => `myhive-voted-${shareToken}`;

// Upvote-only list ballot for CART sessions: tap ♥ on any activities you're up
// for (one vote each), then submit once. Details open in the preview modal —
// participants never leave the voting flow.
function CartVoteList({shareToken, activities, voterToken}) {
    const navigate = useNavigate();
    const [selected, setSelected] = useState(() => new Set());
    const [preview, setPreview] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState(null);

    const toggle = (activityId) => {
        setSelected(prev => {
            const next = new Set(prev);
            if (next.has(activityId)) {
                next.delete(activityId);
            } else {
                next.add(activityId);
            }
            return next;
        });
    };

    const submitSelectedVotes = async () => {
        await voteApi.castVotes(shareToken, {
            voterToken,
            votes: [...selected].map(activityId => ({activityId, liked: true})),
        });
        localStorage.setItem(VOTED_KEY(shareToken), 'true');
        pushEvent('vote_completed', {trip_id: shareToken, user_role: 'participant'});
        navigate(`/vote/${shareToken}/waiting`);
    };

    const handleSubmitError = (e) => {
        if (e.message === 'Session is full') {
            navigate(`/vote/${shareToken}/waiting`);
            return;
        }
        setError('Failed to submit your vote. Please try again.');
        setSubmitting(false);
    };

    const handleSubmit = async () => {
        if (selected.size === 0 || submitting) {
            return;
        }
        setSubmitting(true);
        setError(null);
        try {
            await submitSelectedVotes();
        } catch (e) {
            handleSubmitError(e);
        }
    };

    const previewLink = preview && preview.slug && preview.destinationSlug
        ? `/destination/${preview.destinationSlug}/activity/${preview.slug}`
        : null;

    return (
        <div className="cart-vote-page">
            <h1 className="cart-vote-title">Which activities are you up for?</h1>
            <p className="cart-vote-subtitle">Tap ♥ on everything you like — one vote per activity.</p>
            <ul className="cart-vote-list">
                {activities.map(activity => {
                    const isSelected = selected.has(activity.id);
                    return (
                        <li
                            key={activity.id}
                            className={`cart-vote-row ${isSelected ? 'cart-vote-row--selected' : ''}`}
                        >
                            {activity.imageUrl && (
                                <img
                                    src={activity.imageUrl}
                                    alt={activity.name}
                                    className="cart-vote-image"
                                    loading="lazy"
                                />
                            )}
                            <div className="cart-vote-content">
                                <div className="cart-vote-name">{activity.name}</div>
                                <div className="cart-vote-price">{formatPricePerPerson(activity.price)}</div>
                            </div>
                            <button
                                type="button"
                                className="cart-vote-info-btn"
                                aria-label={`About ${activity.name}`}
                                onClick={() => setPreview(activity)}
                            >
                                i
                            </button>
                            <button
                                type="button"
                                className={`cart-vote-toggle ${isSelected ? 'cart-vote-toggle--on' : ''}`}
                                aria-pressed={isSelected}
                                onClick={() => toggle(activity.id)}
                            >
                                {isSelected ? '♥ Voted' : '♥ Vote'}
                            </button>
                        </li>
                    );
                })}
            </ul>
            {error && <p className="cart-vote-error">{error}</p>}
            <button
                type="button"
                className="cart-vote-submit"
                onClick={handleSubmit}
                disabled={selected.size === 0 || submitting}
            >
                {submitting ? 'Submitting…' : `Submit vote (${selected.size})`}
            </button>
            <ActivityPreviewModal activity={preview} link={previewLink} onClose={() => setPreview(null)}/>
        </div>
    );
}

export default CartVoteList;
