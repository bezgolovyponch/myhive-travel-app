import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppModal from '../AppModal';
import EmailConsentNote from '../EmailConsentNote';
import voteApi from '../../services/voteApi';
import { pushEvent } from '../../utils/analytics';
import { clearTripLead } from '../../utils/tripLead';
import { useEmailLeadCapture } from '../../hooks/useEmailLeadCapture';
import { getOrCreateVoterToken } from '../../utils/voterToken';
import './StartGroupVoteModal.css';

const EMAIL_RE = /\S+@\S+\.\S+/;

// Pure so the validation rules can be reasoned about (and tested) independent
// of component state wiring.
function validate({ email, needsDates, voteStartDate, voteEndDate }) {
    const errors = {};

    if (!email.trim()) {
        errors.email = 'Email is required';
    } else if (!EMAIL_RE.test(email)) {
        errors.email = 'Email is invalid';
    }

    if (needsDates) {
        if (!voteStartDate || !voteEndDate) {
            errors.dates = 'Trip dates are required';
        } else if (voteEndDate < voteStartDate) {
            errors.dates = 'End date must be on or after the start date';
        }
    }

    return errors;
}

// One-field mini-modal that turns the current cart into a CART vote session.
// Date inputs appear only when the trip setup never captured dates (the
// vote_sessions table requires them).
function StartGroupVoteModal({
    isOpen, onClose, destinationId, activityIds, numberOfTravelers, startDate, endDate,
    voteMode = 'CART', quizResponses = null, budget = null, onLaunched,
}) {
    const navigate = useNavigate();
    const [email, setEmail] = useState('');
    const [voteStartDate, setVoteStartDate] = useState(startDate || '');
    const [voteEndDate, setVoteEndDate] = useState(endDate || '');
    const [errors, setErrors] = useState({});
    const [apiError, setApiError] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const launchedRef = useRef(false);

    const needsDates = !startDate || !endDate;

    // Mirror handleCreate's date resolution so leads captured while the modal
    // collects its own dates carry whatever the user has typed so far.
    const captureEmail = useEmailLeadCapture({
        destinationId, numberOfTravelers,
        startDate: (needsDates ? voteStartDate : startDate) || null,
        endDate: (needsDates ? voteEndDate : endDate) || null,
        budget,
    });

    const handleEmailChange = (value) => {
        setEmail(value);
        captureEmail(value);
    };

    const handleClose = () => {
        if (!launchedRef.current) {
            pushEvent('modal_abandoned', {
                modal: 'start_vote', vote_mode: voteMode, has_email: EMAIL_RE.test(email.trim()),
            });
        }
        onClose();
    };

    const handleCreate = async () => {
        if (submitting) {
            return;
        }

        const nextErrors = validate({ email, needsDates, voteStartDate, voteEndDate });
        setErrors(nextErrors);
        if (Object.keys(nextErrors).length > 0) {
            return;
        }

        setSubmitting(true);
        setApiError(null);
        try {
            const resolvedStart = needsDates ? voteStartDate : startDate;
            const resolvedEnd = needsDates ? voteEndDate : endDate;
            const session = voteMode === 'QUIZ'
                ? await voteApi.createSession({
                    destinationId,
                    initiatorEmail: email.trim(),
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
                    initiatorEmail: email.trim(),
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
            // Mirrors CuratePage's A12 vote_launched (QUIZ) — same field names,
            // shareToken as trip_id, organizer is always the creator here.
            pushEvent('vote_launched', {
                trip_id: session.shareToken,
                user_role: 'organizer',
                selected_count: activityIds.length,
            });
            launchedRef.current = true;
            if (onLaunched) onLaunched();
            if (voteMode === 'QUIZ') {
                navigate(`/vote/${session.shareToken}/waiting`, { state: { managerToken: session.managerToken } });
            } else {
                navigate(`/vote/${session.shareToken}/waiting`);
            }
        } catch (e) {
            setApiError(e.message || 'Failed to create the vote. Please try again.');
            setSubmitting(false);
        }
    };

    return (
        <AppModal
            isOpen={isOpen}
            onClose={handleClose}
            closeOnBackdrop
            title="Let your mates vote"
            contentClassName="start-vote-modal"
            footer={(
                <button
                    type="button"
                    className="btn btn--primary btn--full-width"
                    onClick={handleCreate}
                    disabled={submitting}
                >
                    {submitting ? 'Creating…' : 'Create vote'}
                </button>
            )}
        >
            <p className="start-vote-modal-sub">
                We&apos;ll send you the live vote results and your saved shortlist.
                Voting closes automatically after 24 hours.
            </p>
            <label htmlFor="start-vote-email">Your email</label>
            <input
                id="start-vote-email"
                type="email"
                value={email}
                onChange={(e) => handleEmailChange(e.target.value)}
                className={errors.email ? 'error' : ''}
            />
            {errors.email && <span className="error-message">{errors.email}</span>}
            <EmailConsentNote />
            {needsDates && (
                <>
                    <label htmlFor="start-vote-start-date">Trip dates</label>
                    <div className="start-vote-modal-dates">
                        <input
                            id="start-vote-start-date"
                            aria-label="Start date"
                            type="date"
                            value={voteStartDate}
                            onChange={(e) => setVoteStartDate(e.target.value)}
                        />
                        <input
                            id="start-vote-end-date"
                            aria-label="End date"
                            type="date"
                            value={voteEndDate}
                            onChange={(e) => setVoteEndDate(e.target.value)}
                        />
                    </div>
                    {errors.dates && <span className="error-message">{errors.dates}</span>}
                </>
            )}
            {apiError && <p className="error-message">{apiError}</p>}
        </AppModal>
    );
}

export default StartGroupVoteModal;
