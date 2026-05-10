import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import voteApi from '../../services/voteApi';

function VoteWaitingPage() {
    const { shareToken } = useParams();
    const [session, setSession] = useState(null);
    const [participantCount, setParticipantCount] = useState(0);
    const [timeLeft, setTimeLeft] = useState('');
    const [copied, setCopied] = useState(false);

    useEffect(() => {
        voteApi.getSession(shareToken).then(setSession).catch(() => {});
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
            .catch(() => {});
        poll();
        const id = setInterval(poll, 30_000);
        return () => clearInterval(id);
    }, [shareToken]);

    const shareUrl = `${window.location.origin}/vote/${shareToken}/activities`;

    const handleCopy = () => {
        navigator.clipboard.writeText(shareUrl);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    return (
        <div style={{ maxWidth: 480, margin: '60px auto', padding: '0 16px', textAlign: 'center' }}>
            <h2 style={{ marginBottom: 8 }}>Voting is open!</h2>
            <p style={{ color: '#6c757d', marginBottom: 32 }}>
                {session?.destinationName ? `Trip to ${session.destinationName}` : 'Your vote session'}
            </p>

            <div style={{ background: '#f8f9fa', borderRadius: 12, padding: 24, marginBottom: 24 }}>
                <div style={{ fontSize: '2rem', fontWeight: 700, color: '#6A1B9A' }}>{timeLeft}</div>
                <div style={{ color: '#6c757d', fontSize: 14, marginTop: 4 }}>until results</div>
            </div>

            <div style={{ background: '#f8f9fa', borderRadius: 12, padding: 24, marginBottom: 24 }}>
                <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>{participantCount}</div>
                <div style={{ color: '#6c757d', fontSize: 14, marginTop: 4 }}>
                    {participantCount === 1 ? 'person voted' : 'people voted'}
                </div>
            </div>

            <p style={{ marginBottom: 12, fontWeight: 600 }}>Share with friends:</p>
            <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                <input
                    readOnly
                    value={shareUrl}
                    style={{ flex: 1, padding: '8px 12px', borderRadius: 6, border: '1px solid #dee2e6', fontSize: 13 }}
                />
                <button
                    onClick={handleCopy}
                    style={{ padding: '8px 16px', background: copied ? '#28a745' : '#6A1B9A', color: 'white', border: 'none', borderRadius: 6, cursor: 'pointer', fontWeight: 600 }}
                >
                    {copied ? 'Copied!' : 'Copy'}
                </button>
            </div>
            <p style={{ color: '#6c757d', fontSize: 13 }}>
                Results will be emailed to the trip organiser after the timer ends.
            </p>
        </div>
    );
}

export default VoteWaitingPage;
