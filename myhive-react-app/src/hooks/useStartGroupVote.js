import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useCatalog} from '../context/CatalogContext';
import {useTrip} from '../context/TripContext';
import {voteEntryUrlWithSetup} from '../utils/voteSetup';

/**
 * Shared entry point for starting a group vote: owns the vote-setup modal
 * open state and the handlers that launch the quiz flow.
 *
 * Confirm goes through /vote/new with the setup in the query string, not
 * straight to the quiz: this hook also runs on server-rendered mounts (the
 * homepage and the landings under LegacyRouter), where navigation is a full
 * page load that drops react-router location state. The URL survives it;
 * VoteEntryPage turns the params back into the location state the quiz flow
 * runs on. In the SPA the same navigation is a client-side hop and the state
 * rides along, so the setup is never re-derived.
 */
export function useStartGroupVote() {
    const {state: catalog} = useCatalog();
    const {state, dispatch} = useTrip();
    const navigate = useNavigate();
    const [voteSetupOpen, setVoteSetupOpen] = useState(false);

    const destSlug = state.tripItems.find(i => i.destinationSlug)?.destinationSlug;
    const preselectedDestination = catalog.destinations.find(d => d.slug === destSlug) || null;

    const openVoteSetup = () => setVoteSetupOpen(true);
    const closeVoteSetup = () => setVoteSetupOpen(false);

    // Rebuilt object, not the raw argument: state must never carry extras a
    // caller's payload happens to hold (email, most of all).
    const cleanSetup = ({travelers, startDate, endDate, destination, budget}) =>
        ({travelers, startDate, endDate, destination, budget});

    const handleVoteConfirm = (setup) => {
        setVoteSetupOpen(false);
        dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'});
        navigate(voteEntryUrlWithSetup(setup), {state: {setup: cleanSetup(setup)}});
    };

    // VoteEntryPage's continuation into the quiz itself — direct, so the entry
    // page can never loop back into its own URL.
    const continueToQuiz = (setup, {replace = false} = {}) => {
        setVoteSetupOpen(false);
        dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'});
        navigate('/vote/new/quiz', {state: {setup: cleanSetup(setup)}, replace});
    };

    return {voteSetupOpen, openVoteSetup, closeVoteSetup, handleVoteConfirm, continueToQuiz, preselectedDestination};
}
