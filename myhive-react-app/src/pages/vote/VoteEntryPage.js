import {useEffect} from 'react';
import {useNavigate} from 'react-router-dom';
import {useStartGroupVote} from '../../hooks/useStartGroupVote';
import TripSetupModal from '../../components/TripSetupModal';

/**
 * /vote/new — full-page entry into the group-vote funnel. Exists for the SSR
 * public pages (Ф1): their "Start Group Vote" CTAs can't open the SPA's modal
 * in place, so they navigate here; the setup modal opens immediately and the
 * confirm handler continues into /vote/new/quiz exactly like the homepage CTA.
 */
function VoteEntryPage() {
    const navigate = useNavigate();
    const {voteSetupOpen, openVoteSetup, closeVoteSetup, handleVoteConfirm, preselectedDestination} = useStartGroupVote();

    useEffect(() => {
        openVoteSetup();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const handleCancel = () => {
        closeVoteSetup();
        navigate('/');
    };

    return (
        <TripSetupModal
            isVoteMode={true}
            voteOpen={voteSetupOpen}
            onVoteConfirm={handleVoteConfirm}
            onVoteCancel={handleCancel}
            preselectedDestination={preselectedDestination}
        />
    );
}

export default VoteEntryPage;
