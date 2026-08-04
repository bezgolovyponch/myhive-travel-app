import {useEffect, useRef, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import {useCatalog} from '../context/CatalogContext';
import leadApi from '../services/leadApi';
import {writeTripLead} from '../utils/tripLead';
import {writeQuizFlow} from '../utils/quizFlow';
import {generateUuid} from '../utils/uuid';

/**
 * Handles the reminder email's cross-device restore link (?restore=<token>).
 * Cascade: items -> rebuild cart; no items but quiz answers -> rebuild the quiz flow
 * (recommendations reappear); neither -> just travelers/dates. A non-empty local cart
 * is never clobbered without confirmation (pendingRestore + confirm/cancel).
 */
export function useTripLeadRestore(onQuizFlowRestored) {
    const [searchParams, setSearchParams] = useSearchParams();
    const {state, dispatch} = useTrip();
    const {state: catalog} = useCatalog();
    const [pendingRestore, setPendingRestore] = useState(null);
    const restoreToken = searchParams.get('restore');
    // Mount-time cart emptiness decides whether to ask — reading live state in the
    // fetch callback would still be the pre-restore value, but a ref is explicit.
    const hasLocalCartRef = useRef(state.tripItems.length > 0);

    const stripParam = () => {
        setSearchParams(params => {
            params.delete('restore');
            return params;
        }, {replace: true});
    };

    const apply = (data) => {
        writeTripLead({id: data.leadId, restoreToken});
        if (data.items && data.items.length > 0) {
            dispatch({
                type: 'SET_TRIP_ITEMS',
                tripItems: data.items.map(item => ({
                    id: item.activityId,
                    name: item.name,
                    price: item.price,
                    minPrice: item.minPrice,
                    imageUrl: item.imageUrl,
                    duration: item.duration,
                    slug: item.slug,
                    destinationSlug: item.destinationSlug,
                    description: item.description,
                    includes: item.includes,
                })),
            });
        }
        if (data.numberOfTravelers) {
            dispatch({type: 'UPDATE_TRIP_TRAVELERS', travelers: data.numberOfTravelers});
        }
        if (data.startDate || data.endDate) {
            dispatch({type: 'UPDATE_TRIP_DATES', startDate: data.startDate ?? '', endDate: data.endDate ?? ''});
        }
        dispatch({type: 'UPDATE_TRIP_BUDGET', budget: data.budget ?? null});
        dispatch({type: 'SET_TRIP_ID', tripId: generateUuid()});
        if ((!data.items || data.items.length === 0) && data.quizResponsesJson) {
            const destination = catalog.destinations.find(d => d.id === data.destinationId) || null;
            if (destination) {
                try {
                    const responses = JSON.parse(data.quizResponsesJson);
                    const flow = {
                        setup: {
                            travelers: data.numberOfTravelers || 1,
                            startDate: data.startDate || '',
                            endDate: data.endDate || '',
                            email: data.email,
                            destination,
                            budget: data.budget ?? null,
                        },
                        responses,
                    };
                    writeQuizFlow(flow);
                    if (onQuizFlowRestored) {
                        onQuizFlowRestored(flow);
                    }
                } catch (e) {
                    // Malformed stored answers — the plain builder still restores setup fields.
                }
            }
        }
        stripParam();
    };

    useEffect(() => {
        if (!restoreToken || catalog.loading) {
            return undefined;
        }
        let cancelled = false;
        leadApi.restoreLead(restoreToken)
            .then(data => {
                if (cancelled) {
                    return;
                }
                if (hasLocalCartRef.current && data.items && data.items.length > 0) {
                    setPendingRestore(data);
                } else {
                    apply(data);
                }
            })
            .catch(() => {
                if (!cancelled) {
                    stripParam(); // dead/unknown token — open the builder normally
                }
            });
        return () => {
            cancelled = true;
        };
        // apply/stripParam are stable within a render pass; re-running on their identity
        // would refetch on every render.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [restoreToken, catalog.loading]);

    return {
        pendingRestore,
        confirmRestore: () => {
            apply(pendingRestore);
            setPendingRestore(null);
        },
        cancelRestore: () => {
            setPendingRestore(null);
            stripParam();
        },
    };
}
