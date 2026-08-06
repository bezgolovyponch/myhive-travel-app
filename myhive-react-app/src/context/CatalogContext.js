import {createContext, useContext, useEffect, useReducer} from 'react';
import api from '../services/api';

export const CatalogContext = createContext();

export const initialState = {
    destinations: [],
    loading: true,
    error: null,
};

export function reducer(state, action) {
    switch (action.type) {
        case 'SET_DESTINATIONS':
            return {...state, destinations: action.destinations, loading: false};
        case 'SET_ERROR':
            return {...state, error: action.error, loading: false};
        case 'SET_LOADING':
            return {...state, loading: action.loading};
        default:
            return state;
    }
}

// `initialDestinations` lets a server renderer seed the catalog so components
// that read it (header breadcrumbs, the homepage "View All Activities" CTA)
// produce real markup on the first paint instead of waiting for an effect.
// Omitted in the SPA, where the fetch below stays the only source.
export function CatalogProvider({children, initialDestinations}) {
    const seeded = Array.isArray(initialDestinations);
    const [state, dispatch] = useReducer(
        reducer,
        seeded ? {...initialState, destinations: initialDestinations, loading: false} : initialState,
    );

    // Only destinations are needed app-wide (header breadcrumbs, vote setup,
    // home page). Activities are fetched per destination by their consumers.
    useEffect(() => {
        if (seeded) {
            return;
        }
        const fetchData = async () => {
            try {
                dispatch({type: 'SET_LOADING', loading: true});
                const destinations = await api.getDestinations();
                dispatch({type: 'SET_DESTINATIONS', destinations});
            } catch (error) {
                console.error('Error fetching data:', error);
                dispatch({type: 'SET_ERROR', error: error.message});
            }
        };
        fetchData();
    }, [seeded]);

    return (
        <CatalogContext.Provider value={{state, dispatch}}>
            {children}
        </CatalogContext.Provider>
    );
}

export function useCatalog() {
    const context = useContext(CatalogContext);
    if (context === undefined) {
        throw new Error('useCatalog must be used within a CatalogProvider');
    }
    return context;
}
