import {useState} from 'react';
import ContactForm from './ContactForm';
import AppModal from './AppModal';
import {paymentApi} from '../services/paymentApi';

function PaymentActions({voteShareToken, managerToken, tripData, initialValues, makeBookingPayload}) {
    const [mode, setMode] = useState(null);
    const [showForm, setShowForm] = useState(false);
    const [showConsultDone, setShowConsultDone] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState(null);

    const open = (nextMode) => {
        setMode(nextMode);
        setSubmitError(null);
        setShowForm(true);
    };

    const handleSubmit = async (contactData) => {
        setIsSubmitting(true);
        setSubmitError(null);
        try {
            const payload = makeBookingPayload(contactData);
            if (mode === 'deposit') {
                const {bookingId, checkoutUrl} = await paymentApi.createDepositSession(voteShareToken, managerToken, payload);
                // Remember which booking belongs to this vote. Consumed by the balance-collection step
                // (which ships on the follow-up branch); harmless no-op on the prepayment-only flow.
                if (bookingId) {
                    localStorage.setItem(`myhive-booking-${voteShareToken}`, bookingId);
                }
                window.location.assign(checkoutUrl);
                return;
            }
            await paymentApi.createConsultationLead(voteShareToken, managerToken, payload);
            setShowForm(false);
            setShowConsultDone(true);
        } catch (e) {
            setSubmitError(e.message || 'Something went wrong. Please try again.');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="payment-actions">
            <button
                type="button"
                className="btn btn--primary payment-actions__deposit"
                onClick={() => open('deposit')}
            >
                Book &amp; pay 30% prepayment
            </button>
            <button
                type="button"
                className="btn btn--secondary payment-actions__consult"
                onClick={() => open('consult')}
            >
                Contact our consultant
            </button>

            <ContactForm
                isOpen={showForm}
                onClose={() => setShowForm(false)}
                onSubmit={handleSubmit}
                tripData={tripData}
                initialValues={initialValues}
                isSubmitting={isSubmitting}
                submitError={submitError}
            />

            <AppModal
                isOpen={showConsultDone}
                onClose={() => setShowConsultDone(false)}
                title="Request received"
                footer={
                    <button className="btn btn--primary" onClick={() => setShowConsultDone(false)}>
                        Got it
                    </button>
                }
            >
                <p>Thanks! Our consultant will contact you shortly to finalize your trip and arrange payment.</p>
            </AppModal>
        </div>
    );
}

export default PaymentActions;
