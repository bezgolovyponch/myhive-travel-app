import {createContext, useContext, useEffect, useLayoutEffect, useReducer, useRef} from 'react';
import {api} from '../services/api';
import {currentLocale} from '../i18n/routes';

// The saved cart snapshots each activity's name/description in the language
// the page had when it was added. Remember that language next to the items so
// a visit in another locale can refresh the text — see TripProvider.
const TRIP_LOCALE_KEY = 'myhive-trip-locale';

// Restoring the cart must not happen during the first render: under SSR the
// server has no localStorage, so a client that read it while rendering produced
// markup that disagreed with the server's and React threw away the tree
// (hydration error #418) for every returning user with a saved cart.
// useLayoutEffect instead — it commits before paint, so there is no visible
// flash of an empty cart — falling back to useEffect on the server, where
// useLayoutEffect warns and there is nothing to restore anyway.
const useIsomorphicLayoutEffect = typeof window !== 'undefined' ? useLayoutEffect : useEffect;

function readSavedTrip() {
    const saved = {};
    try {
        const tripId = localStorage.getItem('myhive-trip-id');
        if (tripId) {
            saved.tripId = tripId;
        }
    } catch (e) { /* ignore corrupt storage */ }
    try {
        const items = localStorage.getItem('myhive-trip-items');
        if (items) {
            saved.tripItems = JSON.parse(items);
        }
    } catch (e) { /* ignore corrupt storage */ }
    try {
        const setup = localStorage.getItem('myhive-trip-setup');
        if (setup) {
            const parsed = JSON.parse(setup);
            saved.tripTravelers = parsed.travelers || 1;
            saved.tripStartDate = parsed.startDate || '';
            saved.tripEndDate = parsed.endDate || '';
            saved.tripBudget = parsed.budget ?? null;
            saved.tripBudgetBasis = parsed.budgetBasis ?? null;
        }
    } catch (e) { /* ignore corrupt storage */ }
    return saved;
}

export const TripContext = createContext();

export const initialState = {
    tripId: null,
    tripItems: [],
    tripTravelers: 1,
    tripStartDate: '',
    tripEndDate: '',
    tripBudget: null,
    // How the budget was put: 'PER_PERSON' or 'TOTAL' (null reads as TOTAL). tripBudget is the group total either way.
    tripBudgetBasis: null,
    tripSetupModalOpen: false,
    tripBuilderModalOpen: false,
    // True while the inline Complete Booking form is open, so the destination
    // chrome (global header, hero, tabs) collapses into a focused checkout view.
    checkoutOpen: false,
    // False until the saved cart has been read back from localStorage. Consumers
    // that branch on whether the user already has a cart must wait for this —
    // before it flips, tripItems is deliberately empty (see below) and a
    // returning user is indistinguishable from a new one.
    restored: false,
};

// A winner is a fresh row built from the vote result; the extras the organizer ticked before the vote
// belong to the activity, not to the ballot, so they ride over from the cart entry it replaces.
function withExtrasFrom(previousItems, winner) {
    const previous = previousItems.find(item => item.id === winner.id);
    if (!previous) {
        return winner;
    }
    return {...winner,
        extras: winner.extras && winner.extras.length > 0 ? winner.extras : (previous.extras || []),
        selectedExtras: previous.selectedExtras || []};
}

// A budget put per person follows the head-count: 150 each stays 150 each when two more join.
function rescaledBudget(state, travelers) {
    if (state.tripBudgetBasis !== 'PER_PERSON' || state.tripBudget == null || !(travelers >= 1)) {
        return state.tripBudget;
    }
    const before = Math.max(1, Number(state.tripTravelers) || 1);
    return Number(state.tripBudget) / before * travelers;
}

