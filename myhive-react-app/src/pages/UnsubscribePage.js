import {useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import leadApi from '../services/leadApi';
import {useT} from '../i18n';

// Deliberately a confirm-button page: mail scanners prefetch GET links, so the
// link itself must not unsubscribe anyone. The POST happens on click.
function UnsubscribePage() {
    const t = useT('unsubscribe');
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
                    <h1>{t('doneTitle')}</h1>
                    <p>{t('doneBody')}</p>
                </>
            ) : (
                <>
                    <h1>{t('title')}</h1>
                    <p>
                        {t('body')}
                    </p>
                    {status === 'error' && <p style={{color: '#c0392b'}}>{t('error')}</p>}
                    <button
                        type="button"
                        className="btn btn--primary"
                        onClick={handleUnsubscribe}
                        disabled={!token || status === 'working'}
                    >
                        {status === 'working' ? t('working') : t('confirm')}
                    </button>
                </>
            )}
        </div>
    );
}

export default UnsubscribePage;
