import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import SwipeCard from '../../components/SwipeCard';

const VOTER_TOKEN_KEY = (shareToken) => `myhive-voter-${shareToken}`;
const VOTED_KEY = (shareToken) => `myhive-voted-${shareToken}`;

function generateUUID() {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
        return crypto.randomUUID();
    }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        return (c === 'x' ? r : ((r & 0x3) | 0x8)).toString(16);
    });
}

function getOrCreateVoterToken(shareToken) {
    const key = VOTER_TOKEN_KEY(shareToken);
    let token = localStorage.getItem(key);
    if (!token) {
        token = generateUUID();
        localStorage.setItem(key, token);
    }
    return token;
}

function ActivityVotePage() {
    const { shareToken } = useParams();
    const navigate = useNavigate();

    const [activities, setActivities] = useState([]);
    const [currentIndex, setCurrentIndex] = useState(0);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState(null);
    const votesRef = useRef([]);
    const voterToken = getOrCreateVoterToken(shareToken);

    useEffect(() => {
        if (localStorage.getItem(VOTED_KEY(shareToken))) {
            navigate(`/vote/${shareToken}/waiting`, { replace: true });
            return;
        }
        voteApi.getActivities(shareToken)
            .then(setActivities)
            .catch(e => setError(e.message))
            .finally(() => setLoading(false));
    }, [shareToken]);

    const handleSwipe = (direction, activityId) => {
        votesRef.current.push({ activityId, liked: direction === 'right' });
        const nextIndex = currentIndex + 1;
        setCurrentIndex(nextIndex);

        if (nextIndex >= activities.length) {
            submitVotes(votesRef.current);
        }
    };

    const submitVotes = async (votes) => {
        setSubmitting(true);
        try {
            await voteApi.castVotes(shareToken, {
                voterToken,
                votes: votes.map(v => ({ activityId: v.activityId, liked: v.liked })),
            });
            localStorage.setItem(VOTED_KEY(shareToken), 'true');
        } catch {
            // сессия может быть уже закрыта — всё равно идём на waiting
        }
        navigate(`/vote/${shareToken}/waiting`);
    };

    const stateStyle = { paddingTop: 'calc(var(--header-height) + 40px)', textAlign: 'center' };
    if (loading) return (
        <div style={{ ...stateStyle, color: 'var(--text, #f5f5f5)' }}>Loading activities...</div>
    );
    if (submitting) return (
        <div style={{ ...stateStyle, color: 'var(--text, #f5f5f5)' }}>Submitting your votes...</div>
    );
    if (error) return (
        <div style={{ ...stateStyle, color: '#dc3545' }}>{error}</div>
    );
    if (activities.length === 0) return (
        <div style={{ ...stateStyle, color: 'var(--text, #f5f5f5)' }}>
            <p>No activities found for the selected categories.</p>
        </div>
    );

    const shareUrl = `${window.location.origin}/vote/${shareToken}/activities`;

    return (
        <SwipeCard
            cards={activities}
            currentIndex={currentIndex}
            onSwipe={handleSwipe}
            title="Which activities are you up for?"
            subtitle="Swipe right to vote yes, left to skip"
            shareUrl={shareUrl}
        />
    );
}

export default ActivityVotePage;