export function reducer(state, action) {
    switch (action.type) {
        case 'ADD_TO_TRIP':
            if (!state.tripItems.some(item => item.id === action.activity.id)) {
                const isFirstItem = state.tripItems.length === 0;
                return {
                    ...state,
                    tripItems: [...state.tripItems, action.activity],
                    tripSetupModalOpen: isFirstItem && !action.silent,
                    tripBuilderModalOpen: action.silent ? state.tripBuilderModalOpen : (!isFirstItem)
                };
            }
            return state;
        case 'REMOVE_FROM_TRIP':
            return {...state, tripItems: state.tripItems.filter(item => item.id !== action.activityId)};
        case 'OPEN_TRIP_BUILDER_MODAL':
            return {...state, tripBuilderModalOpen: true};
        case 'CLOSE_TRIP_BUILDER_MODAL':
            return {...state, tripBuilderModalOpen: false};
        case 'SET_TRIP_ITEMS':
            return {...state, tripItems: action.tripItems};
        case 'SET_TRIP_SETUP':
            return {
                ...state,
                tripBudget: rescaledBudget(state, action.travelers),
                tripTravelers: action.travelers,
                tripStartDate: action.startDate,
                tripEndDate: action.endDate,
                tripSetupModalOpen: false,
                tripBuilderModalOpen: true
            };
        case 'UPDATE_TRIP_TRAVELERS':
            return {...state, tripBudget: rescaledBudget(state, action.travelers), tripTravelers: action.travelers};
        case 'UPDATE_TRIP_DATES':
            return {...state, tripStartDate: action.startDate, tripEndDate: action.endDate};
        case 'UPDATE_TRIP_BUDGET': {
            // A total read back from the server (a vote, a lead) carries no basis: the same total keeps the one
            // this browser was given, anything else reads as a total.
            const sameTotal = action.budget != null && Number(action.budget) === Number(state.tripBudget);
            const basis = action.basis !== undefined ? action.basis : (sameTotal ? state.tripBudgetBasis : null);
            return {...state, tripBudget: action.budget, tripBudgetBasis: action.budget == null ? null : basis};
        }
        case 'CLOSE_TRIP_SETUP_MODAL':
            return {...state, tripSetupModalOpen: false};
        case 'CANCEL_TRIP_SETUP':
            return {...state, tripItems: [], tripBudget: null, tripBudgetBasis: null, tripSetupModalOpen: false};
        case 'ADD_PACKAGE_TO_TRIP': {
            const pkg = action.pkg;
            const newItems = pkg.activities.map(a => ({
                id: a.activityId,
                name: a.name,
                price: a.price,
                imageUrl: a.imageUrl,
                duration: a.duration,
                // A package's activity takes extras like any other; none is chosen when it goes in.
                extras: a.extras || [],
                selectedExtras: [],
                destinationSlug: pkg.destinationSlug,
                packageId: pkg.id,
                packageName: pkg.name,
                packageDiscountPct: pkg.discountPct,
            }));
            // Remove any standalone copies of activities now part of this package.
            const without = state.tripItems.filter(i => !newItems.some(n => n.id === i.id));
            const isFirstAdd = state.tripItems.length === 0;
            return {
                ...state,
                tripItems: [...without, ...newItems],
                tripSetupModalOpen: isFirstAdd,
                tripBuilderModalOpen: !isFirstAdd || state.tripBuilderModalOpen,
            };
        }
        case 'SET_TRIP_ITEMS_FROM_VOTE': {
            // A finished QUIZ vote is the trip: its ballot was built from this
            // very cart, so the winners replace it — merging them back in would
            // leave everything the group voted down in the itinerary.
            // Packages were never on the ballot and stay; a winner already inside
            // one keeps its packaged copy rather than gaining a standalone
            // duplicate (the mirror of ADD_PACKAGE_TO_TRIP above).
            const packageItems = state.tripItems.filter(item => item.packageId);
            const standaloneWinners = action.winners
                .filter(winner => !packageItems.some(item => item.id === winner.id))
                .map(winner => withExtrasFrom(state.tripItems, winner));
            return {...state, tripItems: [...packageItems, ...standaloneWinners]};
        }
        case 'SET_ITEM_EXTRAS':
            // `extras` (what the activity offers) comes along when the card had to look it up: a row
            // put in the cart before package activities carried their extras has none of its own.
            return {...state, tripItems: state.tripItems.map(item => (
                item.id === action.id
                    ? {...item, selectedExtras: action.selectedExtras, ...(action.extras ? {extras: action.extras} : {})}
                    : item))};
        case 'REMOVE_PACKAGE_FROM_TRIP':
            return {...state, tripItems: state.tripItems.filter(i => i.packageId !== action.packageId)};
        case 'SET_TRIP_ID':
            return {...state, tripId: action.tripId};
        case 'SET_CHECKOUT_OPEN':
            return {...state, checkoutOpen: action.open};
        case 'RESTORE_FROM_STORAGE':
            return {...state, ...action.saved, restored: true};
        default:
            return state;
    }
}

