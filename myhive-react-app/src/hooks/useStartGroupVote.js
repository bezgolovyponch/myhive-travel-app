import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useCatalog} from '../context/CatalogContext';
import {useTrip} from '../context/TripContext';

/**
 * Shared entry point for starting a group vote: owns the vote-setup modal
 * open state and the confirm handler that launches the quiz flow.
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

    const handleVoteConfirm = ({travelers, startDate, endDate, destination, budget}) => {
        setVoteSetupOpen(false);
        dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'});
        navigate('/vote/new/quiz', {
            state: {
                setup: {travelers, startDate, endDate, destination, budget},
            },
        });
    };

    return {voteSetupOpen, openVoteSetup, closeVoteSetup, handleVoteConfirm, preselectedDestination};
}
