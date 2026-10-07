import { useMemo, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import api from '../../services/api';
import voteApi from '../../services/voteApi';
import SwipeCard from '../../components/SwipeCard';
import FriendReview from '../../components/vote/FriendReview';
import FriendThankYou from '../../components/vote/FriendThankYou';
import { getOrCreateVoterToken, votedKey } from '../../utils/voterToken';
import { dashboardPath, tripDates } from '../../utils/groupVote';
import { managerKey } from '../../hooks/useOrganizerVote';
import { pushEvent } from '../../utils/analytics';
import { useT } from '../../i18n';
import VoteMeta from './VoteMeta';
import './ActivityVotePage.css';

// A friend's vote (v3): a full-screen Tinder-style swipe through the plan, then
// a review where they can flip a keep or drop and recommend activities from
// the catalogue, then one "Send to group". After that, and on every later
// visit, only the thank-you screen. Votes are anonymous (a random voter token).
// The organiser does not vote: their own link opens their dashboard instead.
function ActivityVoteContent() {
    const t = useT('vote');
    const { shareToken } = useParams();
    const navigate = useNavigate();

    const voterToken = useMemo(() => getOrCreateVoterToken(), []);
    const [voted, setVoted] = useState(() => Boolean(localStorage.getItem(votedKey(shareToken))));
    const [session, setSession] = useState(null);
    const [activities, setActivities] = useState([]);
    const [currentIndex, setCurrentIndex] = useState(0);
    const [step, setStep] = useState('swipe');
    const [votes, setVotes] = useState({});
    const [recommended, setRecommended] = useState([]);
    const [catalog, setCatalog] = useState([]);
    const [categories, setCategories] = useState([]);
    const [loading, setLoading] = useState(true);
    const [sending, setSending] = useState(false);
    const [sendError, setSendError] = useState(null);
    const [error, setError] = useState(null);
    const swipedRef = useRef([]);
    const voteOpenedFiredRef = useRef(new Set());
    const isOrganiser = Boolean(localStorage.getItem(managerKey(shareToken)));

    useEffect(() => {
        if (!voteOpenedFiredRef.current.has(shareToken)) {
            voteOpenedFiredRef.current.add(shareToken);
            pushEvent('vote_opened', { trip_id: shareToken, user_role: isOrganiser ? 'organizer' : 'participant' });
        }
    }, [shareToken, isOrganiser]);

    useEffect(() => {
        let cancelled = false;
        voteApi.getSession(shareToken)
            .then(s => {
                if (cancelled) return;
                if (isOrganiser) {
                    // The dashboard lives on the destination page; a vote without one gets the result
                    // page - the organiser is never left on "loading" (as VoteWaitingPage does it).
                    navigate(s.destinationSlug
                        ? dashboardPath(s.destinationSlug, shareToken)
                        : `/vote/${shareToken}/result`, { replace: true });
                    return;
                }
                setSession(s);
            })
            .catch(e => {
                // A friend hears about a missing session from the activities request below, with its
                // nicer "gone" message; the organiser makes no such request, so this is their only word.
                if (!cancelled && isOrganiser) setError(e.message);
            });
        if (voted || isOrganiser) {
            setLoading(false);
            return () => {
                cancelled = true;
            };
        }
        voteApi.getActivities(shareToken)
            .then(list => {
                if (!cancelled) setActivities(list);
            })
            .catch(e => {
                if (!cancelled) setError(e.message);
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [shareToken, voted, isOrganiser, navigate]);

    // The catalogue to recommend from, loaded while the friend swipes.
    useEffect(() => {
        if (!session?.destinationSlug || voted) return undefined;
        let cancelled = false;
        api.getDestinationBySlug(session.destinationSlug)
            .then(destination => Promise.all([
                api.getActivities(destination.id),
                api.getCategoriesForDestination(destination.id),
            ]))
            .then(([list, cats]) => {
                if (!cancelled) {
                    setCatalog(list);
                    setCategories(cats);
                }
            })
            .catch(() => {
                // Recommending is optional; the review still sends the vote.
            });
        return () => {
            cancelled = true;
        };
    }, [session?.destinationSlug, voted]);

    const handleSwipe = (direction, activityId) => {
        swipedRef.current.push({ activityId, liked: direction === 'right' });
        const nextIndex = currentIndex + 1;
        setCurrentIndex(nextIndex);
        if (nextIndex >= activities.length) {
            const byId = {};
            swipedRef.current.forEach(v => {
                byId[v.activityId] = v.liked;
            });
            setVotes(byId);
            setStep('review');
            pushEvent('vote_swiped', { trip_id: shareToken, user_role: 'participant' });
        }
    };

    const handleUndo = () => {
        if (swipedRef.current.length === 0) return;
        swipedRef.current.pop();
        setCurrentIndex(i => Math.max(0, i - 1));
    };

    const toggleRecommend = (activityId) => {
        setRecommended(list => (list.includes(activityId)
            ? list.filter(id => id !== activityId)
            : [...list, activityId]));
    };

    const send = async () => {
        if (sending) return;
        setSending(true);
        setSendError(null);
        try {
            await voteApi.castVotes(shareToken, {
                voterToken,
                votes: activities.map(a => ({ activityId: a.id, liked: votes[a.id] === true })),
                recommendedActivityIds: recommended,
            });
            pushEvent('vote_completed', {
                trip_id: shareToken, user_role: 'participant', recommended_count: recommended.length,
            });
            localStorage.setItem(votedKey(shareToken), 'true');
            setVoted(true);
        } catch (e) {
            if (e.message === 'Already voted' || e.message === 'Session is full') {
                localStorage.setItem(votedKey(shareToken), 'true');
                setVoted(true);
                return;
            }
            setSendError(t('activities.submitFailed'));
            setSending(false);
        }
    };

    if (voted) return <FriendThankYou/>;
    // An error first: the organiser is otherwise shown "loading" until the dashboard redirect, and a
    // vote that is gone has no dashboard to redirect to.
    if (error === 'Vote session not found') return (
        <div className="vote-state">
            <p className="vote-state-title">{t('activities.sessionGoneTitle')}</p>
            <p className="vote-state-muted">
                {t('activities.sessionGoneHint')}
            </p>
        </div>
    );
    if (error) return (
        <div className="vote-state vote-state--error">{error}</div>
    );
    if (loading || isOrganiser) return (
        <div className="vote-state">{t('activities.loading')}</div>
    );
    if (session && session.status !== 'ACTIVE') return <FriendThankYou closed/>;
    if (activities.length === 0) return (
        <div className="vote-state">
            <p>{t('activities.empty')}</p>
        </div>
    );

    if (step === 'review') {
        const dates = tripDates(session?.startDate, session?.endDate);
        const eyebrow = session?.destinationName && (dates
            ? t('review.eyebrow', { destination: session.destinationName, dates })
            : t('review.eyebrowNoDates', { destination: session.destinationName }));
        return (
            <FriendReview
                eyebrow={eyebrow}
                activities={activities}
                votes={votes}
                onVote={(id, liked) => setVotes(v => ({ ...v, [id]: liked }))}
                catalog={catalog}
                categories={categories}
                recommended={recommended}
                onToggleRecommend={toggleRecommend}
                onSend={send}
                sending={sending}
                error={sendError}
            />
        );
    }

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
            title={t('activities.title')}
            subtitle={t('activities.subtitle')}
            getCardLink={getCardLink}
            fullscreen
        />
    );
}

function ActivityVotePage() {
    const t = useT('vote');
    return (
        <>
            <VoteMeta title={t('meta.activities')}/>
            <ActivityVoteContent/>
        </>
    );
}

export default ActivityVotePage;
