import { useMemo, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import SwipeCard from '../../components/SwipeCard';
import { getOrCreateVoterToken, votedKey } from '../../utils/voterToken';
import { pushEvent } from '../../utils/analytics';
import { funnelParams } from '../../utils/funnel';
import VoteMeta from './VoteMeta';
import './ActivityVotePage.css';

function ActivityVoteContent() {
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
    const voteOpenedFiredRef = useRef(new Set());

    useEffect(() => {
        if (!voteOpenedFiredRef.current.has(shareToken)) {
            voteOpenedFiredRef.current.add(shareToken);
            pushEvent('vote_opened', { ...funnelParams({ voteId: shareToken }), trip_id: shareToken, user_role: 'participant' });
            voteApi.recordOpen(shareToken, voterToken);
        }
    }, [shareToken, voterToken]);

    useEffect(() => {
        if (localStorage.getItem(votedKey(shareToken))) {
            navigate(`/vote/${shareToken}/waiting`, { replace: true });
            return;
        }
        voteApi.getActivities(shareToken)
            .then(setActivities)
            .catch(e => setError(e.message))
            .finally(() => setLoading(false));
    }, [shareToken, navigate]);

    const handleSwipe = (direction, activityId) => {
        if (submittingRef.current) return;
        votesRef.current.push({ activityId, liked: direction === 'right' });
        const nextIndex = currentIndex + 1;
        setCurrentIndex(nextIndex);

        if (nextIndex >= activities.length) {
            submitVotes(votesRef.current);
        }
    };

    const handleUndo = () => {
        if (votesRef.current.length === 0 || submittingRef.current) return;
        votesRef.current.pop();
        setCurrentIndex(i => Math.max(0, i - 1));
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
            localStorage.setItem(votedKey(shareToken), 'true');
            pushEvent('vote_completed', { ...funnelParams({ voteId: shareToken }), trip_id: shareToken, user_role: 'participant' });
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

    if (loading) return (
        <div className="vote-state">Loading activities...</div>
    );
    if (submitting) return (
        <div className="vote-state">Submitting your votes...</div>
    );
    if (error === 'Vote session not found') return (
        <div className="vote-state">
            <p className="vote-state-title">This vote session no longer exists.</p>
            <p className="vote-state-muted">
                It may have expired or been removed — ask the organiser for a new link.
            </p>
        </div>
    );
    if (error) return (
        <div className="vote-state vote-state--error">{error}</div>
    );
    if (activities.length === 0) return (
        <div className="vote-state">
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
            onUndo={handleUndo}
            canUndo={currentIndex > 0}
            title="Which activities are you up for?"
            subtitle="Swipe right to vote yes, left to skip"
            shareUrl={shareUrl}
            getCardLink={getCardLink}
        />
    );
}

function ActivityVotePage() {
    return (
        <>
            <VoteMeta title="Vote on activities"/>
            <ActivityVoteContent/>
        </>
    );
}

export default ActivityVotePage;
