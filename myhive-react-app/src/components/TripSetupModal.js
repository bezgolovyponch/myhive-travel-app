import {useEffect, useId, useRef, useState} from 'react';
import {useCatalog} from '../context/CatalogContext';
import {useTrip} from '../context/TripContext';
import AppModal from './AppModal';
import {DESTINATION_PICKER_ENABLED} from '../services/config';
import {getDefaultDestination} from '../utils/defaultDestination';
import {pushEvent} from '../utils/analytics';
import {getRef} from '../utils/attribution';
import {hasConsent} from '../utils/consent';
import {generateUuid} from '../utils/uuid';
import './ContactForm.css';
import DateRangePicker from './DateRangePicker';

function TripSetupModal({ isVoteMode = false, voteOpen = false, onVoteConfirm, onVoteCancel, preselectedDestination = null }) {
    const {state: catalog} = useCatalog();
    const {state, dispatch} = useTrip();
    const [travelers, setTravelers] = useState('1');
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [email, setEmail] = useState('');
    const [selectedDestinationId, setSelectedDestinationId] = useState('');
    const [budget, setBudget] = useState('');
    const [budgetError, setBudgetError] = useState('');
    // Unique per instance: this modal is mounted in several places (Header,
    // TripBuilderDropdown, vote flow) and a duplicate id would make the footer
    // submit button target the wrong form.
    const formId = useId();

    const isOpen = isVoteMode ? voteOpen : state.tripSetupModalOpen;

    useEffect(() => {
        if (isOpen) {
            setTravelers('1');
            setStartDate('');
            setEndDate('');
            setEmail('');
            setSelectedDestinationId('');
            setBudget('');
            setBudgetError('');
        }
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
        if (isVoteMode) {
            onVoteCancel();
        } else {
            dispatch({type: 'CANCEL_TRIP_SETUP'});
        }
    };

    if (!isOpen) return null;

    // With the picker disabled (or when the API has a single destination anyway)
    // the default destination is selected automatically and shown as read-only text.
    const canAutoSelect = !DESTINATION_PICKER_ENABLED || catalog.destinations.length === 1;
    const autoDestination = canAutoSelect ? getDefaultDestination(catalog.destinations) : null;
    const effectiveDestination = preselectedDestination || autoDestination;
    const needsDestinationPicker = isVoteMode && !effectiveDestination;
    const destination = effectiveDestination
        || catalog.destinations.find(d => d.id === selectedDestinationId)
        || null;

    const voteFormValid = startDate && endDate && email && destination;

    const handleConfirm = () => {
        const travelersNum = Math.max(1, parseInt(travelers, 10) || 1);
        if (isVoteMode) {
            if (!voteFormValid) return;
            const budgetValue = budget.trim() === '' ? null : Number(budget);
            if (budgetValue !== null && (!Number.isFinite(budgetValue) || budgetValue <= 0)) {
                setBudgetError('Budget must be a positive number.');
                return;
            }
            setBudgetError('');
            // A9: tb_group_submitted — vote mode (no trip_id; email gated by ad_storage consent).
            const voteEventParams = {
                destination: destination ? destination.slug : undefined,
                group_size: travelersNum,
                has_budget: budget.trim() !== '',
            };
            if (email && hasConsent('ad_storage')) {
                voteEventParams.email = email;
            }
            pushEvent('tb_group_submitted', voteEventParams);
            onVoteConfirm({ travelers: travelersNum, startDate, endDate, email, destination, budget: budgetValue });
        } else {
            // A9: tb_group_submitted — trip/direct mode (mint trip_id; no email collected).
            const tripId = generateUuid();
            dispatch({type: 'SET_TRIP_ID', tripId});
            pushEvent('tb_group_submitted', {
                destination: destination ? destination.slug : undefined,
                group_size: travelersNum,
                has_budget: budget.trim() !== '',
                trip_id: tripId,
            });
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
            title="Set Up Your Trip"
            footer={
                <>
                    <button className="btn btn--secondary" onClick={handleCancel}>Cancel</button>
                    {/* type=submit + form attr: routes the click through the form so
                        native constraint validation (email format, budget min) runs. */}
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
                    <p className="trip-setup-description">
                        Tell us about your group so we can calculate the right price.
                    </p>
                    <form id={formId} className="contact-form" onSubmit={handleSubmit}>
                        {needsDestinationPicker && (
                            <div className="form-group">
                                <label htmlFor="voteDestination">Destination *</label>
                                <select
                                    id="voteDestination"
                                    value={selectedDestinationId}
                                    onChange={e => setSelectedDestinationId(e.target.value)}
                                    disabled={catalog.loading}
                                    required
                                >
                                    <option value="">{catalog.loading ? 'Loading destinations…' : 'Select a destination…'}</option>
                                    {!catalog.loading && catalog.destinations.map(d => (
                                        <option key={d.id} value={d.id}>{d.name}</option>
                                    ))}
                                </select>
                                {catalog.error && !catalog.loading && (
                                    <p className="text-error">Couldn't load destinations. Please try again later.</p>
                                )}
                            </div>
                        )}
                        {isVoteMode && effectiveDestination && (
                            <div className="form-group">
                                <label>Destination</label>
                                <p style={{ margin: '4px 0 0', fontWeight: 600 }}>{effectiveDestination.name}</p>
                            </div>
                        )}
                        <div className="form-group">
                            <label htmlFor="tripTravelers">Number of Travelers *</label>
                            <input
                                type="number"
                                id="tripTravelers"
                                value={travelers}
                                onChange={e => setTravelers(e.target.value)}
                                onBlur={e => setTravelers(String(Math.max(1, parseInt(e.target.value, 10) || 1)))}
                                min="1"
                                max="20"
                            />
                        </div>
                        <DateRangePicker
                            from={startDate}
                            to={endDate}
                            onChange={(from, to) => {
                                setStartDate(from);
                                setEndDate(to);
                            }}
                        />
                        {isVoteMode && (
                            <div className="form-group">
                                <label htmlFor="voteEmail">Your Email * <span style={{ fontWeight: 400, color: '#6c757d' }}>(results sent here)</span></label>
                                <input
                                    type="email"
                                    id="voteEmail"
                                    value={email}
                                    onChange={e => setEmail(e.target.value)}
                                    required
                                    placeholder="you@example.com"
                                />
                            </div>
                        )}
                        {isVoteMode && (
                            <div className="form-group">
                                <label htmlFor="voteBudget">Group budget (€, optional)</label>
                                <div style={{ position: 'relative' }}>
                                    <span
                                        aria-hidden="true"
                                        style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted, #6c757d)', pointerEvents: 'none', fontSize: '1rem' }}
                                    >
                                        €
                                    </span>
                                    <input
                                        id="voteBudget"
                                        type="number"
                                        min="0"
                                        step="100"
                                        value={budget}
                                        onChange={e => {
                                            setBudget(e.target.value);
                                            setBudgetError('');
                                        }}
                                        placeholder="3000"
                                        style={{ paddingLeft: '1.6rem' }}
                                    />
                                </div>
                                {budgetError && <p className="text-error">{budgetError}</p>}
                            </div>
                        )}
                    </form>
        </AppModal>
    );
}

export default TripSetupModal;
