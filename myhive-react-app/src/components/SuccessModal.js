import AppModal from './AppModal';

function SuccessModal({isOpen, onClose, userName, userEmail}) {
    return (
        <AppModal
            isOpen={isOpen}
            onClose={onClose}
            title="Booking Submitted Successfully!"
            footer={
                <button className="btn btn--primary" onClick={onClose}>
                    Got it!
                </button>
            }
        >
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
        </AppModal>
    );
}

export default SuccessModal;
