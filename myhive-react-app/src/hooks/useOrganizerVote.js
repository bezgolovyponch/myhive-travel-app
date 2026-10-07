import {useCallback, useEffect, useRef, useState} from 'react';
import voteApi from '../services/voteApi';

const POLL_MS = 30_000;

export const managerKey = (shareToken) => `myhive-manager-${shareToken}`;

/**
 * The organiser's side of a running cart vote, for the Trip Builder dashboard
 * (v3 4b): the session, the live tally (yes/no per activity, who has voted,
 * what friends recommended) polled every 30 s, and the edits the organiser
 * makes to the ballot while friends vote.
 *
 * Only a browser holding the vote's manager token gets a dashboard. The token
 * arrives in localStorage from the contact modal, or in the dashboard link of
 * the organiser's emails (?manager=), which is adopted here and stripped from
 * the URL so the secret does not linger in history. On a device with an empty
 * cart (the email link opened on another phone) the ballot seeds the cart once,
 * with the vote's group size and dates.
 */
export function useOrganizerVote({shareToken, managerParam, restored, cartEmpty, dispatch, stripManagerParam}) {
    const [managerToken, setManagerToken] = useState(
        () => (shareToken ? localStorage.getItem(managerKey(shareToken)) : null));
    const [session, setSession] = useState(null);
    const [tally, setTally] = useState(null);
    const seededRef = useRef(null);

    // Adopt the manager token from the email link.
    useEffect(() => {
        if (!shareToken || !managerParam) {
            return;
        }
        localStorage.setItem(managerKey(shareToken), managerParam);
        localStorage.setItem(`myhive-initiator-${shareToken}`, 'true');
        localStorage.setItem('myhive-trip-vote-session', shareToken);
        setManagerToken(managerParam);
        stripManagerParam();
        // stripManagerParam is a fresh closure per render; the param is the input.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [shareToken, managerParam]);

    useEffect(() => {
        setManagerToken(shareToken ? localStorage.getItem(managerKey(shareToken)) : null);
    }, [shareToken]);

    const loadTally = useCallback(() => {
        if (!shareToken || !managerToken) {
            return Promise.resolve();
        }
        return voteApi.getTally(shareToken, {managerToken})
            .then(setTally)
            .catch(() => {
                // Transient failure — keep the last tally, the next poll retries.
            });
    }, [shareToken, managerToken]);

    useEffect(() => {
        if (!shareToken || !managerToken) {
            setSession(null);
            setTally(null);
            return undefined;
        }
        let cancelled = false;
        voteApi.getSession(shareToken)
            .then(s => {
                if (!cancelled && s.voteMode === 'CART') {
                    setSession(s);
                }
            })
            .catch(() => {});
        return () => {
            cancelled = true;
        };
    }, [shareToken, managerToken]);

    const active = session?.status === 'ACTIVE' && tally?.status !== 'COMPLETED';
    useEffect(() => {
        if (!session) {
            return undefined;
        }
        loadTally();
        if (!active) {
            return undefined;
        }
        const id = setInterval(loadTally, POLL_MS);
        return () => clearInterval(id);
    }, [session, active, loadTally]);

    // An empty cart on this device: the ballot becomes the plan, once per vote.
    useEffect(() => {
        if (!session || !restored || !cartEmpty || seededRef.current === shareToken) {
            return;
        }
        seededRef.current = shareToken;
        voteApi.getActivities(shareToken)
            .then(activities => {
                dispatch({type: 'SET_TRIP_ITEMS', tripItems: activities});
                if (session.numberOfTravelers > 0) {
                    dispatch({type: 'UPDATE_TRIP_TRAVELERS', travelers: session.numberOfTravelers});
                }
                if (session.startDate && session.endDate) {
                    dispatch({type: 'UPDATE_TRIP_DATES', startDate: session.startDate, endDate: session.endDate});
                }
            })
            .catch(() => {});
    }, [session, restored, cartEmpty, shareToken, dispatch]);

    // Edits: the server call, then a fresh tally either way (a failed call leaves
    // the tally as the server sees it). Resolves to null, or to the failure, so a
    // caller can tell a refusal from a request that got no answer; never rejects.
    const edit = useCallback((call) => call().then(
        () => loadTally().then(() => null),
        (failure) => loadTally().then(() => failure),
    ), [loadTally]);

    return {
        session,
        tally,
        managerToken,
        hasDashboard: Boolean(session && managerToken),
        active,
        excludeActivity: (activityId) => edit(() => voteApi.excludeActivity(shareToken, managerToken, activityId)),
        addActivity: (activityId) => edit(() => voteApi.addActivity(shareToken, managerToken, activityId)),
        closeVote: () => voteApi.closeSession(shareToken, managerToken).catch(() => {}),
    };
}
