import {useEffect} from 'react';
import {useTrip} from '../context/TripContext';
import leadApi from '../services/leadApi';
import {clearTripLead, readTripLead} from '../utils/tripLead';
import {readQuizFlow} from '../utils/quizFlow';

const SYNC_DEBOUNCE_MS = 2000;

/**
 * While this browser holds an active trip lead (myhive-trip-lead), mirrors every
 * cart/setup change to the server (debounced) so the reminder email's restore link
 * always carries the user's latest state — including on other devices.
 */
export function useTripLeadSync() {
    const {state} = useTrip();
    const {tripItems, tripTravelers, tripStartDate, tripEndDate, tripBudget} = state;

    useEffect(() => {
        const lead = readTripLead();
        if (!lead) {
            return undefined;
        }
        const timer = setTimeout(() => {
            const quizFlow = readQuizFlow();
            leadApi.syncLead(lead.id, {
                restoreToken: lead.restoreToken,
                numberOfTravelers: tripTravelers,
                startDate: tripStartDate || null,
                endDate: tripEndDate || null,
                budget: tripBudget,
                quizResponsesJson: quizFlow?.responses ? JSON.stringify(quizFlow.responses) : null,
                items: tripItems.map((item, index) => ({activityId: item.id, sortOrder: index})),
            }).catch(e => {
                if (e.message === 'LEAD_GONE') {
                    clearTripLead();
                }
                // Any other failure is silent — the next change retries.
            });
        }, SYNC_DEBOUNCE_MS);
        return () => clearTimeout(timer);
    }, [tripItems, tripTravelers, tripStartDate, tripEndDate, tripBudget]);
}
