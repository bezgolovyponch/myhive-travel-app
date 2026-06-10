import {useContext, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {AppContext} from '../context/AppContext';

/**
 * Shared entry point for starting a group vote: owns the vote-setup modal
 * open state and the confirm handler that launches the quiz flow.
 */
export function useStartGroupVote() {
    const {state, dispatch} = useContext(AppContext);
    const navigate = useNavigate();
    const [voteSetupOpen, setVoteSetupOpen] = useState(false);

    const destSlug = state.tripItems.find(i => i.destinationSlug)?.destinationSlug;
    const preselectedDestination = state.destinations.find(d => d.slug === destSlug) || null;

    const openVoteSetup = () => setVoteSetupOpen(true);
    const closeVoteSetup = () => setVoteSetupOpen(false);

    const handleVoteConfirm = ({travelers, startDate, endDate, email, destination, budget}) => {
        setVoteSetupOpen(false);
        dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'});
        navigate('/vote/new/quiz', {
            state: {
                setup: {travelers, startDate, endDate, email, destination, budget},
            },
        });
    };

    return {voteSetupOpen, openVoteSetup, closeVoteSetup, handleVoteConfirm, preselectedDestination};
}
