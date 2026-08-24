import { useNavigate } from 'react-router-dom';
import AppModal from '../AppModal';
import { useT } from '../../i18n';

// Shown when the initiator tries to start a second vote while one they
// started is still ACTIVE — points them at the running vote's dashboard
// instead of letting them create a competing session.
function ActiveVoteModal({ isOpen, onClose, shareToken }) {
    const t = useT('voteComponents');
    const navigate = useNavigate();

    const handleOpenDashboard = () => {
        navigate(`/vote/${shareToken}/waiting`);
    };

    return (
        <AppModal
            isOpen={isOpen}
            onClose={onClose}
            title={t('active.title')}
            footer={(
                <button
                    type="button"
                    className="btn btn--primary btn--full-width"
                    onClick={handleOpenDashboard}
                >
                    {t('active.openDashboard')}
                </button>
            )}
        >
            <p>
                {t('active.body')}
            </p>
        </AppModal>
    );
}

export default ActiveVoteModal;
