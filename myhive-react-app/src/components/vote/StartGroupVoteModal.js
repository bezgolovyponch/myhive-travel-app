import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppModal from '../AppModal';
import voteApi from '../../services/voteApi';
import { pushEvent } from '../../utils/analytics';
import { clearTripLead } from '../../utils/tripLead';
import { emailFormat } from '../../utils/validators';
import { getOrCreateVoterToken } from '../../utils/voterToken';
import { useT } from '../../i18n';
import './StartGroupVoteModal.css';

const STEP_DETAILS = 'details';
const STEP_EMAIL = 'email';

// Pure so the validation rules can be reasoned about (and tested) independent
// of component state wiring.
function validate({ needsDates, voteStartDate, voteEndDate }, t) {
    const errors = {};

    if (needsDates) {
        if (!voteStartDate || !voteEndDate) {
            errors.dates = t('start.errors.datesRequired');
        } else if (voteEndDate < voteStartDate) {
            errors.dates = t('start.errors.endBeforeStart');
        }
    }

    return errors;
}

// Two-step modal that turns the current cart into a vote session.
// Step 1 ("details") confirms the trip and asks for dates only when the trip
// setup never captured them (vote_sessions requires them). Step 2 ("email")
// is the organizer email screen: the session is created only once a valid
// address is typed, so the invite link is never shown without one.
function StartGroupVoteModal({
    isOpen, onClose, destinationId, activityIds, numberOfTravelers, startDate, endDate,
    voteMode = 'CART', quizResponses = null, budget = null, onLaunched,
}) {
    const t = useT('voteComponents');
    const navigate = useNavigate();
    const [step, setStep] = useState(STEP_DETAILS);
    const [voteStartDate, setVoteStartDate] = useState(startDate || '');
    const [voteEndDate, setVoteEndDate] = useState(endDate || '');
    const [email, setEmail] = useState('');
    const [errors, setErrors] = useState({});
    const [apiError, setApiError] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const launchedRef = useRef(false);
    const emailInputRef = useRef(null);

    const needsDates = !startDate || !endDate;
    const isEmailStep = step === STEP_EMAIL;

    // The modal stays mounted between openings (TripBuilder renders it with
    // isOpen), so every open restarts at step 1 with no stale error: otherwise
    // a reopen would land straight on the email screen, skipping the explainer
    // and the per-open organizer_voted/email_screen_view pair. Typed values
    // (dates, email) deliberately survive as a draft, as in TripSetupModal.
    useEffect(() => {
        if (isOpen) {
            setStep(STEP_DETAILS);
            setErrors({});
            setApiError(null);
        }
    }, [isOpen]);

    // useModalA11y focuses the first focusable element only when the modal
    // opens; the step change happens later, so the email field focuses itself.
    useEffect(() => {
        if (isEmailStep && emailInputRef.current) {
            emailInputRef.current.focus();
        }
    }, [isEmailStep]);

    const handleClose = () => {
        if (!launchedRef.current) {
            pushEvent('modal_abandoned', {
                modal: 'start_vote', vote_mode: voteMode, has_email: email.trim() !== '', step,
            });
        }
        onClose();
    };

    // Step 1 → step 2: validates the dates, never talks to the server.
    const handleContinue = () => {
        const nextErrors = validate({ needsDates, voteStartDate, voteEndDate }, t);
        setErrors(nextErrors);
        if (Object.keys(nextErrors).length > 0) {
            return;
        }
        pushEvent('organizer_voted', { vote_mode: voteMode, selected_count: activityIds.length });
        setStep(STEP_EMAIL);
        pushEvent('email_screen_view', { vote_mode: voteMode });
    };

    const handleCreate = async () => {
        if (submitting) {
            return;
        }
        const trimmedEmail = email.trim();
        const emailError = emailFormat(trimmedEmail, t('start.email.errors.invalid'));
        if (emailError) {
            setErrors({ email: emailError });
            pushEvent('email_invalid_attempt', {
                vote_mode: voteMode, reason: trimmedEmail === '' ? 'empty' : 'format',
            });
            if (emailInputRef.current) {
                emailInputRef.current.focus();
            }
            return;
        }
        setErrors({});
        setSubmitting(true);
        setApiError(null);
        try {
            const resolvedStart = needsDates ? voteStartDate : startDate;
            const resolvedEnd = needsDates ? voteEndDate : endDate;
            const session = voteMode === 'QUIZ'
                ? await voteApi.createSession({
                    destinationId,
                    initiatorEmail: trimmedEmail,
                    numberOfTravelers,
                    startDate: resolvedStart,
                    endDate: resolvedEnd,
                    budget,
                    voterToken: getOrCreateVoterToken(),
                    quizResponses,
                    activityIds,
                })
                : await voteApi.createCartSession({
                    destinationId,
                    initiatorEmail: trimmedEmail,
                    numberOfTravelers,
                    startDate: resolvedStart,
                    endDate: resolvedEnd,
                    activityIds,
                });
            localStorage.setItem(`myhive-initiator-${session.shareToken}`, 'true');
            if (session.managerToken) {
                localStorage.setItem(`myhive-manager-${session.shareToken}`, session.managerToken);
            }
            if (voteMode === 'CART') {
                // QUIZ parity: quiz sessions intentionally do not set this key.
                localStorage.setItem('myhive-trip-vote-session', session.shareToken);
            }
            clearTripLead();
            pushEvent('contact_captured', {
                trip_id: session.shareToken, vote_mode: voteMode, source: 'vote_email_screen',
            });
            // Mirrors CuratePage's A12 vote_launched (QUIZ) — same field names,
            // shareToken as trip_id, organizer is always the creator here.
            pushEvent('vote_launched', {
                trip_id: session.shareToken,
                user_role: 'organizer',
                selected_count: activityIds.length,
            });
            // The waiting page (the invite link) is the only next screen, and the
            // address is stored server-side by now — this is the reveal.
            pushEvent('link_revealed', { trip_id: session.shareToken, vote_mode: voteMode });
            launchedRef.current = true;
            if (onLaunched) onLaunched();
            if (voteMode === 'QUIZ') {
                navigate(`/vote/${session.shareToken}/waiting`, { state: { managerToken: session.managerToken } });
            } else {
                navigate(`/vote/${session.shareToken}/waiting`);
            }
        } catch (e) {
            setApiError(e.message || t('start.errors.createFailed'));
            setSubmitting(false);
        }
    };

    const footer = isEmailStep ? (
        <div className="start-vote-email-footer">
            <button
                type="button"
                className="btn btn--primary btn--full-width"
                onClick={handleCreate}
                disabled={submitting}
            >
                {submitting ? t('start.creating') : t('start.email.submit')}
            </button>
            {apiError && <p className="error-message">{apiError}</p>}
        </div>
    ) : (
        <button
            type="button"
            className="btn btn--primary btn--full-width"
            onClick={handleContinue}
        >
            {t('start.create')}
        </button>
    );

    return (
        <AppModal
            isOpen={isOpen}
            onClose={handleClose}
            closeOnBackdrop
            title={isEmailStep ? t('start.email.title') : t('start.title')}
            contentClassName={isEmailStep ? 'start-vote-modal start-vote-modal--email' : 'start-vote-modal'}
            footer={footer}
        >
            {isEmailStep ? (
                <>
                    <p className="start-vote-email-sub">{t('start.email.sub')}</p>
                    <input
                        ref={emailInputRef}
                        id="start-vote-email"
                        className={`start-vote-email-input${errors.email ? ' error' : ''}`}
                        type="email"
                        inputMode="email"
                        autoComplete="email"
                        autoCapitalize="none"
                        spellCheck={false}
                        aria-label={t('start.email.label')}
                        aria-invalid={Boolean(errors.email)}
                        aria-describedby={errors.email ? 'start-vote-email-error' : undefined}
                        placeholder={t('start.email.placeholder')}
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        onKeyDown={(e) => {
                            if (e.key === 'Enter') {
                                handleCreate();
                            }
                        }}
                    />
                    {errors.email && (
                        <span id="start-vote-email-error" className="error-message" role="alert">{errors.email}</span>
                    )}
                    <p className="start-vote-email-helper">{t('start.email.helper')}</p>
                </>
            ) : (
                <>
                    <p className="start-vote-modal-sub">
                        {t('start.sub')}
                    </p>
                    {needsDates && (
                        <>
                            <label htmlFor="start-vote-start-date">{t('start.tripDates')}</label>
                            <div className="start-vote-modal-dates">
                                <input
                                    id="start-vote-start-date"
                                    aria-label={t('start.startDate')}
                                    type="date"
                                    value={voteStartDate}
                                    onChange={(e) => setVoteStartDate(e.target.value)}
                                />
                                <input
                                    id="start-vote-end-date"
                                    aria-label={t('start.endDate')}
                                    type="date"
                                    value={voteEndDate}
                                    onChange={(e) => setVoteEndDate(e.target.value)}
                                />
                            </div>
                            {errors.dates && <span className="error-message">{errors.dates}</span>}
                        </>
                    )}
                </>
            )}
        </AppModal>
    );
}

export default StartGroupVoteModal;
