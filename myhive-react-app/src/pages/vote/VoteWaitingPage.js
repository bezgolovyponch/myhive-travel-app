import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import VoteMeta from './VoteMeta';
import FriendThankYou from '../../components/vote/FriendThankYou';
import { managerKey } from '../../hooks/useOrganizerVote';
import { dashboardPath } from '../../utils/groupVote';
import { votedKey } from '../../utils/voterToken';
import { useT } from '../../i18n';
import './VoteWaitingPage.css';

// The old waiting page. The organiser's live view moved to the Trip Builder
// dashboard, so the organiser (a stored manager token, or the ?manager= of an
// older email link) is sent there. Everyone else is a friend: once they voted
// they only get the thank-you screen; one who has not voted yet goes to the
// swipe.
function VoteWaitingContent() {
    const t = useT('vote');
    const { shareToken } = useParams();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const managerParam = searchParams.get('manager');
    const isOrganiser = Boolean(managerParam || localStorage.getItem(managerKey(shareToken)));
    const hasVoted = Boolean(localStorage.getItem(votedKey(shareToken)));
    const [sessionError, setSessionError] = useState(null);

    useEffect(() => {
        if (!isOrganiser) {
            if (!hasVoted) {
                navigate(`/vote/${shareToken}/activities`, { replace: true });
            }
            return undefined;
        }
        let cancelled = false;
        voteApi.getSession(shareToken)
            .then(session => {
                if (cancelled) return;
                const path = dashboardPath(session.destinationSlug, shareToken);
                navigate(managerParam ? `${path}&manager=${encodeURIComponent(managerParam)}` : path, { replace: true });
            })
            .catch(e => {
                if (!cancelled) setSessionError(e.message);
            });
        return () => {
            cancelled = true;
        };
    }, [isOrganiser, hasVoted, managerParam, shareToken, navigate]);

    if (sessionError) return (
        <div className="vote-waiting-page vote-waiting-page--error">
            <p>{t('waiting.sessionError', { error: sessionError })}</p>
        </div>
    );
    if (isOrganiser || !hasVoted) return (
        <div className="vote-waiting-page">
            <p>{t('waiting.finalising')}</p>
        </div>
    );
    return <FriendThankYou/>;
}

function VoteWaitingPage() {
    const t = useT('vote');
    return (
        <>
            <VoteMeta title={t('meta.waiting')}/>
            <VoteWaitingContent/>
        </>
    );
}

export default VoteWaitingPage;
