import {useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import leadApi from '../services/leadApi';

// Deliberately a confirm-button page: mail scanners prefetch GET links, so the
// link itself must not unsubscribe anyone. The POST happens on click.
function UnsubscribePage() {
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');
    const [status, setStatus] = useState('idle');

    const handleUnsubscribe = async () => {
        setStatus('working');
        try {
            await leadApi.unsubscribe(token);
            setStatus('done');
        } catch (e) {
            setStatus('error');
        }
    };

    return (
        <div className="container" style={{maxWidth: 560, margin: '60px auto', textAlign: 'center'}}>
            {status === 'done' ? (
                <>
                    <h1>You&apos;re unsubscribed</h1>
                    <p>We won&apos;t send you any more trip reminders.</p>
                </>
            ) : (
                <>
                    <h1>Unsubscribe from trip reminders</h1>
                    <p>
                        You&apos;ll stop receiving reminder emails about your saved trip.
                        Booking and vote confirmations are not affected.
                    </p>
                    {status === 'error' && <p style={{color: '#c0392b'}}>Something went wrong. Please try again.</p>}
                    <button
                        type="button"
                        className="btn btn--primary"
                        onClick={handleUnsubscribe}
                        disabled={!token || status === 'working'}
                    >
                        {status === 'working' ? 'Unsubscribing…' : 'Unsubscribe'}
                    </button>
                </>
            )}
        </div>
    );
}

export default UnsubscribePage;
