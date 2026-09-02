import {useEffect} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {useCatalog} from '../../context/CatalogContext';
import {useStartGroupVote} from '../../hooks/useStartGroupVote';
import {parseVoteSetupParams} from '../../utils/voteSetup';
import TripSetupModal from '../../components/TripSetupModal';

/**
 * /vote/new — full-page entry into the group-vote funnel. Exists for the SSR
 * pages (Ф1): their "Start Group Vote" CTAs open the travelers/dates modal in
 * place, but react-router location state cannot survive the full page load out
 * of a server-rendered document, so a confirmed setup arrives here in the query
 * string (utils/voteSetup.js) and continues straight into /vote/new/quiz —
 * `replace`, so Back from the quiz returns to the page the visitor confirmed
 * on, never to this hop. In the SPA the state arrives too and is preferred.
 * Without a setup (an old link, the cart panel's empty-state CTA, a direct
 * visit) the modal opens here, exactly as before.
 */
function VoteEntryPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const {state: catalog} = useCatalog();
    const {voteSetupOpen, openVoteSetup, closeVoteSetup, continueToQuiz, preselectedDestination} = useStartGroupVote();

    const stateSetup = location.state?.setup;
    // Only a URL that even claims to carry a setup waits for the catalog —
    // a bare /vote/new must open the modal immediately, as it always has.
    const claimsSetup = Boolean(stateSetup) || new URLSearchParams(location.search).has('travelers');

    useEffect(() => {
        if (!claimsSetup) {
            openVoteSetup();
            return;
        }
        if (stateSetup) {
            continueToQuiz(stateSetup, {replace: true});
            return;
        }
        if (catalog.loading) {
            return;
        }
        const setup = parseVoteSetupParams(location.search, catalog.destinations);
        if (setup) {
            continueToQuiz(setup, {replace: true});
        } else {
            // Unresolvable params (stale link, renamed destination): fall back
            // to asking, never to a dead end.
            openVoteSetup();
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [catalog.loading]);

    const handleCancel = () => {
        closeVoteSetup();
        navigate('/');
    };

    return (
        <TripSetupModal
            isVoteMode={true}
            voteOpen={voteSetupOpen}
            onVoteConfirm={(setup) => continueToQuiz(setup)}
            onVoteCancel={handleCancel}
            preselectedDestination={preselectedDestination}
        />
    );
}

export default VoteEntryPage;
