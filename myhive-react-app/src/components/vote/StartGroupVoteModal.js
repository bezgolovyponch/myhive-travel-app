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

const BENEFIT_KEYS = ['seeWhoVoted', 'followLive', 'editActivities'];

function CheckIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.25"
             strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M5 12.5l4.5 4.5L19 7.5"/>
        </svg>
    );
}

function MailIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
             strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <rect x="3" y="5.5" width="18" height="13" rx="2"/>
            <path d="M3.5 7l8.5 6 8.5-6"/>
        </svg>
    );
}

// Single-screen modal that turns the current cart into a vote session: what
// the vote is (destination, size, 24 h deadline), what the organizer gets, and
// the one field we need — the address the results go to. The session is
// created only once a valid address is typed, so the invite link is never
// shown without one. Dates are asked for only when the trip setup never
// captured them (vote_sessions requires them).
function StartGroupVoteModal({
    isOpen, onClose, destinationId, destinationName, activityIds, numberOfTravelers, startDate, endDate,
    voteMode = 'CART', quizResponses = null, budget = null, onLaunched,
}) {
    const t = useT('voteComponents');
    const navigate = useNavigate();
    const [voteStartDate, setVoteStartDate] = useState(startDate || '');
    const [voteEndDate, setVoteEndDate] = useState(endDate || '');
    const [email, setEmail] = useState('');
    const [errors, setErrors] = useState({});
    const [apiError, setApiError] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const launchedRef = useRef(false);
    const emailInputRef = useRef(null);

    const needsDates = !startDate || !endDate;

    // The modal stays mounted between openings (TripBuilder renders it with
    // isOpen), so every open drops stale errors and counts one more view of
    // the email screen (the funnel reads link_revealed / email_screen_view).
    // Typed values (dates, email) deliberately survive as a draft, as in
    // TripSetupModal.
    useEffect(() => {
        if (isOpen) {
            setErrors({});
            setApiError(null);
            pushEvent('email_screen_view', { vote_mode: voteMode });
        }
    }, [isOpen, voteMode]);

    const handleClose = () => {
        if (!launchedRef.current) {
            pushEvent('modal_abandoned', {
                modal: 'start_vote', vote_mode: voteMode, has_email: email.trim() !== '',
            });
        }
        onClose();
    };

    const handleCreate = async () => {
        if (submitting) {
            return;
        }
        const trimmedEmail = email.trim();
        const nextErrors = validate({ needsDates, voteStartDate, voteEndDate }, t);
        const emailError = emailFormat(trimmedEmail, t('start.email.errors.invalid'));
        if (emailError) {
            nextErrors.email = emailError;
            pushEvent('email_invalid_attempt', {
                vote_mode: voteMode, reason: trimmedEmail === '' ? 'empty' : 'format',
            });
        }
        setErrors(nextErrors);
        if (Object.keys(nextErrors).length > 0) {
            if (emailError && emailInputRef.current) {
                emailInputRef.current.focus();
            }
            return;
        }
        // The organizer confirmed the shortlist: everything below is the launch.
        pushEvent('organizer_voted', { vote_mode: voteMode, selected_count: activityIds.length });
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

    const activityCount = activityIds.length;
    const summary = [
        t(activityCount === 1 ? 'start.summary.activitiesOne' : 'start.summary.activitiesOther', { count: activityCount }),
        t(numberOfTravelers === 1 ? 'start.summary.peopleOne' : 'start.summary.peopleOther', { count: numberOfTravelers }),
        t('start.summary.closes'),
    ].join(' · ');

    const footer = (
        <div className="start-vote-footer">
            <button
                type="button"
                className="btn btn--primary btn--full-width"
                onClick={handleCreate}
                disabled={submitting}
            >
                {submitting ? t('start.creating') : t('start.create')}
            </button>
            {apiError && <p className="error-message" role="alert">{apiError}</p>}
            <p className="start-vote-note">{t('start.noSpam')}</p>
        </div>
    );

    return (
        <AppModal
            isOpen={isOpen}
            onClose={handleClose}
            closeOnBackdrop
            title={destinationName ? t('start.title', { destination: destinationName }) : t('start.titleGeneric')}
            contentClassName="start-vote-modal"
            footer={footer}
        >
            <p className="start-vote-summary">{summary}</p>
            <ul className="start-vote-benefits">
                {BENEFIT_KEYS.map((key) => (
                    <li key={key}>
                        <CheckIcon/>
                        <span>{t(`start.benefits.${key}`)}</span>
                    </li>
                ))}
            </ul>
            {needsDates && (
                <div className="start-vote-dates">
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
                    {errors.dates && <span className="error-message" role="alert">{errors.dates}</span>}
                </div>
            )}
            <hr className="start-vote-divider"/>
            <label className="start-vote-email-label" htmlFor="start-vote-email">{t('start.email.label')}</label>
            <div className={`start-vote-email-field${errors.email ? ' error' : ''}`}>
                <MailIcon/>
                <input
                    ref={emailInputRef}
                    id="start-vote-email"
                    className="start-vote-email-input"
                    type="email"
                    inputMode="email"
                    autoComplete="email"
                    autoCapitalize="none"
                    spellCheck={false}
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
            </div>
            {errors.email && (
                <span id="start-vote-email-error" className="error-message" role="alert">{errors.email}</span>
            )}
        </AppModal>
    );
}

export default StartGroupVoteModal;
