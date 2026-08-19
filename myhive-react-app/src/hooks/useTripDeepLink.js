import {useEffect, useRef} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import api from '../services/api';

// Consumes ?add=<activity-slug> / ?addPackage=<package-slug> planted by the
// SSR detail pages' "Add to trip" CTAs: fetches the record and dispatches the
// exact action the in-SPA buttons dispatch (non-silent — modal behavior stays
// identical to a legacy button click), then strips the param via a history
// replace so refresh/back cannot re-add.
export default function useTripDeepLink() {
    const location = useLocation();
    const navigate = useNavigate();
    const {dispatch} = useTrip();
    const handledRef = useRef(false);

    useEffect(() => {
        const params = new URLSearchParams(location.search);
        const addSlug = params.get('add');
        const addPackageSlug = params.get('addPackage');
        if (handledRef.current || (!addSlug && !addPackageSlug)) {
            return;
        }
        handledRef.current = true;

        let cancelled = false;
        const strip = () => {
            const next = new URLSearchParams(location.search);
            next.delete('add');
            next.delete('addPackage');
            navigate({pathname: location.pathname, search: next.toString()}, {replace: true});
        };

        (async () => {
            try {
                if (addSlug) {
                    const activity = await api.getActivityBySlug(addSlug);
                    if (!cancelled && activity) {
                        dispatch({type: 'ADD_TO_TRIP', activity});
                    }
                } else {
                    const pkg = await api.getPackageBySlug(addPackageSlug);
                    if (!cancelled && pkg) {
                        dispatch({type: 'ADD_PACKAGE_TO_TRIP', pkg});
                    }
                }
            } catch (e) {
                // Nothing to add (backend hiccup) — still strip below so the
                // URL doesn't keep a dead param.
            } finally {
                if (!cancelled) {
                    strip();
                }
            }
        })();
        return () => {
            cancelled = true;
        };
    }, [location, navigate, dispatch]);
}
