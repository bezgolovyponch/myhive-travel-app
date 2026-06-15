import {createContext, useContext, useReducer} from 'react';

export const DestinationModalContext = createContext();

export const initialState = {
    destinationModalOpen: false,
    selectedDestination: null,
};

export function reducer(state, action) {
    switch (action.type) {
        case 'OPEN_DESTINATION_MODAL':
            return {...state, destinationModalOpen: true, selectedDestination: action.destination};
        case 'CLOSE_DESTINATION_MODAL':
            return {...state, destinationModalOpen: false, selectedDestination: null};
        default:
            return state;
    }
}

export function DestinationModalProvider({children}) {
    const [state, dispatch] = useReducer(reducer, initialState);
    return (
        <DestinationModalContext.Provider value={{state, dispatch}}>
            {children}
        </DestinationModalContext.Provider>
    );
}

export function useDestinationModal() {
    const context = useContext(DestinationModalContext);
    if (context === undefined) {
        throw new Error('useDestinationModal must be used within a DestinationModalProvider');
    }
    return context;
}
