import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import SwipeCard from '../../components/SwipeCard';

const VOTER_TOKEN_KEY = (shareToken) => `myhive-voter-${shareToken}`;

function getOrCreateVoterToken(shareToken) {
    const key = VOTER_TOKEN_KEY(shareToken);
    let token = localStorage.getItem(key);
    if (!token) {
        token = crypto.randomUUID();
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
    const [error, setError] = useState(null);
    const voterToken = getOrCreateVoterToken(shareToken);

    useEffect(() => {
        voteApi.getActivities(shareToken)
            .then(setActivities)
            .catch(e => setError(e.message))
            .finally(() => setLoading(false));
    }, [shareToken]);

    const handleSwipe = async (direction, activityId) => {
        const nextIndex = currentIndex + 1;
        setCurrentIndex(nextIndex);

        try {
            await voteApi.castVote(shareToken, {
                voterToken,
                activityId,
                liked: direction === 'right',
            });
        } catch (e) {
            if (e.message === 'Session is full') {
                navigate(`/vote/${shareToken}/waiting`);
                return;
            }
        }

        if (nextIndex >= activities.length) {
            navigate(`/vote/${shareToken}/waiting`);
        }
    };

    if (loading) return <div style={{ padding: 40, textAlign: 'center' }}>Loading activities...</div>;
    if (error) return <div style={{ padding: 40, textAlign: 'center', color: 'red' }}>{error}</div>;
    if (activities.length === 0) {
        return (
            <div style={{ padding: 40, textAlign: 'center' }}>
                <p>No activities found for the selected categories.</p>
            </div>
        );
    }

    return (
        <SwipeCard
            cards={activities}
            currentIndex={currentIndex}
            onSwipe={handleSwipe}
            title="Which activities are you up for?"
            subtitle="Swipe right to vote yes, left to skip"
        />
    );
}

export default ActivityVotePage;
