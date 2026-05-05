import {useContext, useEffect, useState} from 'react';
import {AppContext} from '../context/AppContext';
import './ContactForm.css';
import DateRangePicker from './DateRangePicker';

function TripSetupModal() {
    const {state, dispatch} = useContext(AppContext);
    const [travelers, setTravelers] = useState(1);
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');

    useEffect(() => {
        if (state.tripSetupModalOpen) {
            setTravelers(1);
            setStartDate('');
            setEndDate('');
        }
    }, [state.tripSetupModalOpen]);

    if (!state.tripSetupModalOpen) return null;

    const handleConfirm = () => {
        dispatch({
            type: 'SET_TRIP_SETUP',
            travelers: travelers,
            startDate: startDate,
            endDate: endDate
        });
    };

    const handleCancel = () => {
        dispatch({type: 'CANCEL_TRIP_SETUP'});
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
                    </form>
                </div>
                <div className="app-modal-footer">
                    <button className="btn btn--secondary" onClick={handleCancel}>Cancel</button>
                    <button className="btn btn--primary" onClick={handleConfirm}>Confirm</button>
                </div>
            </div>
        </div>
    );
}

export default TripSetupModal;
