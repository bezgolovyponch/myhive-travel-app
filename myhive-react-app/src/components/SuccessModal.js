import {useState} from 'react';
import AppModal from './AppModal';
import {WHATSAPP_URL, PAYMENTS_ENABLED} from '../services/config';
import {paymentApi} from '../services/paymentApi';
import {useTurnstileWidget} from '../hooks/useTurnstileWidget';
import {pushEvent, navigateAfterEvents} from '../utils/analytics';
import {useT} from '../i18n';

function SuccessModal({isOpen, onClose, userName, userEmail, bookingId, tripId, userRole}) {
    const t = useT('contact');
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
            // payment_page_viewed (ТЗ §8) — see PaymentActions for the reasoning and for why
            // share_value is absent. Pushed only once the session exists: a failed call means
            // the payment page was never reached.
            pushEvent('payment_page_viewed', {
                trip_id: tripId,
                user_role: userRole,
                currency: 'EUR',
            });
            // Hand off to Stripe Checkout; keep isRedirecting true through the redirect.
            // navigateAfterEvents, not a bare assign: the push above must not die with the
            // document before the container dispatches it.
            navigateAfterEvents(checkoutUrl);
        } catch (error) {
            setDepositError(error.message || t('bookingSuccess.paymentError'));
            setIsRedirecting(false);
        }
    };

    return (
        <AppModal
            isOpen={isOpen}
            onClose={onClose}
            title={t('bookingSuccess.title')}
            footer={
                <button className="btn btn--primary" onClick={onClose}>
                    {t('bookingSuccess.gotIt')}
                </button>
            }
        >
            <div className="success-message">
                <h4>{t('bookingSuccess.thankYou', {name: userName})}</h4>
                <p>{t('bookingSuccess.submitted')}</p>
                <p>{t('bookingSuccess.contactTextBefore')} <strong>{userEmail}</strong> {t('bookingSuccess.contactTextAfter')}</p>
            </div>

            <div className="next-steps">
                <h5>{t('bookingSuccess.nextStepsTitle')}</h5>
                <ul>
                    <li>{t('bookingSuccess.step1')}</li>
                    <li>{t('bookingSuccess.step2')}</li>
                    <li>{t('bookingSuccess.step3')}</li>
                    <li>{t('bookingSuccess.step4')}</li>
                </ul>
            </div>

            {PAYMENTS_ENABLED && bookingId && (
                <div className="success-deposit">
                    <div ref={containerRef} className="turnstile-widget"/>
                    <h5>{t('bookingSuccess.depositTitle')}</h5>
                    <button
                        type="button"
                        className="btn btn--primary success-deposit-btn"
                        onClick={handleDepositClick}
                        disabled={!turnstileToken || isRedirecting}
                    >
                        {isRedirecting ? t('bookingSuccess.redirecting') : t('bookingSuccess.payDeposit')}
                    </button>
                    {depositError && <div className="form-error">{depositError}</div>}
                </div>
            )}

            <div className="success-whatsapp">
                <h5>{t('bookingSuccess.whatsappTitle')}</h5>
                <a
                    className="success-whatsapp-link"
                    href={WHATSAPP_URL}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label={t('bookingSuccess.whatsappAria')}
                >
                    <i className="ph ph-whatsapp-logo" aria-hidden="true"/> {t('bookingSuccess.whatsappUs')}
                </a>
            </div>
        </AppModal>
    );
}

export default SuccessModal;
