import { useMemo, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import SwipeCard from '../../components/SwipeCard';
import { getOrCreateVoterToken } from '../../utils/voterToken';

const VOTED_KEY = (shareToken) => `myhive-voted-${shareToken}`;

function ActivityVotePage() {
    const { shareToken } = useParams();
    const navigate = useNavigate();

    const voterToken = useMemo(() => getOrCreateVoterToken(), []);
    const [activities, setActivities] = useState([]);
    const [currentIndex, setCurrentIndex] = useState(0);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState(null);
    const votesRef = useRef([]);
    const submittingRef = useRef(false);

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
        if (submittingRef.current) return;
        votesRef.current.push({ activityId, liked: direction === 'right' });
        const nextIndex = currentIndex + 1;
        setCurrentIndex(nextIndex);

        if (nextIndex >= activities.length) {
            submitVotes(votesRef.current);
        }
    };

    const submitVotes = async (votes) => {
        if (submittingRef.current) return;
        submittingRef.current = true;
        setSubmitting(true);
        setError(null);

        const seen = new Set();
        const deduped = votes.filter(v => {
            if (seen.has(v.activityId)) return false;
            seen.add(v.activityId);
            return true;
        });

        try {
            await voteApi.castVotes(shareToken, {
                voterToken,
                votes: deduped.map(v => ({ activityId: v.activityId, liked: v.liked })),
            });
            localStorage.setItem(VOTED_KEY(shareToken), 'true');
            navigate(`/vote/${shareToken}/waiting`);
        } catch (e) {
            if (e.message === 'Session is full') {
                navigate(`/vote/${shareToken}/waiting`);
            } else {
                submittingRef.current = false;
                setSubmitting(false);
                setError('Failed to submit votes. Please try again.');
            }
        }
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

    const getCardLink = (activity) => {
        if (!activity || !activity.slug || !activity.destinationSlug) return null;
        return `/destination/${activity.destinationSlug}/activity/${activity.slug}`;
    };

    return (
        <SwipeCard
            cards={activities}
            currentIndex={currentIndex}
            onSwipe={handleSwipe}
            title="Which activities are you up for?"
            subtitle="Swipe right to vote yes, left to skip"
            shareUrl={shareUrl}
            getCardLink={getCardLink}
        />
    );
}

export default ActivityVotePage;
