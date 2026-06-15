import {useContext, useEffect, useId, useState} from 'react';
import {AppContext} from '../context/AppContext';
import AppModal from './AppModal';
import {DESTINATION_PICKER_ENABLED} from '../services/config';
import {getDefaultDestination} from '../utils/defaultDestination';
import './ContactForm.css';
import DateRangePicker from './DateRangePicker';

function TripSetupModal({ isVoteMode = false, voteOpen = false, onVoteConfirm, onVoteCancel, preselectedDestination = null }) {
    const {state, dispatch} = useContext(AppContext);
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
    const canAutoSelect = !DESTINATION_PICKER_ENABLED || state.destinations.length === 1;
    const autoDestination = canAutoSelect ? getDefaultDestination(state.destinations) : null;
    const effectiveDestination = preselectedDestination || autoDestination;
    const needsDestinationPicker = isVoteMode && !effectiveDestination;
    const destination = effectiveDestination
        || state.destinations.find(d => d.id === selectedDestinationId)
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
            onVoteConfirm({ travelers: travelersNum, startDate, endDate, email, destination, budget: budgetValue });
        } else {
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
                                    disabled={state.loading}
                                    required
                                >
                                    <option value="">{state.loading ? 'Loading destinations…' : 'Select a destination…'}</option>
                                    {!state.loading && state.destinations.map(d => (
                                        <option key={d.id} value={d.id}>{d.name}</option>
                                    ))}
                                </select>
                                {state.error && !state.loading && (
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
