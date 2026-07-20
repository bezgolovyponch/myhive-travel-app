import {useState} from 'react';
import AppModal from './AppModal';
import {WHATSAPP_URL, PAYMENTS_ENABLED} from '../services/config';
import {paymentApi} from '../services/paymentApi';
import {useTurnstileWidget} from '../hooks/useTurnstileWidget';

function SuccessModal({isOpen, onClose, userName, userEmail, bookingId}) {
    // The 30% deposit is a real charge, so it is Turnstile-gated; the widget only renders
    // when this success screen belongs to a booking that can still be paid (bookingId set)
    // and online payment is enabled.
    const {token: turnstileToken, containerRef} = useTurnstileWidget(PAYMENTS_ENABLED && isOpen && !!bookingId);
    const [isRedirecting, setIsRedirecting] = useState(false);
    const [depositError, setDepositError] = useState(null);

    const handleDepositClick = async () => {
        setIsRedirecting(true);
        setDepositError(null);
        try {
            const {checkoutUrl} = await paymentApi.createBookingDepositSession(bookingId, turnstileToken);
            // Hand off to Stripe Checkout; keep isRedirecting true through the redirect.
            window.location.assign(checkoutUrl);
        } catch (error) {
            setDepositError(error.message || 'Failed to start payment. Please try again.');
            setIsRedirecting(false);
        }
    };

    return (
        <AppModal
            isOpen={isOpen}
            onClose={onClose}
            title="Booking Submitted Successfully!"
            footer={
                <button className="btn btn--primary" onClick={onClose}>
                    Got it!
                </button>
            }
        >
            <div className="success-message">
                <h4>Thank you, {userName}!</h4>
                <p>Your travel booking has been submitted successfully.</p>
                <p>We will contact you soon at <strong>{userEmail}</strong> to confirm the details.</p>
            </div>

            <div className="next-steps">
                <h5>What happens next?</h5>
                <ul>
                    <li>Our team will review your booking request</li>
                    <li>We'll contact you via email to confirm details</li>
                    <li>We'll provide personalized recommendations</li>
                    <li>We'll finalize your travel itinerary</li>
                </ul>
            </div>

            {PAYMENTS_ENABLED && bookingId && (
                <div className="success-deposit">
                    <div ref={containerRef} className="turnstile-widget"/>
                    <h5>Want to lock your trip in right away?</h5>
                    <button
                        type="button"
                        className="btn btn--primary success-deposit-btn"
                        onClick={handleDepositClick}
                        disabled={!turnstileToken || isRedirecting}
                    >
                        {isRedirecting ? 'Redirecting…' : 'Pay 30% deposit now'}
                    </button>
                    {depositError && <div className="form-error">{depositError}</div>}
                </div>
            )}

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
        </AppModal>
    );
}

export default SuccessModal;
