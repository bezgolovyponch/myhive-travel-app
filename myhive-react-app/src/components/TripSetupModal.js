import {useContext, useEffect, useState} from 'react';
import {AppContext} from '../context/AppContext';
import {DESTINATION_PICKER_ENABLED} from '../services/config';
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

    const isOpen = isVoteMode ? voteOpen : state.tripSetupModalOpen;

    useEffect(() => {
        if (isOpen) {
            setTravelers('1');
            setStartDate('');
            setEndDate('');
            setEmail('');
            setSelectedDestinationId('');
            setBudget('');
        }
    }, [isOpen]);

    if (!isOpen) return null;

    // With the picker disabled (or when the API has a single destination anyway)
    // the first destination is selected automatically and shown as read-only text.
    const canAutoSelect = !DESTINATION_PICKER_ENABLED || state.destinations.length === 1;
    const autoDestination = canAutoSelect && state.destinations.length > 0 ? state.destinations[0] : null;
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
                return;
            }
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

    const handleCancel = () => {
        if (isVoteMode) {
            onVoteCancel();
        } else {
            dispatch({type: 'CANCEL_TRIP_SETUP'});
        }
    };

    return (
        <div className="app-modal">
            <div className="app-modal-content">
                <div className="app-modal-header">
                    <h2>Set Up Your Trip</h2>
                    <button className="app-modal-close-btn" onClick={handleCancel}>×</button>
                </div>
                <div className="app-modal-body">
                    <p className="trip-setup-description">
                        Tell us about your group so we can calculate the right price.
                    </p>
                    <form className="contact-form" onSubmit={e => e.preventDefault()}>
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
                                        onChange={e => setBudget(e.target.value)}
                                        placeholder="3000"
                                        style={{ paddingLeft: '1.6rem' }}
                                    />
                                </div>
                            </div>
                        )}
                    </form>
                </div>
                <div className="app-modal-footer">
                    <button className="btn btn--secondary" onClick={handleCancel}>Cancel</button>
                    <button
                        className="btn btn--primary"
                        onClick={handleConfirm}
                        disabled={isVoteMode && !voteFormValid}
                    >
                        {isVoteMode ? 'Continue to Categories' : 'Confirm'}
                    </button>
                </div>
            </div>
        </div>
    );
}

export default TripSetupModal;
