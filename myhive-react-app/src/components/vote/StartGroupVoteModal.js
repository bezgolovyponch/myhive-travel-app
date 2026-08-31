import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppModal from '../AppModal';
import voteApi from '../../services/voteApi';
import { pushEvent } from '../../utils/analytics';
import { clearTripLead } from '../../utils/tripLead';
import { getOrCreateVoterToken } from '../../utils/voterToken';
import { funnelParams } from '../../utils/funnel';
import { getAttribution } from '../../utils/attribution';
import { getCookie } from '../../utils/cookies';
import { useT } from '../../i18n';
import './StartGroupVoteModal.css';

// Pure so the validation rules can be reasoned about (and tested) independent
// of component state wiring. Email is intentionally not asked here — it is
// collected on the booking page instead.
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

// One-field mini-modal that turns the current cart into a CART vote session.
// Date inputs appear only when the trip setup never captured dates (the
// vote_sessions table requires them).
function StartGroupVoteModal({
    isOpen, onClose, destinationId, activityIds, numberOfTravelers, startDate, endDate,
    voteMode = 'CART', quizResponses = null, budget = null, onLaunched,
}) {
    const t = useT('voteComponents');
    const navigate = useNavigate();
    const [voteStartDate, setVoteStartDate] = useState(startDate || '');
    const [voteEndDate, setVoteEndDate] = useState(endDate || '');
    const [groomName, setGroomName] = useState('');
    const [errors, setErrors] = useState({});
    const [apiError, setApiError] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const launchedRef = useRef(false);

    const needsDates = !startDate || !endDate;

    const handleClose = () => {
        if (!launchedRef.current) {
            // has_email stays in the payload for taxonomy stability; always false
            // now that the modal no longer collects an email.
            pushEvent('modal_abandoned', {
                modal: 'start_vote', vote_mode: voteMode, has_email: false,
            });
        }
        onClose();
    };

    const handleCreate = async () => {
        if (submitting) {
            return;
        }

        const nextErrors = validate({ needsDates, voteStartDate, voteEndDate }, t);
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
                    numberOfTravelers,
                    startDate: resolvedStart,
                    endDate: resolvedEnd,
                    budget,
                    voterToken: getOrCreateVoterToken(),
                    quizResponses,
                    activityIds,
                    fbp: getCookie('_fbp'),
                    fbc: getCookie('_fbc'),
                    fbclid: getAttribution().fbclid,
                    groomName: groomName.trim() || undefined,
                })
                : await voteApi.createCartSession({
                    destinationId,
                    numberOfTravelers,
                    startDate: resolvedStart,
                    endDate: resolvedEnd,
                    activityIds,
                    fbp: getCookie('_fbp'),
                    fbc: getCookie('_fbc'),
                    fbclid: getAttribution().fbclid,
                    groomName: groomName.trim() || undefined,
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
                ...funnelParams({
                    startDate: needsDates ? voteStartDate : startDate,
                    endDate: needsDates ? voteEndDate : endDate,
                    groupSize: numberOfTravelers,
                    activitiesCount: activityIds.length,
                    voteId: session.shareToken,
                }),
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
            setApiError(e.message || t('start.errors.createFailed'));
            setSubmitting(false);
        }
    };

    return (
        <AppModal
            isOpen={isOpen}
            onClose={handleClose}
            closeOnBackdrop
            title={t('start.title')}
            contentClassName="start-vote-modal"
            footer={(
                <button
                    type="button"
                    className="btn btn--primary btn--full-width"
                    onClick={handleCreate}
                    disabled={submitting}
                >
                    {submitting ? t('start.creating') : t('start.create')}
                </button>
            )}
        >
            <p className="start-vote-modal-sub">
                {t('start.sub')}
            </p>
            <label htmlFor="start-vote-groom">Who&apos;s the stag? <span className="optional">(optional)</span></label>
            <input
                id="start-vote-groom"
                type="text"
                maxLength={100}
                placeholder="e.g. Tom"
                value={groomName}
                onChange={(e) => setGroomName(e.target.value)}
            />
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
            {apiError && <p className="error-message">{apiError}</p>}
        </AppModal>
    );
}

export default StartGroupVoteModal;
