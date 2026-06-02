import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import voteApi from '../../services/voteApi';

function VoteWaitingPage() {
    const { shareToken } = useParams();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const [isInitiator, setIsInitiator] = useState(
        () => !!localStorage.getItem(`myhive-initiator-${shareToken}`));
    const [managerToken, setManagerToken] = useState(
        () => localStorage.getItem(`myhive-manager-${shareToken}`));

    const [session, setSession] = useState(null);
    const [participantCount, setParticipantCount] = useState(0);
    const [timeLeft, setTimeLeft] = useState('');
    const [copied, setCopied] = useState(false);
    const [sessionError, setSessionError] = useState(null);
    const [closing, setClosing] = useState(false);

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

    useEffect(() => {
        voteApi.getSession(shareToken)
            .then(s => {
                setSession(s);
                setParticipantCount(s.participantCount);
                if (s.status === 'COMPLETED' && s.destinationSlug) {
                    navigate(`/destination/${s.destinationSlug}?tab=trip-builder&voteSession=${shareToken}`,
                        { replace: true });
                }
            })
            .catch(e => setSessionError(e.message));
    }, [shareToken]);

    useEffect(() => {
        if (!session?.expiresAt) return;
        const tick = () => {
            const diff = new Date(session.expiresAt) - Date.now();
            if (diff <= 0) { setTimeLeft('Processing results...'); return; }
            const hours = Math.floor(diff / 3_600_000);
            const minutes = Math.floor((diff % 3_600_000) / 60_000);
            setTimeLeft(`${hours}h ${minutes}m`);
        };
        tick();
        const id = setInterval(tick, 60_000);
        return () => clearInterval(id);
    }, [session]);

    useEffect(() => {
        const poll = () => voteApi.getParticipantCount(shareToken)
            .then(data => setParticipantCount(data.count))
            .catch(e => console.error('participant-count error:', e));
        poll();
        const id = setInterval(poll, 30_000);
        return () => clearInterval(id);
    }, [shareToken]);

    const handleClose = useCallback(() => {
        if (closing || !managerToken) return;
        setClosing(true);
        voteApi.closeSession(shareToken, managerToken)
            .catch(() => {})
            .finally(() => {
                const destinationSlug = session?.destinationSlug;
                if (destinationSlug) {
                    navigate(`/destination/${destinationSlug}?tab=trip-builder&voteSession=${shareToken}`);
                } else {
                    navigate(`/vote/${shareToken}/result`);
                }
            });
    }, [closing, managerToken, shareToken, navigate, session?.destinationSlug]);

    // Session closes manually (organizer's "End voting early" button) or
    // automatically when expiresAt hits (24h scheduler). No auto-close on
    // participant-count threshold — `numberOfTravelers` is a pricing input,
    // not an expected-voter count.

    const shareUrl = `${window.location.origin}/vote/${shareToken}/activities`;

    const handleCopy = () => {
        navigator.clipboard.writeText(shareUrl);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
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

    const pageStyle = { maxWidth: 480, margin: '0 auto', padding: 'calc(var(--header-height) + 24px) 16px 40px', textAlign: 'center' };

    if (sessionError) return (
        <div style={{ ...pageStyle, color: '#dc3545' }}>
            <p>Could not load session: {sessionError}</p>
        </div>
    );

    if (closing) return (
        <div style={{ ...pageStyle, color: 'var(--text, #f5f5f5)' }}>
            <p>Finalising results...</p>
        </div>
    );

    return (
        <div style={{ ...pageStyle, color: 'var(--text, #f5f5f5)' }}>
            <h2 style={{ marginBottom: 8 }}>Voting is open!</h2>
            <p style={{ color: 'var(--text-muted, rgba(167,169,169,0.7))', marginBottom: 32 }}>
                {session?.destinationName ? `Trip to ${session.destinationName}` : 'Your vote session'}
            </p>

            <div style={{ background: 'var(--surface, #262828)', border: '1px solid var(--card-border, rgba(119,124,124,0.15))', borderRadius: 12, padding: 24, marginBottom: 24 }}>
                <div style={{ fontSize: '2rem', fontWeight: 700, color: 'var(--brand, #6A1B9A)' }}>{timeLeft}</div>
                <div style={{ color: 'var(--text-muted, rgba(167,169,169,0.7))', fontSize: 14, marginTop: 4 }}>until results</div>
            </div>

            <div style={{ background: 'var(--surface, #262828)', border: '1px solid var(--card-border, rgba(119,124,124,0.15))', borderRadius: 12, padding: 24, marginBottom: 24 }}>
                <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>{session ? participantCount : '...'}</div>
                <div style={{ color: 'var(--text-muted, rgba(167,169,169,0.7))', fontSize: 14, marginTop: 4 }}>
                    {session && (participantCount === 1 ? 'person voted' : 'people voted')}
                    {session?.numberOfTravelers > 0 && ` of ${session.numberOfTravelers}`}
                </div>
            </div>

            {/* Primary CTA: sharing the invite link is the main thing to do here */}
            <p style={{ marginBottom: 12, fontWeight: 600 }}>Share with friends:</p>
            <input
                readOnly
                value={shareUrl}
                style={{ width: '100%', boxSizing: 'border-box', padding: '12px 14px', marginBottom: 12, borderRadius: 8, border: '1px solid var(--border, rgba(119,124,124,0.3))', fontSize: 14, background: 'var(--surface, #262828)', color: 'var(--text, #f5f5f5)', textAlign: 'center' }}
            />
            <div style={{ display: 'flex', gap: 8, marginBottom: 24 }}>
                {canShare && (
                    <button
                        onClick={handleShare}
                        style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '16px 0', background: 'var(--brand, #6A1B9A)', color: 'white', border: 'none', borderRadius: 10, cursor: 'pointer', fontWeight: 700, fontSize: 17 }}
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
                    onClick={handleCopy}
                    style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '16px 0', background: copied ? '#28a745' : 'var(--brand, #6A1B9A)', color: 'white', border: 'none', borderRadius: 10, cursor: 'pointer', fontWeight: 700, fontSize: 17 }}
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
                    onClick={handleClose}
                    style={{ width: '100%', padding: '10px 0', marginBottom: 16, background: 'rgba(119,124,124,0.12)', color: 'var(--brand, #6A1B9A)', border: '1px solid var(--brand, #6A1B9A)', borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: 'pointer' }}
                >
                    End voting early &amp; see results
                </button>
            )}

            <p style={{ color: 'var(--text-muted, rgba(167,169,169,0.7))', fontSize: 13 }}>
                Results will be emailed to the trip organiser after the timer ends.
            </p>
        </div>
    );
}

export default VoteWaitingPage;
