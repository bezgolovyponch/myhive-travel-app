import {useEffect} from 'react';
import {Link, useSearchParams} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import {WHATSAPP_URL} from '../services/config';
import './PaymentReturnPages.css';

function PaymentSuccessPage() {
    const [params] = useSearchParams();
    const booking = params.get('booking');
    const {dispatch} = useTrip();

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
            <h1>Thank you — your payment is on its way!</h1>
            <p>We're confirming your payment with our provider. You'll get an email once it's processed.</p>
            {booking && <p className="payment-return__ref">Booking reference: {booking}</p>}
            <div className="success-whatsapp">
                <h5>Contact us to get details about your trip</h5>
                <a
                    className="success-whatsapp-link"
                    href={WHATSAPP_URL}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label="Contact us on WhatsApp"
                >
                    <i className="ph ph-whatsapp-logo" aria-hidden="true"/> WhatsApp us
                </a>
            </div>
            <Link to="/" className="btn btn--primary">Back to home</Link>
        </div>
    );
}

export default PaymentSuccessPage;
