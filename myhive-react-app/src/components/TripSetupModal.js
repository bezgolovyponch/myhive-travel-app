import {useEffect, useId, useRef, useState} from 'react';
import {useCatalog} from '../context/CatalogContext';
import {useTrip} from '../context/TripContext';
import AppModal from './AppModal';
import {getDefaultDestination} from '../utils/defaultDestination';
import {pushEvent} from '../utils/analytics';
import {getRef} from '../utils/attribution';
import {clearSetupDraft, readSetupDraft, writeSetupDraft} from '../utils/setupDraft';
import {generateUuid} from '../utils/uuid';
import './ContactForm.css';
import DateRangePicker from './DateRangePicker';

function todayLocalIso() {
    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${now.getFullYear()}-${month}-${day}`;
}

function TripSetupModal({ isVoteMode = false, voteOpen = false, onVoteConfirm, onVoteCancel, preselectedDestination = null }) {
    const {state: catalog} = useCatalog();
    const {state, dispatch} = useTrip();
    const [travelers, setTravelers] = useState('1');
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
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
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isOpen]);

    // A8: fire tb_start once per open transition in vote mode.
    const tbStartFiredRef = useRef(false);
    useEffect(() => {
        if (isOpen && isVoteMode && !tbStartFiredRef.current) {
            tbStartFiredRef.current = true;
            pushEvent('tb_start', {ref: getRef()});
        }
        if (!isOpen) {
            tbStartFiredRef.current = false;
        }
    }, [isOpen, isVoteMode]);

    const handleCancel = () => {
        // Save whatever the user already entered — a dismissed modal never
        // costs them re-typing travelers/dates on the next open.
        writeSetupDraft({travelers: parseInt(travelers, 10) || 1, startDate, endDate});
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
        if (isVoteMode) {
            if (!voteFormValid) return;
            // A9: tb_group_submitted — vote mode (no trip_id; no email collected here).
            pushEvent('tb_group_submitted', {
                destination: destination ? destination.slug : undefined,
                group_size: travelersNum,
                has_budget: false,
            });
            clearSetupDraft();
            // budget stays in the payload as null so session seeding keeps working unchanged.
            onVoteConfirm({ travelers: travelersNum, startDate, endDate, destination, budget: null });
        } else {
            // A9: tb_group_submitted — trip/direct mode (mint trip_id; no email collected).
            const tripId = generateUuid();
            dispatch({type: 'SET_TRIP_ID', tripId});
            pushEvent('tb_group_submitted', {
                destination: destination ? destination.slug : undefined,
                group_size: travelersNum,
                has_budget: false,
                trip_id: tripId,
            });
            clearSetupDraft();
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
            title="Set Up Your Trip"
            footer={
                <>
                    <button className="btn btn--ghost" onClick={handleCancel}>Cancel</button>
                    {/* type=submit + form attr: routes the click through the form so
                        native constraint validation runs. */}
                    <button
                        type="submit"
                        form={formId}
                        className="btn btn--primary"
                        disabled={isVoteMode && !voteFormValid}
                    >
                        {isVoteMode ? 'Continue to Categories' : 'Confirm'}
                    </button>
                </>
            }
        >
                    <form id={formId} className="contact-form" onSubmit={handleSubmit}>
                        <div className="form-group">
                            <label htmlFor="tripTravelers">Number of Travelers *</label>
                            {/* Stepper: − / editable number / +. No upper cap — big groups
                                are typed directly. */}
                            <div className="travelers-control">
                                <button
                                    type="button"
                                    className="travelers-step"
                                    aria-label="Decrease travelers"
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
                                    aria-label="Increase travelers"
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
                    </form>
        </AppModal>
    );
}

export default TripSetupModal;
