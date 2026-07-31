import {createContext, useContext, useEffect, useReducer} from 'react';

export const TripContext = createContext();

export const initialState = {
    tripId: null,
    tripItems: [],
    tripTravelers: 1,
    tripStartDate: '',
    tripEndDate: '',
    tripBudget: null,
    tripSetupModalOpen: false,
    tripBuilderModalOpen: false,
    // True while the inline Complete Booking form is open, so the destination
    // chrome (global header, hero, tabs) collapses into a focused checkout view.
    checkoutOpen: false,
};

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
                tripTravelers: action.travelers,
                tripStartDate: action.startDate,
                tripEndDate: action.endDate,
                tripSetupModalOpen: false,
                tripBuilderModalOpen: true
            };
        case 'UPDATE_TRIP_TRAVELERS':
            return {...state, tripTravelers: action.travelers};
        case 'UPDATE_TRIP_DATES':
            return {...state, tripStartDate: action.startDate, tripEndDate: action.endDate};
        case 'UPDATE_TRIP_BUDGET':
            return {...state, tripBudget: action.budget};
        case 'CLOSE_TRIP_SETUP_MODAL':
            return {...state, tripSetupModalOpen: false};
        case 'CANCEL_TRIP_SETUP':
            return {...state, tripItems: [], tripBudget: null, tripSetupModalOpen: false};
        case 'ADD_PACKAGE_TO_TRIP': {
            const pkg = action.pkg;
            const newItems = pkg.activities.map(a => ({
                id: a.activityId,
                name: a.name,
                price: a.price,
                imageUrl: a.imageUrl,
                duration: a.duration,
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
        case 'REMOVE_PACKAGE_FROM_TRIP':
            return {...state, tripItems: state.tripItems.filter(i => i.packageId !== action.packageId)};
        case 'SET_TRIP_ID':
            return {...state, tripId: action.tripId};
        case 'SET_CHECKOUT_OPEN':
            return {...state, checkoutOpen: action.open};
        default:
            return state;
    }
}

export function TripProvider({children}) {
    const [state, dispatch] = useReducer(reducer, initialState, (init) => {
        let {tripId, tripItems, tripTravelers, tripStartDate, tripEndDate, tripBudget} = init;
        try {
            const saved = localStorage.getItem('myhive-trip-id');
            if (saved) {
                tripId = saved;
            }
        } catch (e) { /* ignore corrupt storage */ }
        try {
            const saved = localStorage.getItem('myhive-trip-items');
            if (saved) {
                tripItems = JSON.parse(saved);
            }
        } catch (e) { /* ignore corrupt storage */ }
        try {
            const saved = localStorage.getItem('myhive-trip-setup');
            if (saved) {
                const setup = JSON.parse(saved);
                tripTravelers = setup.travelers || 1;
                tripStartDate = setup.startDate || '';
                tripEndDate = setup.endDate || '';
                tripBudget = setup.budget ?? null;
            }
        } catch (e) { /* ignore corrupt storage */ }
        return {...init, tripId, tripItems, tripTravelers, tripStartDate, tripEndDate, tripBudget};
    });

    useEffect(() => {
        if (state.tripId !== null) {
            localStorage.setItem('myhive-trip-id', state.tripId);
        }
    }, [state.tripId]);

    useEffect(() => {
        localStorage.setItem('myhive-trip-items', JSON.stringify(state.tripItems));
    }, [state.tripItems]);

    useEffect(() => {
        localStorage.setItem('myhive-trip-setup', JSON.stringify({
            travelers: state.tripTravelers,
            startDate: state.tripStartDate,
            endDate: state.tripEndDate,
            budget: state.tripBudget
        }));
    }, [state.tripTravelers, state.tripStartDate, state.tripEndDate, state.tripBudget]);

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
