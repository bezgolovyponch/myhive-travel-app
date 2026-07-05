import { useNavigate } from 'react-router-dom';
import AppModal from '../AppModal';

// Shown when the initiator tries to start a second vote while one they
// started is still ACTIVE — points them at the running vote's dashboard
// instead of letting them create a competing session.
function ActiveVoteModal({ isOpen, onClose, shareToken }) {
    const navigate = useNavigate();

    const handleOpenDashboard = () => {
        navigate(`/vote/${shareToken}/waiting`);
    };

    return (
        <AppModal
            isOpen={isOpen}
            onClose={onClose}
            title="A vote is already running"
            footer={(
                <button
                    type="button"
                    className="btn btn--primary btn--full-width"
                    onClick={handleOpenDashboard}
                >
                    Open the vote dashboard
                </button>
            )}
        >
            <p>
                Your mates are still voting on this trip. Finish that vote before starting a new
                one — you can end it early from the vote dashboard.
            </p>
        </AppModal>
    );
}

export default ActiveVoteModal;
