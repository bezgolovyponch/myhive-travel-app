import {useModalA11y} from '../hooks/useModalA11y';

function SuccessModal({isOpen, onClose, userName, userEmail}) {
    const contentRef = useModalA11y(isOpen, onClose);

    if (!isOpen) return null;

    return (
        <div className="app-modal" role="dialog" aria-modal="true" aria-labelledby="success-modal-title">
            <div className="app-modal-content" ref={contentRef}>
                <div className="app-modal-header">
                    <h2 id="success-modal-title">Booking Submitted Successfully!</h2>
                    <button type="button" className="app-modal-close-btn" onClick={onClose} aria-label="Close">×</button>
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
