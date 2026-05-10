import {useContext, useEffect, useState} from 'react';
import {AppContext} from '../context/AppContext';
import './ContactForm.css';
import DateRangePicker from './DateRangePicker';

function TripSetupModal({ isVoteMode = false, voteOpen = false, onVoteConfirm, onVoteCancel, preselectedDestination = null }) {
    const {state, dispatch} = useContext(AppContext);
    const [travelers, setTravelers] = useState(1);
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [email, setEmail] = useState('');
    const [selectedDestinationId, setSelectedDestinationId] = useState('');

    const isOpen = isVoteMode ? voteOpen : state.tripSetupModalOpen;

    useEffect(() => {
        if (isOpen) {
            setTravelers(1);
            setStartDate('');
            setEndDate('');
            setEmail('');
            setSelectedDestinationId('');
        }
    }, [isOpen]);

    if (!isOpen) return null;

    const needsDestinationPicker = isVoteMode && !preselectedDestination;
    const destination = preselectedDestination
        || state.destinations.find(d => d.id === selectedDestinationId)
        || null;

    const voteFormValid = startDate && endDate && email && destination;

    const handleConfirm = () => {
        if (isVoteMode) {
            if (!voteFormValid) return;
            onVoteConfirm({ travelers, startDate, endDate, email, destination });
        } else {
            dispatch({
                type: 'SET_TRIP_SETUP',
                travelers: travelers,
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
                                    required
                                >
                                    <option value="">Select a destination…</option>
                                    {state.destinations.map(d => (
                                        <option key={d.id} value={d.id}>{d.name}</option>
                                    ))}
                                </select>
                            </div>
                        )}
                        {preselectedDestination && (
                            <div className="form-group">
                                <label>Destination</label>
                                <p style={{ margin: '4px 0 0', fontWeight: 600 }}>{preselectedDestination.name}</p>
                            </div>
                        )}
                        <div className="form-group">
                            <label htmlFor="tripTravelers">Number of Travelers *</label>
                            <input
                                type="number"
                                id="tripTravelers"
                                value={travelers}
                                onChange={e => setTravelers(Math.max(1, parseInt(e.target.value, 10) || 1))}
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
