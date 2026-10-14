import {useEffect, useId, useRef, useState} from 'react';
import {useCatalog} from '../context/CatalogContext';
import {useTrip} from '../context/TripContext';
import AppModal from './AppModal';
import {getDefaultDestination} from '../utils/defaultDestination';
import {pushEvent} from '../utils/analytics';
import {getRef} from '../utils/attribution';
import {clearSetupDraft, readSetupDraft, writeSetupDraft} from '../utils/setupDraft';
import {generateUuid} from '../utils/uuid';
import {useT} from '../i18n';
import './ContactForm.css';
import DateRangePicker from './DateRangePicker';
import {PER_PERSON, TOTAL, budgetAsEntered, budgetBasisOf, totalFor} from '../utils/tripBudget';

function todayLocalIso() {
    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${now.getFullYear()}-${month}-${day}`;
}

// The vote-mode callbacks default to null: in trip mode (Header, and the vote
// landing's first-add modal) the component is rendered prop-less, and only the
// isVoteMode branches ever call them.
function TripSetupModal({ isVoteMode = false, voteOpen = false, onVoteConfirm = null, onVoteCancel = null, preselectedDestination = null }) {
    const {state: catalog} = useCatalog();
    const {state, dispatch} = useTrip();
    const t = useT('tripSetup');
    const [travelers, setTravelers] = useState('1');
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    // Optional: what the group wants to spend, per person or for the whole group. Plans are fitted to it.
    const [budget, setBudget] = useState('');
    const [budgetBasis, setBudgetBasis] = useState(PER_PERSON);
    // Unique per instance: this modal is mounted in several places (Header,
    // TripBuilderDropdown, vote flow) and a duplicate id would make the footer
    // submit button target the wrong form.
    const formId = useId();

    const isOpen = isVoteMode ? voteOpen : state.tripSetupModalOpen;

    useEffect(() => {
        if (isOpen) {
            // Seed from a saved draft first (the user dismissed the modal without
            // submitting), then trip state (set when the first activity was added
            // to the cart) — don't ask twice either way. Past dates are dropped
            // either way: they must not flow into a new booking or vote session.
            const draft = readSetupDraft();
            const draftDatesCurrent = Boolean(draft?.startDate) && draft.startDate >= todayLocalIso();
            const datesAreCurrent = Boolean(state.tripStartDate) && state.tripStartDate >= todayLocalIso();
            setTravelers(String(draft?.travelers || state.tripTravelers || 1));
            setStartDate(draftDatesCurrent ? draft.startDate : (datesAreCurrent ? state.tripStartDate : ''));
            setEndDate(draftDatesCurrent ? draft.endDate : (datesAreCurrent ? state.tripEndDate : ''));
            const savedBudget = draft?.budget ?? budgetAsEntered(state);
            setBudget(savedBudget == null ? '' : String(savedBudget));
            setBudgetBasis(draft?.budgetBasis ?? (state.tripBudget != null ? budgetBasisOf(state) : PER_PERSON));
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isOpen]);

    // A8: fire tb_start once per open transition. ТЗ §8 defines the trigger as
    // opening the Trip Builder's first screen — this modal — however the user got
    // there, so direct entry counts too. vote_mode separates the two paths instead
    // of one of them being silent: previously every organizer who did not arrive
    // through a vote link was missing from the top of the funnel entirely.
    const tbStartFiredRef = useRef(false);
    useEffect(() => {
        if (isOpen && !tbStartFiredRef.current) {
            tbStartFiredRef.current = true;
            pushEvent('tb_start', {ref: getRef(), vote_mode: isVoteMode ? 'VOTE' : 'TRIP'});
        }
        if (!isOpen) {
            tbStartFiredRef.current = false;
        }
    }, [isOpen, isVoteMode]);

    const handleCancel = () => {
        // Save whatever the user already entered — a dismissed modal never
        // costs them re-typing travelers/dates on the next open.
        writeSetupDraft({travelers: parseInt(travelers, 10) || 1, startDate, endDate, budget, budgetBasis});
        pushEvent('modal_abandoned', {
            modal: 'trip_setup',
            // String, not boolean: the shared modal_abandoned.vote_mode param
            // also carries StartGroupVoteModal's 'CART'|'QUIZ' values, so one
            // type with four values {TRIP, VOTE, CART, QUIZ} keeps it sane.
            vote_mode: isVoteMode ? 'VOTE' : 'TRIP',
            has_travelers: (parseInt(travelers, 10) || 1) > 1,
            has_dates: Boolean(startDate && endDate),
        });
        if (isVoteMode) {
            onVoteCancel();
        } else {
            dispatch({type: 'CANCEL_TRIP_SETUP'});
        }
    };

    if (!isOpen) return null;

    // The destination is never asked here — it resolves to the preselected or
    // default one and is only surfaced later, on the booking page.
    const destination = preselectedDestination || getDefaultDestination(catalog.destinations);

    const voteFormValid = startDate && endDate && destination;

    const handleConfirm = () => {
        const travelersNum = Math.max(1, parseInt(travelers, 10) || 1);
        const budgetTotal = totalFor(budget, budgetBasis, travelersNum);
        const hasBudget = budgetTotal != null;
        if (isVoteMode) {
            if (!voteFormValid) return;
            // A9: tb_group_submitted — vote mode (no trip_id; no email collected here).
            pushEvent('tb_group_submitted', {
                destination: destination ? destination.slug : undefined,
                group_size: travelersNum,
                has_budget: hasBudget,
            });
            clearSetupDraft();
            // The vote session takes the group total; the basis rides along so the plan can say it back.
            onVoteConfirm({ travelers: travelersNum, startDate, endDate, destination, budget: budgetTotal,
                budgetBasis: hasBudget ? budgetBasis : null });
        } else {
            // A9: tb_group_submitted — trip/direct mode (mint trip_id; no email collected).
            const tripId = generateUuid();
            dispatch({type: 'SET_TRIP_ID', tripId});
            pushEvent('tb_group_submitted', {
                destination: destination ? destination.slug : undefined,
                group_size: travelersNum,
                has_budget: hasBudget,
                trip_id: tripId,
            });
            clearSetupDraft();
            dispatch({type: 'UPDATE_TRIP_BUDGET', budget: budgetTotal, basis: hasBudget ? budgetBasis : null});
            dispatch({
                type: 'SET_TRIP_SETUP',
                travelers: travelersNum,
                startDate: startDate,
                endDate: endDate
            });
        }
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        handleConfirm();
    };

    return (
        <AppModal
            isOpen
            onClose={handleCancel}
            closeOnBackdrop
            title={t('title')}
            footer={
                <>
                    <button className="btn btn--ghost" onClick={handleCancel}>{t('cancel')}</button>
                    {/* type=submit + form attr: routes the click through the form so
                        native constraint validation runs. */}
                    <button
                        type="submit"
                        form={formId}
                        className="btn btn--primary"
                        disabled={isVoteMode && !voteFormValid}
                    >
                        {isVoteMode ? t('continue') : t('confirm')}
                    </button>
                </>
            }
        >
                    <form id={formId} className="contact-form" onSubmit={handleSubmit}>
                        <div className="form-group">
                            <label htmlFor="tripTravelers">{t('travelersLabel')}</label>
                            {/* Stepper: − / editable number / +. No upper cap — big groups
                                are typed directly. */}
                            <div className="travelers-control">
                                <button
                                    type="button"
                                    className="travelers-step"
                                    aria-label={t('decreaseAria')}
                                    disabled={(parseInt(travelers, 10) || 1) <= 1}
                                    onClick={() => setTravelers(String(Math.max(1, (parseInt(travelers, 10) || 1) - 1)))}
                                >−</button>
                                <input
                                    type="number"
                                    id="tripTravelers"
                                    className="travelers-count"
                                    min="1"
                                    value={travelers}
                                    onChange={e => setTravelers(e.target.value)}
                                    onBlur={e => setTravelers(String(Math.max(1, parseInt(e.target.value, 10) || 1)))}
                                />
                                <button
                                    type="button"
                                    className="travelers-step"
                                    aria-label={t('increaseAria')}
                                    onClick={() => setTravelers(String(Math.max(1, (parseInt(travelers, 10) || 1) + 1)))}
                                >+</button>
                            </div>
                        </div>
                        <DateRangePicker
                            from={startDate}
                            to={endDate}
                            onChange={(from, to) => {
                                setStartDate(from);
                                setEndDate(to);
                            }}
                            popover
                        />
                        <div className="form-group">
                            <label htmlFor={`${formId}-budget`}>{t('budgetLabel')}</label>
                            <div className="trip-budget-control">
                                <input
                                    type="number"
                                    id={`${formId}-budget`}
                                    className="trip-budget-amount"
                                    min="1"
                                    step="1"
                                    inputMode="numeric"
                                    placeholder={t('budgetPlaceholder')}
                                    value={budget}
                                    onChange={e => setBudget(e.target.value)}
                                />
                                <div className="trip-budget-basis" role="radiogroup" aria-label={t('budgetBasisAria')}>
                                    {[PER_PERSON, TOTAL].map(basis => (
                                        <button
                                            key={basis}
                                            type="button"
                                            role="radio"
                                            aria-checked={budgetBasis === basis}
                                            className={`trip-budget-basis-option${budgetBasis === basis ? ' is-active' : ''}`}
                                            onClick={() => setBudgetBasis(basis)}
                                        >
                                            {t(basis === PER_PERSON ? 'budgetPerPerson' : 'budgetTotal')}
                                        </button>
                                    ))}
                                </div>
                            </div>
                            <p className="trip-budget-hint">{t('budgetHint')}</p>
                        </div>
                    </form>
        </AppModal>
    );
}

export default TripSetupModal;
