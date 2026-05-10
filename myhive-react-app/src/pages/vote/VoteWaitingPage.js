import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import voteApi from '../../services/voteApi';

function VoteWaitingPage() {
    const { shareToken } = useParams();
    const navigate = useNavigate();
    const isInitiator = !!localStorage.getItem(`myhive-initiator-${shareToken}`);

    const [session, setSession] = useState(null);
    const [participantCount, setParticipantCount] = useState(0);
    const [timeLeft, setTimeLeft] = useState('');
    const [copied, setCopied] = useState(false);
    const [sessionError, setSessionError] = useState(null);
    const [closing, setClosing] = useState(false);

    useEffect(() => {
        voteApi.getSession(shareToken)
            .then(s => {
                setSession(s);
                if (s.status === 'COMPLETED') {
                    navigate(`/vote/${shareToken}/result`, { replace: true });
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

    useEffect(() => {
        if (!session || closing) return;
        if (session.numberOfTravelers > 0 && participantCount >= session.numberOfTravelers) {
            handleClose();
        }
    }, [participantCount, session?.numberOfTravelers, closing]);

    const handleClose = () => {
        if (closing) return;
        setClosing(true);
        voteApi.closeSession(shareToken)
            .catch(() => {})
            .finally(() => navigate(`/vote/${shareToken}/result`));
    };

    const shareUrl = `${window.location.origin}/vote/${shareToken}/activities`;

    const handleCopy = () => {
        navigator.clipboard.writeText(shareUrl);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
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
                <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>{participantCount}</div>
                <div style={{ color: 'var(--text-muted, rgba(167,169,169,0.7))', fontSize: 14, marginTop: 4 }}>
                    {participantCount === 1 ? 'person voted' : 'people voted'}
                    {session?.numberOfTravelers > 0 && ` of ${session.numberOfTravelers}`}
                </div>
            </div>

            {isInitiator && (
                <button
                    onClick={handleClose}
                    style={{ width: '100%', padding: '12px 0', marginBottom: 24, background: 'var(--brand, #6A1B9A)', color: 'white', border: 'none', borderRadius: 8, fontSize: 15, fontWeight: 600, cursor: 'pointer' }}
                >
                    End voting early &amp; see results
                </button>
            )}

            <p style={{ marginBottom: 12, fontWeight: 600 }}>Share with friends:</p>
            <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                <input
                    readOnly
                    value={shareUrl}
                    style={{ flex: 1, padding: '8px 12px', borderRadius: 6, border: '1px solid var(--border, rgba(119,124,124,0.3))', fontSize: 13, background: 'var(--surface, #262828)', color: 'var(--text, #f5f5f5)' }}
                />
                <button
                    onClick={handleCopy}
                    style={{ padding: '8px 16px', background: copied ? '#28a745' : 'var(--brand, #6A1B9A)', color: 'white', border: 'none', borderRadius: 6, cursor: 'pointer', fontWeight: 600 }}
                >
                    {copied ? 'Copied!' : 'Copy'}
                </button>
            </div>
            <p style={{ color: 'var(--text-muted, rgba(167,169,169,0.7))', fontSize: 13 }}>
                Results will be emailed to the trip organiser after the timer ends.
            </p>
        </div>
    );
}

export default VoteWaitingPage;
