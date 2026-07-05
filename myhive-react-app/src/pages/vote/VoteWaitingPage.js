import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import VoteMeta from './VoteMeta';
import VoteTallyCard from '../../components/vote/VoteTallyCard';
import { copyToClipboard } from '../../utils/clipboard';
import { getOrCreateVoterToken, votedKey } from '../../utils/voterToken';
import './VoteWaitingPage.css';

function VoteWaitingContent() {
    const { shareToken } = useParams();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const [isInitiator, setIsInitiator] = useState(
        () => !!localStorage.getItem(`myhive-initiator-${shareToken}`));
    const [managerToken, setManagerToken] = useState(
        () => localStorage.getItem(`myhive-manager-${shareToken}`));
    const [hasVoted] = useState(
        () => !!localStorage.getItem(votedKey(shareToken)));
    const voterToken = useMemo(() => getOrCreateVoterToken(), []);

    const [session, setSession] = useState(null);
    const [participantCount, setParticipantCount] = useState(0);
    const [timeLeft, setTimeLeft] = useState('');
    const [copied, setCopied] = useState(false);
    const [sessionError, setSessionError] = useState(null);
    const [closing, setClosing] = useState(false);
    const [tally, setTally] = useState(null);

    // Adopt a managerToken arriving via the email dashboard link (?manager=...),
    // persist it, then strip it from the URL so the secret isn't left in history.
    useEffect(() => {
        const urlManager = searchParams.get('manager');
        if (!urlManager) {
            return;
        }
        localStorage.setItem(`myhive-manager-${shareToken}`, urlManager);
        localStorage.setItem(`myhive-initiator-${shareToken}`, 'true');
        setManagerToken(urlManager);
        setIsInitiator(true);
        navigate(`/vote/${shareToken}/waiting`, { replace: true });
    }, [searchParams, shareToken, navigate]);

    // Poll the full session (it includes participantCount) so we also notice
    // the organizer closing the session / the 24h expiry while we wait.
    useEffect(() => {
        let cancelled = false;
        const load = (initial) => voteApi.getSession(shareToken)
            .then(s => {
                if (cancelled) {
                    return;
                }
                setSession(s);
                setParticipantCount(s.participantCount);
                // A later successful poll recovers from a transient initial failure.
                setSessionError(null);
                if (s.status === 'COMPLETED') {
                    if (s.voteMode === 'CART') {
                        // Cart votes never hydrate the Trip Builder — everyone sees
                        // the ranked results page instead.
                        navigate(`/vote/${shareToken}/result`, { replace: true });
                    } else if (s.destinationSlug) {
                        navigate(`/destination/${s.destinationSlug}?tab=trip-builder&voteSession=${shareToken}`,
                            { replace: true });
                    } else {
                        navigate(`/vote/${shareToken}/result`, { replace: true });
                    }
                }
            })
            .catch(e => {
                if (cancelled) {
                    return;
                }
                if (initial) {
                    setSessionError(e.message);
                } else {
                    // Transient poll failure — keep the page up, try again next tick.
                    console.error('session poll error:', e);
                }
            });
        load(true);
        const id = setInterval(() => load(false), 30_000);
        return () => {
            cancelled = true;
            clearInterval(id);
        };
    }, [shareToken, navigate]);

    // Live tally (CART only): visible once you've voted, or to the initiator via
    // the manager token — they authored the list, so seeing votes can't bias them.
    const voteMode = session?.voteMode;
    useEffect(() => {
        if (voteMode !== 'CART' || (!hasVoted && !managerToken)) {
            return undefined;
        }
        let cancelled = false;
        const load = () => voteApi.getTally(shareToken, {
            voterToken: hasVoted ? voterToken : null,
            managerToken,
        }).then(t => {
            if (!cancelled) {
                setTally(t);
            }
        }).catch(() => {
            // Transient tally failure — keep the last tally, retry next tick.
        });
        load();
        const id = setInterval(load, 30_000);
        return () => {
            cancelled = true;
            clearInterval(id);
        };
    }, [voteMode, hasVoted, managerToken, shareToken, voterToken]);

    // Keyed on expiresAt (a stable string), not the session object — the 30s
    // poll stores a fresh object each tick and would needlessly restart the
    // countdown interval.
    const expiresAt = session?.expiresAt;
    useEffect(() => {
        if (!expiresAt) return;
        const tick = () => {
            const diff = new Date(expiresAt) - Date.now();
            if (diff <= 0) { setTimeLeft('Processing results...'); return; }
            const hours = Math.floor(diff / 3_600_000);
            const minutes = Math.floor((diff % 3_600_000) / 60_000);
            setTimeLeft(`${hours}h ${minutes}m`);
        };
        tick();
        const id = setInterval(tick, 60_000);
        return () => clearInterval(id);
    }, [expiresAt]);

    const handleClose = useCallback(() => {
        if (closing || !managerToken) return;
        setClosing(true);
        voteApi.closeSession(shareToken, managerToken)
            .catch(() => {})
            .finally(() => {
                if (session?.voteMode === 'CART') {
                    navigate(`/vote/${shareToken}/result`);
                    return;
                }
                const destinationSlug = session?.destinationSlug;
                if (destinationSlug) {
                    navigate(`/destination/${destinationSlug}?tab=trip-builder&voteSession=${shareToken}`);
                } else {
                    navigate(`/vote/${shareToken}/result`);
                }
            });
    }, [closing, managerToken, shareToken, navigate, session?.destinationSlug, session?.voteMode]);

    // Session closes manually (organizer's "End voting early" button) or
    // automatically when expiresAt hits (24h scheduler). No auto-close on
    // participant-count threshold — `numberOfTravelers` is a pricing input,
    // not an expected-voter count.

    const shareUrl = `${window.location.origin}/vote/${shareToken}/activities?ref=invite`;

    const handleCopy = () => {
        copyToClipboard(shareUrl).then(ok => {
            if (ok) {
                setCopied(true);
                setTimeout(() => setCopied(false), 2000);
            }
            // On failure the readonly input above stays visible for manual copying.
        });
    };

    // Native share sheet (WhatsApp/Telegram/Mail/...) — only available on
    // supporting browsers (mobile + Safari/Edge desktop). We feature-detect and
    // fall back to the Copy button everywhere else.
    const canShare = typeof navigator !== 'undefined' && typeof navigator.share === 'function';

    const handleShare = () => {
        navigator.share({
            title: session?.destinationName ? `Vote: trip to ${session.destinationName}` : 'Vote on our trip',
            text: 'Help pick what we do — vote on the activities:',
            url: shareUrl,
        }).catch(() => {
            // User dismissed the share sheet (AbortError) or the OS rejected it — nothing to do.
        });
    };

    if (sessionError) return (
        <div className="vote-waiting-page vote-waiting-page--error">
            <p>Could not load session: {sessionError}</p>
        </div>
    );

    if (closing) return (
        <div className="vote-waiting-page">
            <p>Finalising results...</p>
        </div>
    );

    return (
        <div className="vote-waiting-page">
            <h2 className="vote-waiting-title">Voting is open!</h2>
            <p className="vote-waiting-subtitle">
                {session?.destinationName ? `Trip to ${session.destinationName}` : 'Your vote session'}
            </p>

            {hasVoted && (
                <p className="vote-waiting-voted">
                    ✓ You’ve already voted in this session.
                </p>
            )}

            <div className="vote-waiting-card">
                <div className="vote-waiting-count">{timeLeft}</div>
                <div className="vote-waiting-card-label">until results</div>
            </div>

            <div className="vote-waiting-card">
                <div className="vote-waiting-participants">{session ? participantCount : '...'}</div>
                <div className="vote-waiting-card-label">
                    {session && (participantCount === 1 ? 'person voted' : 'people voted')}
                    {session?.numberOfTravelers > 0 && ` of ${session.numberOfTravelers}`}
                </div>
            </div>

            {tally && tally.rows.length > 0 && (
                <VoteTallyCard
                    title="Live results"
                    participantCount={tally.participantCount}
                    rows={tally.rows}
                />
            )}

            {/* Primary CTA: sharing the invite link is the main thing to do here */}
            <p className="vote-waiting-share-label">Share with friends:</p>
            <input
                readOnly
                aria-label="Invite link"
                value={shareUrl}
                className="vote-waiting-share-input"
            />
            <div className="vote-waiting-actions">
                {canShare && (
                    <button
                        type="button"
                        onClick={handleShare}
                        className="vote-waiting-btn"
                    >
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                            <path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8" />
                            <polyline points="16 6 12 2 8 6" />
                            <line x1="12" y1="2" x2="12" y2="15" />
                        </svg>
                        Share
                    </button>
                )}
                <button
                    type="button"
                    onClick={handleCopy}
                    className={`vote-waiting-btn ${copied ? 'vote-waiting-btn--copied' : ''}`}
                >
                    {copied ? '✓ Link copied!' : (
                        <>
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                                <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                            </svg>
                            {canShare ? 'Copy link' : 'Copy invite link'}
                        </>
                    )}
                </button>
            </div>

            {/* Secondary, de-emphasised action placed low so it isn't clicked by reflex right after creating the session */}
            {isInitiator && (
                <button
                    type="button"
                    onClick={handleClose}
                    className="vote-waiting-close-btn"
                >
                    End voting early &amp; see results
                </button>
            )}

            <p className="vote-waiting-note">
                Results will be emailed to the trip organiser after the timer ends.
            </p>
        </div>
    );
}

function VoteWaitingPage() {
    return (
        <>
            <VoteMeta title="Voting open"/>
            <VoteWaitingContent/>
        </>
    );
}

export default VoteWaitingPage;
