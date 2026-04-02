import React from 'react';

function SuccessModal({isOpen, onClose, userName, userEmail}) {
    if (!isOpen) return null;

    return (
        <div className="app-modal">
            <div className="app-modal-content">
                <div className="app-modal-header">
                    <h2>Booking Submitted Successfully!</h2>
                    <button className="app-modal-close-btn" onClick={onClose}>×</button>
                </div>

                <div className="app-modal-body">
                    <div className="success-message">
                        <h4>Thank you, {userName}!</h4>
                        <p>Your travel booking has been submitted successfully.</p>
                        <p>We will contact you soon at <strong>{userEmail}</strong> to confirm the details.</p>
                    </div>

                    <div className="next-steps">
                        <h5>What happens next?</h5>
                        <ul>
                            <li>Our team will review your booking request</li>
                            <li>We'll contact you via email to confirm details</li>
                            <li>We'll provide personalized recommendations</li>
                            <li>We'll finalize your travel itinerary</li>
                        </ul>
                    </div>
                </div>

                <div className="app-modal-footer">
                    <button className="btn btn--primary" onClick={onClose}>
                        Got it!
                    </button>
                </div>
            </div>
        </div>
    );
}

export default SuccessModal;
