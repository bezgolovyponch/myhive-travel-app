import {createContext, useEffect, useReducer} from 'react';
import api from '../services/api';

export const AppContext = createContext();

export const initialState = {
    destinations: [],
    activities: [],
    tripItems: [],
    tripTravelers: 1,
    tripStartDate: '',
    tripEndDate: '',
    tripBudget: null,
    tripSetupModalOpen: false,
    tripBuilderModalOpen: false,
    destinationModalOpen: false,
    selectedDestination: null,
    chatOpen: false,
    chatMessages: [
        {sender: 'ai', text: 'Hi! I\'m your AI travel assistant. What type of trip are you looking for?'}
    ],
    autoEngaged: false,
    loading: true,
    error: null
};

export const reducer = (state, action) => {
    switch (action.type) {
        case 'SET_DESTINATIONS':
            return {...state, destinations: action.destinations, loading: false};
        case 'SET_ACTIVITIES':
            return {...state, activities: action.activities};
        case 'SET_ERROR':
            return {...state, error: action.error, loading: false};
        case 'SET_LOADING':
            return {...state, loading: action.loading};
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
            return {
                ...state,
                tripItems: state.tripItems.filter(item => item.id !== action.activityId)
            };
        case 'OPEN_TRIP_BUILDER_MODAL':
            return {...state, tripBuilderModalOpen: true};
        case 'CLOSE_TRIP_BUILDER_MODAL':
            return {...state, tripBuilderModalOpen: false};
        case 'OPEN_DESTINATION_MODAL':
            return {...state, destinationModalOpen: true, selectedDestination: action.destination};
        case 'CLOSE_DESTINATION_MODAL':
            return {...state, destinationModalOpen: false, selectedDestination: null};
        case 'TOGGLE_CHAT':
            return {...state, chatOpen: !state.chatOpen};
        case 'SET_AUTO_ENGAGED':
            return {...state, autoEngaged: action.value};
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
            return {
                ...state,
                tripItems: [],
                tripBudget: null,
                tripSetupModalOpen: false
            };
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
            // Remove any standalone copies of activities that are now part of this package
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
            return {
                ...state,
                tripItems: state.tripItems.filter(i => i.packageId !== action.packageId),
            };
        case 'ADD_CHAT_MESSAGE':
            return {
                ...state,
                chatMessages: [...state.chatMessages, action.message]
            };
        default:
            return state;
    }
};

export function AppProvider({children}) {

    const [state, dispatch] = useReducer(reducer, initialState, (init) => {
        let tripItems = init.tripItems;
        let tripTravelers = init.tripTravelers;
        let tripStartDate = init.tripStartDate;
        let tripEndDate = init.tripEndDate;
        let tripBudget = init.tripBudget;

        try {
            const saved = localStorage.getItem('myhive-trip-items');
            if (saved) tripItems = JSON.parse(saved);
        } catch (e) { /* ignore */
        }

        try {
            const saved = localStorage.getItem('myhive-trip-setup');
            if (saved) {
                const setup = JSON.parse(saved);
                tripTravelers = setup.travelers || 1;
                tripStartDate = setup.startDate || '';
                tripEndDate = setup.endDate || '';
                tripBudget = setup.budget ?? null;
            }
        } catch (e) { /* ignore */
        }

        return {...init, tripItems, tripTravelers, tripStartDate, tripEndDate, tripBudget};
    });

  // Save tripItems to localStorage whenever they change
  useEffect(() => {
    localStorage.setItem('myhive-trip-items', JSON.stringify(state.tripItems));
  }, [state.tripItems]);

    // Save trip setup to localStorage whenever it changes
    useEffect(() => {
        localStorage.setItem('myhive-trip-setup', JSON.stringify({
            travelers: state.tripTravelers,
            startDate: state.tripStartDate,
            endDate: state.tripEndDate,
            budget: state.tripBudget
        }));
    }, [state.tripTravelers, state.tripStartDate, state.tripEndDate, state.tripBudget]);

  useEffect(() => {
    const fetchData = async () => {
      try {
        dispatch({ type: 'SET_LOADING', loading: true });
        
        const [destinations, activities] = await Promise.all([
          api.getDestinations(),
          api.getActivities()
        ]);

        dispatch({ type: 'SET_DESTINATIONS', destinations });
        dispatch({ type: 'SET_ACTIVITIES', activities });
      } catch (error) {
        console.error('Error fetching data:', error);
        dispatch({ type: 'SET_ERROR', error: error.message });
      }
    };

    fetchData();
  }, []);

  return (
    <AppContext.Provider value={{ state, dispatch }}>
      {children}
    </AppContext.Provider>
  );
}
