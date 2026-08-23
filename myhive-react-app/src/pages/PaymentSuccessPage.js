import {useEffect} from 'react';
import {Link, useSearchParams} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import {WHATSAPP_URL} from '../services/config';
import {pushEvent} from '../utils/analytics';
import {useT} from '../i18n';
import './PaymentReturnPages.css';

function PaymentSuccessPage() {
    const t = useT('payment');
    const [params] = useSearchParams();
    const booking = params.get('booking');
    const {dispatch} = useTrip();

    // Card-only checkout: reaching this return URL means the charge succeeded, so fire the
    // payment_completed conversion (→ GA4 purchase / Meta Purchase). value/currency/trip_id are
    // server-authoritative params on the URL (see PaymentService success_url). sessionStorage keyed
    // by booking id dedups a refresh / back-forward re-open; the webhook remains the money source
    // of truth, this is the client-side analytics signal.
    useEffect(() => {
        if (!booking) {
            return;
        }
        const dedupKey = `myhive-paid-${booking}`;
        if (sessionStorage.getItem(dedupKey)) {
            return;
        }
        sessionStorage.setItem(dedupKey, '1');
        const value = parseFloat(params.get('value'));
        pushEvent('payment_completed', {
            transaction_id: booking,
            value: Number.isFinite(value) ? value : undefined,
            currency: params.get('currency') || undefined,
            trip_id: params.get('trip_id') || undefined,
        });
        // params is derived from the URL, stable for this mount; booking is the meaningful trigger.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [booking]);

    useEffect(() => {
        // The paid-for activities are booked now — clear the builder the same way the lead flow
        // does after submit, so the trip doesn't linger and get booked twice. The backend returns
        // the buyer to the origin they paid from, so this reaches the right localStorage.
        // Require the booking param so a bare /payment/success (opened from history) is a no-op.
        // This is not a security gate: a crafted ?booking=x link can still wipe an unsaved cart,
        // but that is harmless griefing (no auth/payment/PII impact) — the same trip-clear the
        // lead flow performs on submit.
        if (!booking) {
            return;
        }
        dispatch({type: 'CANCEL_TRIP_SETUP'});
        dispatch({type: 'UPDATE_TRIP_TRAVELERS', travelers: 1});
        dispatch({type: 'UPDATE_TRIP_DATES', startDate: '', endDate: ''});
        dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'});
        // The paid-for trip may have come from a group vote session — clear the
        // stored token too, so a later visit to the Trip Builder doesn't try to
        // re-annotate a vote that's already been paid for.
        localStorage.removeItem('myhive-trip-vote-session');
    }, [dispatch, booking]);

    return (
        <div className="payment-return">
            <h1>{t('success.title')}</h1>
            <p>{t('success.body')}</p>
            {booking && <p className="payment-return__ref">{t('success.bookingReference', {booking})}</p>}
            <div className="success-whatsapp">
                <h5>{t('success.contactHeading')}</h5>
                <a
                    className="success-whatsapp-link"
                    href={WHATSAPP_URL}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label={t('success.whatsappAria')}
                >
                    <i className="ph ph-whatsapp-logo" aria-hidden="true"/> {t('success.whatsappUs')}
                </a>
            </div>
            <Link to="/" className="btn btn--primary">{t('backToHome')}</Link>
        </div>
    );
}

export default PaymentSuccessPage;
