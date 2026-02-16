import {createContext, useEffect, useReducer} from 'react';
import api from '../services/api';

export const AppContext = createContext();

export function AppProvider({ children }) {
  const initialState = {
    destinations: [],
    activities: [],
    packages: [],
    currentPath: '/',
    currentTab: 'activities',
    tripItems: [],
    tripBuilderModalOpen: false,
    destinationModalOpen: false,
    selectedDestination: null,
    chatOpen: false,
    chatMessages: [
      { sender: 'ai', text: 'Hi! I\'m your AI travel assistant. What type of trip are you looking for?' }
    ],
    autoEngaged: false,
    loading: true,
    error: null
  };

  const reducer = (state, action) => {
    switch (action.type) {
      case 'SET_DESTINATIONS':
        return { ...state, destinations: action.destinations, loading: false };
      case 'SET_ACTIVITIES':
        return { ...state, activities: action.activities };
      case 'SET_PACKAGES':
        return { ...state, packages: action.packages };
      case 'SET_ERROR':
        return { ...state, error: action.error, loading: false };
      case 'SET_LOADING':
        return { ...state, loading: action.loading };
      case 'NAVIGATE':
        return { ...state, currentPath: action.path };
      case 'ADD_TO_TRIP':
        if (!state.tripItems.some(item => item.id === action.activity.id)) {
          return { 
            ...state, 
            tripItems: [...state.tripItems, action.activity] 
          };
        }
        return state;
      case 'REMOVE_FROM_TRIP':
        return { 
          ...state, 
          tripItems: state.tripItems.filter(item => item.id !== action.activityId)
        };
      case 'SELECT_PACKAGE':
        const newItems = [...state.tripItems];
        action.pkg.activities.forEach(actId => {
          const activity = state.activities.find(a => a.id === actId);
          if (activity && !newItems.some(item => item.id === actId)) {
            newItems.push(activity);
          }
        });
        return { ...state, tripItems: newItems };
      case 'SWITCH_TAB':
        return { ...state, currentTab: action.tab };
      case 'OPEN_TRIP_BUILDER_MODAL':
        return {...state, tripBuilderModalOpen: true};
      case 'CLOSE_TRIP_BUILDER_MODAL':
        return {...state, tripBuilderModalOpen: false};
      case 'OPEN_DESTINATION_MODAL':
        return {...state, destinationModalOpen: true, selectedDestination: action.destination};
      case 'CLOSE_DESTINATION_MODAL':
        return {...state, destinationModalOpen: false, selectedDestination: null};
      case 'TOGGLE_CHAT':
        return { ...state, chatOpen: !state.chatOpen };
      case 'SET_AUTO_ENGAGED':
        return {...state, autoEngaged: action.value};
      case 'SET_TRIP_ITEMS':
        return {...state, tripItems: action.tripItems};
      case 'ADD_CHAT_MESSAGE':
        return { 
          ...state, 
          chatMessages: [...state.chatMessages, action.message] 
        };
      default:
        return state;
    }
  };

  const [state, dispatch] = useReducer(reducer, initialState);

  // Load tripItems from localStorage on mount
  useEffect(() => {
    const savedTripItems = localStorage.getItem('myhive-trip-items');
    if (savedTripItems) {
      try {
        const parsed = JSON.parse(savedTripItems);
        dispatch({type: 'SET_TRIP_ITEMS', tripItems: parsed});
      } catch (e) {
        console.error('Failed to load trip items from localStorage', e);
      }
    }
  }, []);

  // Save tripItems to localStorage whenever they change
  useEffect(() => {
    localStorage.setItem('myhive-trip-items', JSON.stringify(state.tripItems));
  }, [state.tripItems]);

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