export function TripProvider({children}) {
    // Starts from the same empty state the server renders; the saved cart is
    // restored below, before paint.
    const [state, dispatch] = useReducer(reducer, initialState);
    // Locale the saved cart was written in, captured before the write effects
    // below stamp the current one over it.
    const savedLocaleRef = useRef(null);

    useIsomorphicLayoutEffect(() => {
        try {
            savedLocaleRef.current = localStorage.getItem(TRIP_LOCALE_KEY);
        } catch (e) { /* ignore */ }
        // Dispatched even when nothing was saved, so `restored` flips exactly
        // once and consumers can tell "no cart" from "not read yet".
        dispatch({type: 'RESTORE_FROM_STORAGE', saved: readSavedTrip()});
    }, []);

    // The writers below must never run before the read above, or mounting would
    // persist the empty initial state over a saved cart. They are gated on the
    // restored *state*, not on a ref set inside the layout effect: the ref
    // flips while that effect runs, but the passive writers of the very same
    // commit still close over the pre-restore render's empty tripItems — and
    // wrote exactly that over the saved cart. The flag arrives together with
    // the items, one commit later, so the first write is the restored cart.
    // Losing the race was only invisible because the next commit rewrote the
    // real items; a second mount (the SPA shim, StrictMode in dev) re-read the
    // storage in between and restored the emptied cart for good.
    useEffect(() => {
        if (!state.restored) {
            return;
        }
        if (state.tripId !== null) {
            localStorage.setItem('myhive-trip-id', state.tripId);
        }
    }, [state.restored, state.tripId]);

    // Another tab of this site changed the cart (the organiser adds activities
    // in the "Browse all" tab while the dashboard stays open): take its items,
    // so each tab shows and books the same plan.
    useEffect(() => {
        const onStorage = (e) => {
            if (e.key !== 'myhive-trip-items' || e.newValue == null) {
                return;
            }
            try {
                dispatch({type: 'SET_TRIP_ITEMS', tripItems: JSON.parse(e.newValue)});
            } catch (err) { /* ignore corrupt storage */ }
        };
        window.addEventListener('storage', onStorage);
        return () => window.removeEventListener('storage', onStorage);
    }, []);

    useEffect(() => {
        if (!state.restored) {
            return;
        }
        localStorage.setItem('myhive-trip-items', JSON.stringify(state.tripItems));
        localStorage.setItem(TRIP_LOCALE_KEY, currentLocale());
    }, [state.restored, state.tripItems]);

    // A saved cart carries the extras the catalogue offered when it was saved, and a cart saved under
    // another locale carries that locale's names and descriptions. Re-read both from the API once per
    // restore: an extra the catalogue has since retired drops out of the cart (and of the quote and the
    // booking, which would refuse its code), and the text follows the current page. Prices, packages and
    // order are kept as saved. Best-effort: a failed lookup leaves that item as is.
    useEffect(() => {
        if (!state.restored || state.tripItems.length === 0) {
            return undefined;
        }
        const relocalize = (savedLocaleRef.current || 'en') !== currentLocale();
        let cancelled = false;
        const items = state.tripItems;
        Promise.all(items.map((item) => api.getActivity(item.id).catch(() => null))).then((fresh) => {
            if (cancelled) {
                return;
            }
            const refreshed = items.map((item, i) => {
                if (!fresh[i]) {
                    return item;
                }
                const withExtras = {...item, extras: fresh[i].extras ?? item.extras};
                return relocalize
                    ? {...withExtras, name: fresh[i].name, description: fresh[i].description, includes: fresh[i].includes}
                    : withExtras;
            });
            dispatch({type: 'SET_TRIP_ITEMS', tripItems: refreshed});
        });
        return () => {
            cancelled = true;
        };
        // Runs once, right after the saved cart is restored.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [state.restored]);

    useEffect(() => {
        if (!state.restored) {
            return;
        }
        localStorage.setItem('myhive-trip-setup', JSON.stringify({
            travelers: state.tripTravelers,
            startDate: state.tripStartDate,
            endDate: state.tripEndDate,
            budget: state.tripBudget,
            budgetBasis: state.tripBudgetBasis
        }));
    }, [state.restored, state.tripTravelers, state.tripStartDate, state.tripEndDate, state.tripBudget,
        state.tripBudgetBasis]);

    return (
        <TripContext.Provider value={{state, dispatch}}>
            {children}
        </TripContext.Provider>
    );
}

export function useTrip() {
    const context = useContext(TripContext);
    if (context === undefined) {
        throw new Error('useTrip must be used within a TripProvider');
    }
    return context;
}
